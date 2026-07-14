package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.service.agent.AgentRunRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * CliRunner 注册表。参考 multica {@code server/pkg/agent/agent.go} 的 {@code New(agentType, cfg)}
 * 分发——按 {@link CliRunner#tool()} 索引，由 {@code Agent.cliTool} 选择。
 *
 * <p>Spring 注入所有 {@link CliRunner} bean，按 {@code tool()} 建 map。
 * {@link #resolve(String)} 在 tool 为 null/blank/未知时回落默认 claude（向后兼容：
 * 既有 agent {@code cliTool} 列为 null，行为不变）。</p>
 *
 * <p>所有业务调用应使用带 {@link AgentInvocationContext} 的入口，确保每次 CLI 启动前已有
 * 唯一已提交的 Agent Run。</p>
 *
 * @author sei-online-code
 */
@Component
public class CliRunnerRegistry {

    /** 默认 CLI 工具（向后兼容既有 null cliTool 的 agent）。 */
    public static final String DEFAULT_TOOL = "claude";

    private final Map<String, CliRunner> runners;
    private final WorkspaceManager workspaceManager;
    private final AgentRunRecorder agentRunRecorder;

    @Autowired
    public CliRunnerRegistry(List<CliRunner> runners, WorkspaceManager workspaceManager,
                             AgentRunRecorder agentRunRecorder) {
        this.runners = new HashMap<>();
        this.workspaceManager = workspaceManager;
        this.agentRunRecorder = agentRunRecorder;
        for (CliRunner r : runners) {
            this.runners.put(r.tool(), r);
        }
    }

    CliRunnerRegistry(List<CliRunner> runners) {
        this(runners, null, null);
    }

    /**
     * 按 tool 名解析 runner。null/blank/未知 → 默认 claude runner。
     *
     * @param tool Agent.cliTool 取值（可为 null）
     * @return 命中 runner；未命中返回默认 claude runner
     */
    CliRunner resolve(String tool) {
        if (tool == null || tool.isBlank()) {
            return runners.get(DEFAULT_TOOL);
        }
        CliRunner r = runners.get(tool);
        return r != null ? r : runners.get(DEFAULT_TOOL);
    }

    /** 默认 tool 名。 */
    public String defaultTool() {
        return DEFAULT_TOOL;
    }

    /** Resolve and validate the only workspace in which a project's agents may run. */
    public AgentWorkspace workspace(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("Agent 执行缺少 projectId，拒绝解析工作区");
        }
        if (workspaceManager == null) {
            throw new IllegalStateException("WorkspaceManager 未配置，拒绝启动 Agent");
        }
        WorkspaceResolveResult resolved = workspaceManager.resolve(projectId);
        if (resolved == null || resolved.getPath() == null || resolved.getPath().isBlank()) {
            throw new IllegalStateException("项目工作区解析失败: " + projectId);
        }
        Path path = Path.of(resolved.getPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException("项目工作区不存在或不是目录: " + path);
        }
        return new AgentWorkspace(projectId, path);
    }

    /**
     * 执行一次 Agent CLI 调用，返回完整结果。
     *
     * @param workspace 已验证的项目工作区
     * @param context   调用上下文，runId 必填
     * @param prompt    提示词
     * @param mcpConfig MCP 配置 JSON
     * @return 完成后携带 {@link CliRunResult} 的 future
     */
    public CompletableFuture<CliRunResult> executeDetailed(AgentWorkspace workspace,
                                                           AgentInvocationContext context,
                                                           String prompt,
                                                           String mcpConfig) {
        Objects.requireNonNull(context, "AgentInvocationContext 不能为空");
        Objects.requireNonNull(context.runId(), "runId 不能为空");
        String cwd = validate(workspace);
        String tool = context.cliTool();
        if (tool != null && !tool.isBlank() && !runners.containsKey(tool)) {
            throw new IllegalArgumentException("未知的 CLI 工具: " + tool);
        }
        CompletableFuture<CliRunResult> future = resolve(tool).executeDetailed(
                context.iterationId(),
                context.taskId(),
                context.runId(),
                prompt,
                cwd,
                context.model(),
                mcpConfig);
        // CLI 完成后定向落 usage；落库失败不得改变业务 output future。
        if (agentRunRecorder != null) {
            future = future.whenComplete((result, ex) -> persistUsage(context.runId(), result, ex));
        }
        return future;
    }

    private void persistUsage(String runId, CliRunResult result, Throwable ex) {
        try {
            if (result != null && result.getUsage() != null) {
                agentRunRecorder.updateUsage(runId, result.getUsage());
            }
        } catch (Exception e) {
            // usage 落库失败不影响业务输出。
        }
    }

    private String validate(AgentWorkspace workspace) {
        Objects.requireNonNull(workspace, "Agent 工作区绑定不能为空");
        AgentWorkspace current = workspace(workspace.projectId());
        if (!current.path().equals(workspace.path())) {
            throw new IllegalStateException("项目工作区已变化，拒绝在旧工作区启动 Agent: projectId="
                    + workspace.projectId() + ", expected=" + current.path() + ", actual=" + workspace.path());
        }
        return current.pathString();
    }

    /** Cancel a run without requiring the caller to know which vendor owns it. */
    public boolean cancel(String runId) {
        boolean cancelled = false;
        for (CliRunner runner : runners.values()) {
            cancelled |= runner.cancel(runId);
        }
        return cancelled;
    }
}
