package com.changhong.onlinecode.service.agent;

/**
 * Agent 执行结果。
 *
 * @param runId         本次执行对应 Run id；agent 缺失等未启动场景可为 null
 * @param output        Agent 输出
 * @param succeeded     CLI 调用是否成功完成
 * @param failureReason 失败原因
 */
public record AgentExecutionResult(String runId, String output, boolean succeeded, String failureReason) {

    public static AgentExecutionResult failed(String runId, String failureReason) {
        return new AgentExecutionResult(runId, null, false, failureReason);
    }
}
