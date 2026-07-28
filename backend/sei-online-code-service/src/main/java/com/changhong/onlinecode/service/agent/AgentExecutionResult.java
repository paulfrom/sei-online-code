package com.changhong.onlinecode.service.agent;

import java.util.Objects;

/**
 * Agent 执行结果。
 *
 * <p>{@link Status#DEFERRED} 表示工作区租约或执行器繁忙：任务因调度延迟而未真正执行，
 * 不得记为失败、不得消耗重试次数。</p>
 *
 * @param runId         本次执行对应 Run id；agent 缺失/租约繁忙等未启动场景可为 null
 * @param output        Agent 输出
 * @param status        执行状态
 * @param failureReason 失败原因
 */
public record AgentExecutionResult(String runId, String output, Status status, String failureReason) {

    public AgentExecutionResult {
        Objects.requireNonNull(status, "status is required");
    }

    public enum Status {
        SUCCEEDED,
        FAILED,
        DEFERRED
    }

    public static AgentExecutionResult succeeded(String runId, String output) {
        return new AgentExecutionResult(runId, output, Status.SUCCEEDED, null);
    }

    public static AgentExecutionResult failed(String runId, String failureReason) {
        return new AgentExecutionResult(runId, null, Status.FAILED, failureReason);
    }

    public static AgentExecutionResult failed(String runId, String output, String failureReason) {
        return new AgentExecutionResult(runId, output, Status.FAILED, failureReason);
    }

    /**
     * 工作区租约繁忙：未取得租约，本次执行被推迟（方案 §6.2）。
     * 不创建失败 Run，不写失败摘要，不增加 retryCount。
     */
    public static AgentExecutionResult deferred(String runId, String reason) {
        return new AgentExecutionResult(runId, null, Status.DEFERRED, reason);
    }
}
