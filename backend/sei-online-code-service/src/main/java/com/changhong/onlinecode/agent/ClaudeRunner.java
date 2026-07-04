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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * ClaudeRunner 骨架（B7）。参考 multica {@code server/pkg/agent/claude.go}。
 *
 * <p>以 {@link ProcessBuilder} spawn {@code claude} CLI，将 stdout/stderr 逐行
 * 流式推送到 {@link RunLogWebSocketHub}。本轮为 compile-only stub：进程 spawn 与
 * 流式读取骨架已就绪，但不在本轮运行；真正的 stream-json 协议解析、控制帧
 * 自动放行、超时/会话恢复等属后续接入项。</p>
 *
 * @author sei-online-code
 */
@Component
public class ClaudeRunner implements CliRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClaudeRunner.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public String tool() {
        return "claude";
    }

    /** claude 可执行文件路径，允许由环境变量覆盖，缺省为 PATH 中的 "claude"。 */
    private final String executable;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeRunner() {
        String env = System.getenv("CLAUDE_EXECUTABLE_PATH");
        this.executable = (env == null || env.isBlank()) ? "claude" : env;
    }

    /**
     * 异步执行一次 claude 运行，流式回传日志。
     *
     * @param iterationId 迭代 id（用于日志帧路由）
     * @param prompt      提示词
     * @param cwd         工作目录（可为 null，表示继承当前目录）
     * @return 完成后携带聚合 stdout 的 future
     */
    @Override
    public CompletableFuture<String> execute(String iterationId, String prompt, String cwd) {
        return CompletableFuture.supplyAsync(() -> runBlocking(iterationId, null, null, prompt, cwd));
    }

    /**
     * 异步执行一次 claude 运行，带 taskId/runId（Phase 2 并行 fan-out 场景）。
     *
     * @param iterationId 迭代 id
     * @param taskId      任务 id（日志帧路由）
     * @param runId       运行 id（日志帧路由）
     * @param prompt      提示词
     * @param cwd         工作目录（可为 null）
     * @return 完成后携带聚合 stdout 的 future
     */
    @Override
    public CompletableFuture<String> execute(String iterationId, String taskId, String runId,
                                             String prompt, String cwd) {
        return CompletableFuture.supplyAsync(() -> runBlocking(iterationId, taskId, runId, prompt, cwd));
    }

    /**
     * 阻塞执行 claude 进程并逐行流式回传（供 {@link #execute} 在独立线程调用）。
     *
     * @param iterationId 迭代 id
     * @param taskId      任务 id（可为 null）
     * @param runId       运行 id（可为 null）
     * @param prompt      提示词
     * @param cwd         工作目录（可为 null）
     * @return 聚合 stdout
     */
    private String runBlocking(String iterationId, String taskId, String runId, String prompt, String cwd) {
        List<String> args = buildArgs(prompt);
        ProcessBuilder pb = new ProcessBuilder(args);
        if (cwd != null && !cwd.isBlank()) {
            pb.directory(new java.io.File(cwd));
        }
        StringBuilder output = new StringBuilder();
        try {
            emit(iterationId, taskId, runId, "system", "spawning: " + String.join(" ", args), null);
            Process process = pb.start();

            // stderr 独立线程读取，避免与 stdout 相互阻塞（参考 claude.go 的双 goroutine）。
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
            LOGGER.warn("claude spawn failed: iterationId={}", iterationId, e);
            emit(iterationId, taskId, runId, "system", "DONE", "FAILED");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emit(iterationId, taskId, runId, "system", "DONE", "FAILED");
        }
        return null;
    }

    /**
     * 从 claude {@code --output-format json} 的 stdout 提取 {@code result} 字段并剥离 markdown 围栏，
     * 返回供调用方直接解析的 JSON 串。envelope 解析失败或 result 缺失时返回 null（调用方走 fallback）。
     */
    private String extractResultJson(String stdout, String iterationId) {
        try {
            JsonNode node = objectMapper.readTree(stdout);
            JsonNode result = node.path("result");
            String text = result.isMissingNode() ? stdout.trim() : result.asText();
            String stripped = stripFences(text);
            return stripped.isBlank() ? null : stripped;
        } catch (Exception e) {
            LOGGER.warn("claude result envelope parse failed, iterationId={}, rawHead={}",
                    iterationId, stdout.substring(0, Math.min(stdout.length(), 200)));
            return null;
        }
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
     * 构建 claude 启动参数。本轮为最小非交互调用骨架。
     *
     * @param prompt 提示词
     * @return 命令行参数
     */
    private List<String> buildArgs(String prompt) {
        List<String> args = new ArrayList<>();
        args.add(executable);
        args.add("-p");
        args.add(prompt);
        args.add("--output-format");
        args.add("json");
        // TODO(oma-deferred): 接入 stream-json 协议与控制帧放行（当前用 result json 一次性取回）
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
                LOGGER.debug("claude stderr pump ended: iterationId={}", iterationId, e);
            }
        }, "claude-stderr-" + iterationId);
    }

    private void emit(String iterationId, String taskId, String runId, String stream, String line, String state) {
        RunLogFrame frame = new RunLogFrame(iterationId, stream, line, LocalDateTime.now().format(TS));
        frame.setTaskId(taskId);
        frame.setRunId(runId);
        frame.setState(state);
        RunLogWebSocketHub.broadcast(frame);
    }
}
