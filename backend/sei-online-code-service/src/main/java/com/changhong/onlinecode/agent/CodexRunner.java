package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dto.run.RunLogFrame;
import com.changhong.onlinecode.ws.RunLogWebSocketHub;
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
 * CodexRunner（PR1 + PR1.1）。参考 multica {@code server/pkg/agent/codex.go}。
 *
 * <p>以 {@link ProcessBuilder} spawn {@code codex exec <prompt> --json -o <file> [-m <model>]}
 * （一次性），stdout/stderr 逐行流式推送到 {@link RunLogWebSocketHub}。spawn 前在 per-run
 * {@code CODEX_HOME} 经 {@link CodexSandboxConfig} 写托管块（sandbox + memory/multi_agent 禁用 +
 * skill_strip）并播种用户 skills，再注入 {@code CODEX_HOME} 环境变量。</p>
 *
 * <p><b>结果提取（PR1.1）</b>：用 {@code -o <tempfile>} 让 codex 自身把最终消息写入文件，
 * runBlocking 末尾读文件——替代 PR1 的 NDJSON {@code item.text} 猜解（PR1 该字段未在线核实，
 * 为最大风险项）。{@code --json} 仍保留，仅用于 stdout 流式事件可见性，不再参与结果解析。</p>
 *
 * <p><b>代理 env（PR1.1）</b>：{@link ProcessBuilder} 默认继承当前 JVM 进程环境，
 * 故 JVM 启动时已存在的 {@code HTTPS_PROXY}/{@code HTTP_PROXY}/{@code NO_PROXY} 会自动透传给
 * codex 子进程。注意：shell 里给 codex 设的 alias 不会进入 JVM——后端启动器
 * （{@code dev-start.sh} / systemd unit）必须显式 export 这些变量，codex 才能经代理访问 OpenAI。</p>
 *
 * <p><b>不在本 PR</b>：app-server JSON-RPC 协议（~2000 行客户端）、home-link/MCP/AGENTS.md brief。
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

    public CodexRunner() {
        String env = System.getenv("CODEX_EXECUTABLE_PATH");
        this.executable = (env == null || env.isBlank()) ? "codex" : env;
    }

    /**
     * 测试专用构造函数：显式指定可执行文件路径（绕过 {@code CODEX_EXECUTABLE_PATH} env）。
     *
     * <p>WHY：real-codex e2e 依赖 OpenAI 网络，CI / 受限网络环境跑不动；用 fake 可执行脚本
     * 替身可离线验证 {@code -o} 落盘→读取→剥围栏 的 Java 接线端到端正确。</p>
     */
    CodexRunner(String executable) {
        this.executable = executable;
    }

    @Override
    public String tool() {
        return "codex";
    }

    @Override
    public CompletableFuture<String> execute(String iterationId, String prompt, String cwd, String model) {
        return CompletableFuture.supplyAsync(() -> runBlocking(iterationId, null, null, prompt, cwd, model));
    }

    @Override
    public CompletableFuture<String> execute(String iterationId, String taskId, String runId,
                                             String prompt, String cwd, String model) {
        return CompletableFuture.supplyAsync(() -> runBlocking(iterationId, taskId, runId, prompt, cwd, model));
    }

    /**
     * 阻塞执行 codex 进程并逐行流式回传。
     *
     * <p>per-run codex-home 与结果输出文件均在系统临时区建（避免污染用户 {@code ~/.codex/}），
     * 进程结束后 best-effort 清理。沙箱策略由 {@link CodexSandboxConfig} 按平台写入 config.toml。</p>
     */
    private String runBlocking(String iterationId, String taskId, String runId, String prompt,
                                String cwd, String model) {
        Path codexHome = null;
        Path outputFile = null;
        try {
            codexHome = Files.createTempDirectory("codex-home-");
            CodexSandboxConfig.write(codexHome, LOGGER);
            CodexSandboxConfig.seedUserSkills(codexHome, LOGGER);
            // 预占唯一路径供 codex -o 写入；codex 会覆盖空文件。
            outputFile = Files.createTempFile("codex-last-", ".txt");
        } catch (IOException e) {
            LOGGER.warn("codex sandbox config / 输出文件创建失败，回落无 CODEX_HOME：iterationId={}", iterationId, e);
        }

        List<String> args = buildArgs(prompt, model, outputFile);
        ProcessBuilder pb = new ProcessBuilder(args);
        if (cwd != null && !cwd.isBlank()) {
            pb.directory(new java.io.File(cwd));
        }
        if (codexHome != null) {
            pb.environment().put("CODEX_HOME", codexHome.toString());
        }
        // 代理 env：ProcessBuilder 默认继承 JVM 环境（HTTPS_PROXY/HTTP_PROXY/NO_PROXY），
        // 无需显式复制——见类 javadoc。启动器负责 export。
        try {
            emit(iterationId, taskId, runId, "system", "spawning: " + String.join(" ", args), null);
            Process process = pb.start();

            Thread stderrPump = pumpStderr(iterationId, taskId, runId, process.getErrorStream());
            stderrPump.start();

            // stdout 仅作流式日志展示（--json 事件流），不再用于结果提取。
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    emit(iterationId, taskId, runId, "stdout", line, null);
                }
            }

            int code = process.waitFor();
            stderrPump.join();
            String finalState = code == 0 ? "PREVIEW" : "FAILED";
            emit(iterationId, taskId, runId, "system", "DONE", finalState);
            return code == 0 ? readResultFile(outputFile) : null;
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
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException e) {
                    LOGGER.debug("codex 输出文件清理失败 file={}", outputFile, e);
                }
            }
        }
        return null;
    }

    /**
     * 读取 {@code codex exec -o <file>} 写出的最终消息文件并剥离 markdown 围栏。
     *
     * <p>WHY：codex 自身把最终消息落盘，是结果文本的权威来源——替代 PR1 对未核实 NDJSON
     * {@code item.text} 字段的猜解。文件缺失/空（codex 未产出最终消息）返回 null，
     * 调用方走既有 fallback（如 PlanAgentService 的 canned 设计）。</p>
     *
     * @param outputFile codex -o 写入的目标文件（可为 null，表示 spawn 前建文件失败）
     * @return 剥围栏后的文本；空或缺失返回 null
     */
    String readResultFile(Path outputFile) {
        if (outputFile == null || !Files.isReadable(outputFile)) {
            return null;
        }
        String content;
        try {
            content = Files.readString(outputFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("codex 输出文件读取失败 file={}", outputFile, e);
            return null;
        }
        String stripped = stripFences(content);
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
     * 构建 codex 启动参数。一次性 exec + NDJSON 事件流（{@code --json}）+ 最终消息落盘（{@code -o}）。
     *
     * @param prompt     提示词
     * @param model      模型名（null/blank → 不注入，走 codex config.toml 默认）
     * @param outputFile {@code -o} 目标文件（null → 不加 -o，仅退化场景；正常路径非空）
     * @return 命令行参数
     */
    List<String> buildArgs(String prompt, String model, Path outputFile) {
        List<String> args = new ArrayList<>();
        args.add(executable);
        args.add("exec");
        args.add(prompt);
        args.add("--json");
        if (outputFile != null) {
            args.add("-o");
            args.add(outputFile.toString());
        }
        if (model != null && !model.isBlank()) {
            args.add("-m");
            args.add(model);
        }
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
