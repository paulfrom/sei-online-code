package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dto.enums.UsageStatus;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private final ConcurrentMap<String, Process> activeProcesses = new ConcurrentHashMap<>();

    public ClaudeRunner() {
        this(defaultExecutable());
    }

    ClaudeRunner(String executable) {
        this.executable = executable;
    }

    private static String defaultExecutable() {
        String env = System.getenv("CLAUDE_EXECUTABLE_PATH");
        return (env == null || env.isBlank()) ? "claude" : env;
    }

    /**
     * 异步执行一次 claude 运行，返回完整结果。
     */
    @Override
    public CompletableFuture<CliRunResult> executeDetailed(String iterationId, String taskId, String runId,
                                                           String prompt, String cwd, String model, String mcpConfig) {
        return CompletableFuture.supplyAsync(() -> runBlocking(iterationId, taskId, runId, prompt, cwd, model, mcpConfig));
    }

    /**
     * 阻塞执行 claude 进程并逐行流式回传（供 {@link #executeDetailed} 在独立线程调用）。
     *
     * @param iterationId 迭代 id
     * @param taskId      任务 id（可为 null）
     * @param runId       运行 id（可为 null）
     * @param prompt      提示词
     * @param cwd         工作目录（可为 null）
     * @param model       模型名（可为 null/blank）
     * @param mcpConfig   MCP server 配置 JSON（可为 null/blank）
     * @return 单次运行结果
     */
    private CliRunResult runBlocking(String iterationId, String taskId, String runId, String prompt,
                                      String cwd, String model, String mcpConfig) {
        Path mcpConfigFile = null;
        StringBuilder output = new StringBuilder();
        try {
            mcpConfigFile = writeMcpConfigFile(mcpConfig);
            List<String> args = buildArgs(prompt, model, mcpConfigFile, cwd);
            ProcessBuilder pb = new ProcessBuilder(args);
            if (cwd != null && !cwd.isBlank()) {
                pb.directory(new java.io.File(cwd));
            }
            emit(iterationId, taskId, runId, "system", "spawning: " + String.join(" ", args), null);
            Process process = pb.start();
            if (runId != null) {
                activeProcesses.put(runId, process);
            }

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
            String stdout = output.toString();
            Envelope envelope = parseEnvelope(stdout, iterationId);
            AgentUsage usage = envelope.usage();
            String finalState = code == 0 ? "PREVIEW" : "FAILED";
            emit(iterationId, taskId, runId, "system", "DONE", finalState);
            if (code == 0) {
                return successResult(envelope.result(), usage);
            }
            // 非零退出码但 stdout 中存在可解析 usage 时，仍尽力保存。
            return failedResult("claude exited with code " + code, usage);
        } catch (IOException e) {
            LOGGER.warn("claude spawn failed: iterationId={}", iterationId, e);
            emit(iterationId, taskId, runId, "system", "DONE", "FAILED");
            return failedResult(e.getMessage(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emit(iterationId, taskId, runId, "system", "DONE", "FAILED");
            return failedResult("interrupted", null);
        } finally {
            if (runId != null) {
                activeProcesses.remove(runId);
            }
            if (mcpConfigFile != null) {
                try {
                    Files.deleteIfExists(mcpConfigFile);
                } catch (IOException e) {
                    LOGGER.debug("claude mcp config temp file cleanup failed: iterationId={}, path={}",
                            iterationId, mcpConfigFile, e);
                }
            }
        }
    }

    private CliRunResult successResult(String output, AgentUsage usage) {
        CliRunResult result = new CliRunResult();
        result.setOutput(output);
        result.setUsage(usage);
        result.setProcessSucceeded(true);
        return result;
    }

    private CliRunResult failedResult(String reason, AgentUsage usage) {
        CliRunResult result = new CliRunResult();
        result.setUsage(usage);
        result.setProcessSucceeded(false);
        result.setFailureReason(reason);
        return result;
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

    /**
     * 解析 claude {@code --output-format json} 的完整 envelope。
     *
     * <p>从 {@code result} 提取业务输出并剥离 markdown 围栏；从 {@code usage} 提取 token 并归一化。
     * envelope 表示错误但包含 usage 时，仍保存 usage。</p>
     */
    private Envelope parseEnvelope(String stdout, String iterationId) {
        try {
            JsonNode node = objectMapper.readTree(stdout);
            String result = extractResultText(node);
            AgentUsage usage = extractUsage(node);
            return new Envelope(result, usage);
        } catch (Exception e) {
            LOGGER.warn("claude result envelope parse failed, iterationId={}, rawHead={}",
                    iterationId, stdout.substring(0, Math.min(stdout.length(), 200)));
            AgentUsage unavailable = new AgentUsage();
            unavailable.setStatus(UsageStatus.UNAVAILABLE);
            return new Envelope(null, unavailable);
        }
    }

    private String extractResultText(JsonNode node) {
        JsonNode result = node.path("result");
        if (result.isMissingNode()) {
            return null;
        }
        String text = result.isTextual() ? result.asText() : result.toString();
        String stripped = stripFences(text);
        return stripped.isBlank() ? null : stripped;
    }

    private AgentUsage extractUsage(JsonNode node) {
        JsonNode usageNode = node.path("usage");
        if (usageNode.isMissingNode() || !usageNode.isObject()) {
            AgentUsage unavailable = new AgentUsage();
            unavailable.setStatus(UsageStatus.UNAVAILABLE);
            return unavailable;
        }
        long input = readLongDefaultZero(usageNode.path("input_tokens"));
        long cacheRead = readLongDefaultZero(usageNode.path("cache_read_input_tokens"));
        long cacheWrite = readLongDefaultZero(usageNode.path("cache_creation_input_tokens"));
        long output = readLongDefaultZero(usageNode.path("output_tokens"));

        AgentUsage usage = new AgentUsage();
        usage.setInputTokens(input + cacheRead + cacheWrite);
        usage.setOutputTokens(output);
        usage.setCacheReadTokens(cacheRead > 0 ? cacheRead : null);
        usage.setCacheWriteTokens(cacheWrite > 0 ? cacheWrite : null);
        usage.setTotalTokens(input + cacheRead + cacheWrite + output);
        usage.setStatus(UsageStatus.COMPLETE);
        usage.setRawUsageJson(usageNode.toString());
        return usage;
    }

    private static long readLongDefaultZero(JsonNode node) {
        if (node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return 0L;
        }
        long value = node.asLong();
        return value >= 0 ? value : 0L;
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
     * @param model  模型名（null/blank → 不注入，用 claude 默认模型）
     * @param mcpConfigFile MCP 配置临时文件（null → 不注入）
     * @return 命令行参数
     */
    List<String> buildArgs(String prompt, String model, Path mcpConfigFile, String cwd) {
        List<String> args = new ArrayList<>();
        args.add(executable);
        args.add("-p");
        args.add(prompt);
        args.add("--output-format");
        args.add("json");
        if (cwd != null && !cwd.isBlank()) {
            args.add("--add-dir");
            args.add(Path.of(cwd).toAbsolutePath().normalize().toString());
        }
        args.add("--permission-mode");
        args.add("bypassPermissions");
        args.add("--allowedTools");
        args.add("Read,Glob,Grep,LS,Edit,MultiEdit,Write,Bash");
        if (model != null && !model.isBlank()) {
            args.add("--model");
            args.add(model);
        }
        if (mcpConfigFile != null) {
            args.add("--mcp-config");
            args.add(mcpConfigFile.toString());
            args.add("--strict-mcp-config");
        }
        // TODO(oma-deferred): 接入 stream-json 协议与控制帧放行（当前用 result json 一次性取回）
        return args;
    }

    private Path writeMcpConfigFile(String mcpConfig) throws IOException {
        if (mcpConfig == null || mcpConfig.isBlank()) {
            return null;
        }
        try {
            objectMapper.readTree(mcpConfig);
        } catch (IOException e) {
            LOGGER.warn("claude mcp config ignored because it is not valid JSON", e);
            return null;
        }
        Path file = Files.createTempFile("claude-mcp-", ".json");
        Files.writeString(file, mcpConfig, StandardCharsets.UTF_8);
        return file;
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

    private record Envelope(String result, AgentUsage usage) {
    }
}
