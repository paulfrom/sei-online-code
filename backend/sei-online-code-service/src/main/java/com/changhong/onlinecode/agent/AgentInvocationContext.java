package com.changhong.onlinecode.agent;

/**
 * Agent CLI 调用上下文。
 *
 * <p>约束：</p>
 * <ul>
 *   <li>runId 必填且对应已提交的 Agent Run；</li>
 *   <li>agentId、agentName、cliTool、model 必须与 Run 快照一致；</li>
 *   <li>cliTool 使用 Registry 解析后的实际 Runner 名称；</li>
 *   <li>未知非空 CLI 工具名直接失败；null 或 blank 仍按兼容规则解析为默认工具。</li>
 * </ul>
 *
 * @author sei-online-code
 */
public record AgentInvocationContext(
        String runId,
        String logStreamKey,
        String taskId,
        String agentId,
        String agentName,
        String cliTool,
        String model) {
}
