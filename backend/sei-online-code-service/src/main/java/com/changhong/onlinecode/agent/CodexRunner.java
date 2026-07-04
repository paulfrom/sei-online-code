package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dto.run.RunLogFrame;
import com.changhong.onlinecode.ws.RunLogWebSocketHub;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * CodexRunner（PR1）。参考 multica {@code server/pkg/agent/codex.go}。
 *
 * <p>以 {@link ProcessBuilder} spawn {@code codex exec <prompt> --json}（一次性 NDJSON，
 * 对齐 {@link ClaudeRunner} 的 {@code claude -p --output-format json} 一次性 stub 水平），
 * stdout/stderr 逐行流式推送到 {@link RunLogWebSocketHub}。spawn 前在 per-run
 * {@code CODEX_HOME} 写 {@link CodexSandboxConfig} 托管沙箱块，并注入 {@code CODEX_HOME}
 * 环境变量。</p>
 *
 * <p><b>本 PR 范围</b>：sandbox TOML 块 + 一次性 exec 调用 + NDJSON 防御性解析。
 * <b>不在本 PR</b>：app-server JSON-RPC 协议（~2000 行客户端）、memory/multi_agent/
 * skill_strip/user_skills/home-link/MCP/AGENTS.md brief、{@code --model} 注入。
 * 见 docs/plan/MULTI-CLI-RUNNER.md。</p>
 *
 * @author sei-online-code
 */
