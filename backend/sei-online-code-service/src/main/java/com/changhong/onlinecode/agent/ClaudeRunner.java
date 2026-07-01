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
public class ClaudeRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClaudeRunner.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** claude 可执行文件路径，允许由环境变量覆盖，缺省为 PATH 中的 "claude"。 */
    private final String executable;

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
    public CompletableFuture<String> execute(String iterationId, String prompt, String cwd) {
        return CompletableFuture.supplyAsync(() -> runBlocking(iterationId, prompt, cwd));
    }

    /**
     * 阻塞执行 claude 进程并逐行流式回传（供 {@link #execute} 在独立线程调用）。
     *
     * @param iterationId 迭代 id
     * @param prompt      提示词
     * @param cwd         工作目录（可为 null）
     * @return 聚合 stdout
     */
    private String runBlocking(String iterationId, String prompt, String cwd) {
        List<String> args = buildArgs(prompt);
        ProcessBuilder pb = new ProcessBuilder(args);
        if (cwd != null && !cwd.isBlank()) {
            pb.directory(new java.io.File(cwd));
        }
        StringBuilder output = new StringBuilder();
        try {
            emit(iterationId, "system", "spawning: " + String.join(" ", args), null);
            Process process = pb.start();

            // stderr 独立线程读取，避免与 stdout 相互阻塞（参考 claude.go 的双 goroutine）。
            Thread stderrPump = pumpStderr(iterationId, process.getErrorStream());
            stderrPump.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                    emit(iterationId, "stdout", line, null);
                }
            }

            int code = process.waitFor();
            stderrPump.join();
            String finalState = code == 0 ? "PREVIEW" : "FAILED";
            emit(iterationId, "system", "DONE", finalState);
        } catch (IOException e) {
            LOGGER.warn("claude spawn failed: iterationId={}", iterationId, e);
            emit(iterationId, "system", "DONE", "FAILED");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emit(iterationId, "system", "DONE", "FAILED");
        }
        return output.toString();
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
        // TODO(oma-deferred): 接入 stream-json 协议（--output-format stream-json 等）与控制帧放行
        args.add(prompt);
        return args;
    }

    private Thread pumpStderr(String iterationId, InputStream stderr) {
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stderr, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    emit(iterationId, "stderr", line, null);
                }
            } catch (IOException e) {
                LOGGER.debug("claude stderr pump ended: iterationId={}", iterationId, e);
            }
        }, "claude-stderr-" + iterationId);
    }

    private void emit(String iterationId, String stream, String line, String state) {
        RunLogFrame frame = new RunLogFrame(iterationId, stream, line, LocalDateTime.now().format(TS));
        frame.setState(state);
        RunLogWebSocketHub.broadcast(frame);
    }
}
