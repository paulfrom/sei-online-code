package com.changhong.onlinecode.agent;

import java.util.concurrent.CompletableFuture;

/**
 * CLI 工具执行器抽象（多 vendor）。参考 multica {@code server/pkg/agent/agent.go}
 * 的 backend 接口——每个 protocol_family（claude/codex/...）一个实现，由
 * {@link CliRunnerRegistry} 按 {@link Agent#getCliTool()} 选择。
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
     * 异步执行一次 CLI 运行，流式回传日志。
     *
     * @param iterationId 迭代 id（用于日志帧路由）
     * @param prompt      提示词
     * @param cwd         工作目录（可为 null，表示继承当前目录）
     * @param model       模型名（可为 null/blank，表示用 CLI 默认模型；非空时 runner 注入 {@code --model}/{@code -m}）
     * @return 完成后携带聚合结果文本的 future
     */
    CompletableFuture<String> execute(String iterationId, String prompt, String cwd, String model);

    /**
     * 异步执行一次 CLI 运行，带 taskId/runId（并行 fan-out 场景）。
     *
     * @param iterationId 迭代 id
     * @param taskId      任务 id（日志帧路由）
     * @param runId       运行 id（日志帧路由）
     * @param prompt      提示词
     * @param cwd         工作目录（可为 null）
     * @param model       模型名（可为 null/blank，表示用 CLI 默认模型；非空时 runner 注入 {@code --model}/{@code -m}）
     * @return 完成后携带聚合结果文本的 future
     */
    CompletableFuture<String> execute(String iterationId, String taskId, String runId,
                                      String prompt, String cwd, String model);
}