@Component
public class CodexRunner implements CliRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodexRunner.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** codex 可执行文件路径，允许由环境变量覆盖，缺省为 PATH 中的 "codex"。 */
    private final String executable;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CodexRunner() {
        String env = System.getenv("CODEX_EXECUTABLE_PATH");
        this.executable = (env == null || env.isBlank()) ? "codex" : env;
    }

    @Override
    public String tool() {
        return "codex";
    }

    @Override
    public CompletableFuture<String> execute(String iterationId, String prompt, String cwd) {
        return CompletableFuture.supplyAsync(() -> runBlocking(iterationId, null, null, prompt, cwd));
    }

    @Override
    public CompletableFuture<String> execute(String iterationId, String taskId, String runId,
                                             String prompt, String cwd) {
        return CompletableFuture.supplyAsync(() -> runBlocking(iterationId, taskId, runId, prompt, cwd));
    }

    /**
     * 阻塞执行 codex 进程并逐行流式回传。
     *
     * <p>per-run codex-home 在系统临时区建（避免污染用户 {@code ~/.codex/}），进程结束后
     * best-effort 清理。沙箱策略由 {@link CodexSandboxConfig} 按平台写入 config.toml。</p>
     */
    private String runBlocking(String iterationId, String taskId, String runId, String prompt, String cwd) {
        Path codexHome = null;
        try {
            codexHome = Files.createTempDirectory("codex-home-");
            CodexSandboxConfig.write(codexHome, LOGGER);
        } catch (IOException e) {
            LOGGER.warn("codex sandbox config 写入失败，回落无 CODEX_HOME：iterationId={}", iterationId, e);
        }

        List<String> args = buildArgs(prompt);
        ProcessBuilder pb = new ProcessBuilder(args);
        if (cwd != null && !cwd.isBlank()) {
            pb.directory(new java.io.File(cwd));
        }
        if (codexHome != null) {
            pb.environment().put("CODEX_HOME", codexHome.toString());
        }
        StringBuilder output = new StringBuilder();
        try {
            emit(iterationId, taskId, runId, "system", "spawning: " + String.join(" ", args), null);
            Process process = pb.start();

            Thread stderrPump = pumpStderr(iterationId, taskId, runId, process.getErrorStream());
            stderrPump.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                    emit(iterationId, taskId, runId, "stdout", line, null);
                }
            }

            int code = process.waitFor();
            stderrPump.join();
            String finalState = code == 0 ? "PREVIEW" : "FAILED";
            emit(iterationId, taskId, runId, "system", "DONE", finalState);
            return code == 0 ? extractResultJson(output.toString(), iterationId) : null;
        } catch (IOException e) {
            LOGGER.warn("codex spawn failed: iterationId={}", iterationId, e);
            emit(iterationId, taskId, runId, "system", "DONE", "FAILED");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emit(iterationId, taskId, runId, "system", "DONE", "FAILED");
        } finally {
            if (codexHome != null) {
                deleteTree(codexHome);
            }
        }
        return null;
    }

    /**
     * 从 {@code codex exec --json} 的 NDJSON stdout 提取最终文本。
     *
     * <p>逐行解析 JSON 事件，累加 {@code item.type=="agentMessage" && item.phase=="final_answer"}
     * 的 {@code item.text}（对齐 multica app-server 已核实的 item schema）。未命中则回退取
     * 最后一个非空 JSON 行的 {@code text}/{@code result} 字段，再回退裸文本最后非空行。</p>
     *
     * <p>TODO(oma-deferred): {@code codex exec --json} 精确事件字段需对照 {@code codex exec --help}
     * 与真实运行核实（web search 本会话不可用）。当前防御性解析保证不抛、返回 null 时调用方走 fallback。</p>
     */
    String extractResultJson(String stdout, String iterationId) {
        String[] lines = stdout.split("\n", -1);
        StringBuilder finalText = new StringBuilder();
        String lastNonEmpty = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            lastNonEmpty = line;
            try {
                JsonNode node = objectMapper.readTree(line);
                JsonNode item = node.path("item");
                if (!item.isMissingNode()) {
                    if ("agentMessage".equals(item.path("type").asText())
                            && "final_answer".equals(item.path("phase").asText())) {
                        String text = item.path("text").asText("");
                        if (!text.isEmpty()) {
                            finalText.append(text);
                        }
                    }
                } else {
                    // 顶层 text/result 字段（部分事件形态）
                    String text = node.path("text").asText("");
                    if (text.isEmpty()) {
                        text = node.path("result").asText("");
                    }
                    if (!text.isEmpty()) {
                        lastNonEmpty = text;
                    }
                }
            } catch (Exception e) {
                // 非 JSON 行：跳过解析，留作裸文本回退候选
                LOGGER.debug("codex NDJSON 行解析失败 iterationId={} line={}", iterationId, line);
            }
        }
        String stripped = stripFences(finalText.length() > 0 ? finalText.toString()
                : (lastNonEmpty != null ? lastNonEmpty : ""));
        return stripped.isBlank() ? null : stripped;
    }

    private String stripFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }

    /**
     * 构建 codex 启动参数。一次性 exec + NDJSON 输出。
     * TODO(oma-deferred): 接入 --model（runner 需感知 agent.model）与 app-server 协议。
     */
    List<String> buildArgs(String prompt) {
        List<String> args = new ArrayList<>();
        args.add(executable);
        args.add("exec");
        args.add(prompt);
        args.add("--json");
        return args;
    }

    private Thread pumpStderr(String iterationId, String taskId, String runId, InputStream stderr) {
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stderr, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    emit(iterationId, taskId, runId, "stderr", line, null);
                }
            } catch (IOException e) {
                LOGGER.debug("codex stderr pump ended: iterationId={}", iterationId, e);
            }
        }, "codex-stderr-" + iterationId);
    }

    private void emit(String iterationId, String taskId, String runId, String stream, String line, String state) {
        RunLogFrame frame = new RunLogFrame(iterationId, stream, line, LocalDateTime.now().format(TS));
        frame.setTaskId(taskId);
        frame.setRunId(runId);
        frame.setState(state);
        RunLogWebSocketHub.broadcast(frame);
    }

    /** best-effort 递归删除 per-run codex-home，失败仅 debug 日志（OS 临时区会兜底清）。 */
    private static void deleteTree(Path root) {
        if (root == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 残留于系统临时区，OS 会清
                }
            });
        } catch (IOException e) {
            LOGGER.debug("codex-home 清理失败 root={}", root, e);
        }
    }
}
