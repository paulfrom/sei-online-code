package com.changhong.onlinecode.agent;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CliRunner 注册表。参考 multica {@code server/pkg/agent/agent.go} 的 {@code New(agentType, cfg)}
 * 分发——按 {@link CliRunner#tool()} 索引，由 {@code Agent.cliTool} 选择。
 *
 * <p>Spring 注入所有 {@link CliRunner} bean，按 {@code tool()} 建 map。
 * {@link #resolve(String)} 在 tool 为 null/blank/未知时回落默认 claude（向后兼容：
 * 既有 agent {@code cliTool} 列为 null，行为不变）。</p>
 *
 * @author sei-online-code
 */
@Component
public class CliRunnerRegistry {

    /** 默认 CLI 工具（向后兼容既有 null cliTool 的 agent）。 */
    public static final String DEFAULT_TOOL = "claude";

    private final Map<String, CliRunner> runners;

    public CliRunnerRegistry(List<CliRunner> runners) {
        this.runners = new HashMap<>();
        for (CliRunner r : runners) {
            this.runners.put(r.tool(), r);
        }
    }

    /**
     * 按 tool 名解析 runner。null/blank/未知 → 默认 claude runner。
     *
     * @param tool Agent.cliTool 取值（可为 null）
     * @return 命中 runner；未命中返回默认 claude runner
     */
    public CliRunner resolve(String tool) {
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

    /** Cancel a run without requiring the caller to know which vendor owns it. */
    public boolean cancel(String runId) {
        boolean cancelled = false;
        for (CliRunner runner : runners.values()) {
            cancelled |= runner.cancel(runId);
        }
        return cancelled;
    }
}
