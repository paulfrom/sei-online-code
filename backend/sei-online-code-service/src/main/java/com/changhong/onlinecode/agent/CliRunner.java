package com.changhong.onlinecode.agent;

import java.util.concurrent.CompletableFuture;

/**
 * CLI 工具执行器抽象（多 vendor）。参考 multica {@code server/pkg/agent/agent.go}
 * 的 backend 接口——每个 protocol_family（claude/codex/...）一个实现，由
 * {@link CliRunnerRegistry} 按 {@code Agent#getCliTool()} 选择。
 *
 * <p>当前实现：{@link ClaudeRunner}（tool="claude"）、{@link CodexRunner}（tool="codex"）。
 * 未知/空 tool 默认走 claude（向后兼容）。</p>
 *
 * @author sei-online-code
 */
public interface CliRunner {

    /** 该 runner 对应的 CLI 工具名（与 {@code Agent.cliTool} 取值一致），如 "claude" / "codex"。 */
    String tool();

    /**
     * 异步执行一次 CLI 运行，返回完整结果（业务输出 + token usage）。
     *
     * @param logStreamKey 日志流键（用于日志帧路由）
     * @param taskId      任务 id（日志帧路由）
     * @param runId       运行 id（日志帧路由）
     * @param prompt      提示词
     * @param cwd         工作目录（可为 null，表示继承当前目录）
     * @param model       模型名（可为 null/blank，表示用 CLI 默认模型；非空时 runner 注入 {@code --model}/{@code -m}）
     * @param mcpConfig   MCP server 配置 JSON（Claude 风格 {@code {"mcpServers":{...}}}；null/blank = 不托管，
     *                    codex 经 {@code CODEX_HOME/config.toml} [mcp_servers.*] 托管块注入；claude 当前忽略，TODO）
     * @return 完成后携带 {@link CliRunResult} 的 future
     */
    CompletableFuture<CliRunResult> executeDetailed(String logStreamKey, String taskId, String runId,
                                                    String prompt, String cwd, String model, String mcpConfig);

    /** Best-effort cancellation for a process associated with the Run id. */
    default boolean cancel(String runId) {
        return false;
    }
}
