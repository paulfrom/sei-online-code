package com.changhong.onlinecode.service.agent;

/**
 * Agent 执行结果。
 *
 * <p>{@code deferred} 表示工作区租约繁忙（方案 §6.2）：任务因调度延迟而未真正执行，
 * 不得记为失败、不得消耗重试次数。调用方收到 {@code deferred=true} 的结果时，
 * 应保持任务 {@code PENDING} 并在下一轮调度重试。</p>
 *
 * @param runId         本次执行对应 Run id；agent 缺失/租约繁忙等未启动场景可为 null
 * @param output        Agent 输出
 * @param succeeded     CLI 调用是否成功完成
 * @param failureReason 失败原因
 * @param deferred      工作区租约繁忙，本次执行被推迟（非失败）
 */
public record AgentExecutionResult(String runId, String output, boolean succeeded,
                                   String failureReason, boolean deferred) {

    /** 兼容既有 4 参数构造调用：默认 deferred=false。 */
    public AgentExecutionResult(String runId, String output, boolean succeeded, String failureReason) {
        this(runId, output, succeeded, failureReason, false);
    }

    public static AgentExecutionResult failed(String runId, String failureReason) {
        return new AgentExecutionResult(runId, null, false, failureReason);
    }

    public static AgentExecutionResult failed(String runId, String output, String failureReason) {
        return new AgentExecutionResult(runId, output, false, failureReason);
    }

    /**
     * 工作区租约繁忙：未取得租约，本次执行被推迟（方案 §6.2）。
     * 不创建失败 Run，不写失败摘要，不增加 retryCount。
     */
    public static AgentExecutionResult deferred(String runId, String reason) {
        return new AgentExecutionResult(runId, null, false, reason, true);
    }
}
