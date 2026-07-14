package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dto.run.RunLogFrame;
import com.changhong.onlinecode.ws.RunLogWebSocketHub;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * CodexRunner。
 *
 * <p>以 {@link ProcessBuilder} spawn {@code codex app-server --listen stdio://}，
 * 通过 JSON-RPC stdio 启动 thread/turn，stdout/stderr 逐行流式推送到 {@link RunLogWebSocketHub}。
 * spawn 前在 per-run
 * {@code CODEX_HOME} 经 {@link CodexSandboxConfig} 写托管块（sandbox + memory/multi_agent 禁用 +
 * skill_strip）、链接共享家目录（auth.json/sessions/plugins/cache + instructions.md/config.json
 * 拷贝）、播种用户 skills、写 MCP 托管块，再注入 {@code CODEX_HOME} 环境变量。</p>
 *
 * <p><b>代理 env（PR1.1）</b>：{@link ProcessBuilder} 默认继承当前 JVM 进程环境，
 * 故 JVM 启动时已存在的 {@code HTTPS_PROXY}/{@code HTTP_PROXY}/{@code NO_PROXY} 会自动透传给
 * codex 子进程。注意：shell 里给 codex 设的 alias 不会进入 JVM——后端启动器
 * （{@code dev-start.sh} / systemd unit）必须显式 export 这些变量，codex 才能经代理访问 OpenAI。</p>
 *
 * @author sei-online-code
 */
@Component
public class CodexRunner implements CliRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodexRunner.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** codex 可执行文件路径，允许由环境变量覆盖，缺省为 PATH 中的 "codex"。 */
    private final String executable;
    private final ConcurrentMap<String, Process> activeProcesses = new ConcurrentHashMap<>();

    public CodexRunner() {
        String env = System.getenv("CODEX_EXECUTABLE_PATH");
        this.executable = (env == null || env.isBlank()) ? "codex" : env;
    }

    /**
     * 测试专用构造函数：显式指定可执行文件路径（绕过 {@code CODEX_EXECUTABLE_PATH} env）。
     *
     * <p>WHY：real-codex e2e 依赖 OpenAI 网络，CI / 受限网络环境跑不动；测试可显式指定
     * fake 可执行脚本离线验证 Java 侧进程接线。</p>
     */
    CodexRunner(String executable) {
        this.executable = executable;
    }

    @Override
    public String tool() {
        return "codex";
    }

    @Override
    public CompletableFuture<String> execute(String iterationId, String prompt, String cwd, String model, String mcpConfig) {
        return CompletableFuture.supplyAsync(() -> runBlocking(iterationId, null, null, prompt, cwd, model, mcpConfig));
    }

    @Override
    public CompletableFuture<String> execute(String iterationId, String taskId, String runId,
                                             String prompt, String cwd, String model, String mcpConfig) {
        return CompletableFuture.supplyAsync(() -> runBlocking(iterationId, taskId, runId, prompt, cwd, model, mcpConfig));
    }

    /**
     * 阻塞执行 codex 进程并逐行流式回传。
     *
     * <p>per-run codex-home 在系统临时区建（避免污染用户 {@code ~/.codex/}），进程结束后
     * best-effort 清理。沙箱策略由 {@link CodexSandboxConfig} 按平台写入 config.toml。</p>
     */
    private String runBlocking(String iterationId, String taskId, String runId, String prompt,
                                String cwd, String model, String mcpConfig) {
        Path codexHome = null;
        try {
            codexHome = Files.createTempDirectory("codex-home-");
            Path writableRoot = (cwd == null || cwd.isBlank())
                    ? null : Path.of(cwd).toAbsolutePath().normalize();
            CodexSandboxConfig.write(codexHome, writableRoot, LOGGER);
            CodexSandboxConfig.linkSharedHome(codexHome, LOGGER);
            CodexSandboxConfig.seedUserSkills(codexHome, LOGGER);
            CodexSandboxConfig.writeMcpBlock(codexHome, mcpConfig, LOGGER);
        } catch (IOException e) {
            // 覆盖 sandbox 写块 / MCP 块（mcpConfig 畸形时 writeMcpBlock 抛）。
            // codexHome 可能已部分配置（如 MCP 块未写但 sandbox 块已写）——soft-fail：仍注入 CODEX_HOME
            // 让 codex 跑（损失 MCP/sandbox 能力但不阻断任务），由 e 携带的具体消息定位根因。
            LOGGER.warn("codex spawn 前置配置失败（sandbox/MCP），soft-fail 继续运行：iterationId={}", iterationId, e);
        }

        List<String> args = buildArgs();
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
            if (runId != null) {
                activeProcesses.put(runId, process);
            }

            Thread stderrPump = pumpStderr(iterationId, taskId, runId, process.getErrorStream());
            stderrPump.start();
            Thread stdoutPump = null;

            try {
                CodexAppServerEvents events = new CodexAppServerEvents();
                CodexAppServerClient client = new CodexAppServerClient(
                        process.getOutputStream(),
                        line -> emit(iterationId, taskId, runId, "stdout", line, null),
                        events);
                stdoutPump = pumpAppServerStdout(iterationId, taskId, runId, process.getInputStream(), client);
                stdoutPump.start();

                client.request("initialize", Map.of(
                        "clientInfo", Map.of(
                                "name", "sei-online-code",
                                "title", "SEI Online Code",
                                "version", "0.1.0"),
                        "capabilities", Map.of("experimentalApi", true))).get(30, TimeUnit.SECONDS);
                client.notify("initialized");

                JsonNode threadResult = client.request("thread/start", threadStartParams(cwd, model))
                        .get(30, TimeUnit.SECONDS);
                String threadId = threadResult.path("thread").path("id").asText();
                if (threadId.isBlank()) {
                    throw new IllegalStateException("codex app-server thread/start did not return thread.id");
                }
                client.request("turn/start", Map.of(
                        "threadId", threadId,
                        "input", List.of(Map.of("type", "text", "text", prompt == null ? "" : prompt))))
                        .get(30, TimeUnit.SECONDS);

                waitForTurn(events, process, Duration.ofMinutes(30));

                if (events.isFailed()) {
                    LOGGER.warn("codex app-server turn failed: iterationId={} reason={}", iterationId, events.failureReason());
                    emit(iterationId, taskId, runId, "system", "DONE", "FAILED");
                    return null;
                }
                String output = stripFences(events.output());
                emit(iterationId, taskId, runId, "system", "DONE", "PREVIEW");
                return output.isBlank() ? null : output;
            } finally {
                if (runId != null) {
                    activeProcesses.remove(runId);
                }
                stopProcess(process);
                stderrPump.join(5_000);
                if (stdoutPump != null) {
                    stdoutPump.join(5_000);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("codex spawn failed: iterationId={}", iterationId, e);
            emit(iterationId, taskId, runId, "system", "DONE", "FAILED");
        } catch (TimeoutException e) {
            LOGGER.warn("codex app-server timed out: iterationId={}", iterationId, e);
            emit(iterationId, taskId, runId, "system", "DONE", "FAILED");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emit(iterationId, taskId, runId, "system", "DONE", "FAILED");
        } catch (Exception e) {
            LOGGER.warn("codex app-server failed: iterationId={}", iterationId, e);
            emit(iterationId, taskId, runId, "system", "DONE", "FAILED");
        } finally {
            if (codexHome != null) {
                deleteTree(codexHome);
            }
        }
        return null;
    }

    @Override
    public boolean cancel(String runId) {
        Process process = activeProcesses.get(runId);
        if (process == null) {
            return false;
        }
        process.destroy();
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        return true;
    }

    String stripFences(String text) {
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

    List<String> buildArgs() {
        return List.of(executable, "app-server", "--listen", "stdio://");
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

    private Thread pumpAppServerStdout(String iterationId, String taskId, String runId,
                                       InputStream stdout, CodexAppServerClient client) {
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stdout, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    emit(iterationId, taskId, runId, "stdout", line, null);
                    client.handleLine(line);
                }
                client.failPendingRequests(new IOException("codex app-server stdout closed"));
            } catch (IOException e) {
                LOGGER.debug("codex app-server stdout pump ended: iterationId={}", iterationId, e);
                client.failPendingRequests(e);
            }
        }, "codex-stdout-" + iterationId);
    }

    Map<String, Object> threadStartParams(String cwd, String model) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (cwd != null && !cwd.isBlank()) {
            params.put("cwd", cwd);
        }
        if (model != null && !model.isBlank()) {
            params.put("model", model);
        }
        return params;
    }

    private void waitForTurn(CodexAppServerEvents events, Process process, Duration timeout)
            throws InterruptedException, TimeoutException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!events.isTurnDone()) {
            if (!process.isAlive()) {
                events.markFailed("codex app-server process exited before turn completed");
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new TimeoutException("codex app-server turn timed out after " + timeout);
            }
            Thread.sleep(100);
        }
    }

    private void stopProcess(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
        }
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
