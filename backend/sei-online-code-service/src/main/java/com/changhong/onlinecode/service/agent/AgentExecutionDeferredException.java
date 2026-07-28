package com.changhong.onlinecode.service.agent;

/**
 * Agent 因调度资源繁忙而未执行。调用方应恢复业务任务为 PENDING，不得记为失败。
 */
public class AgentExecutionDeferredException extends RuntimeException {

    private final String runId;

    public AgentExecutionDeferredException(String runId, String message) {
        super(message);
        this.runId = runId;
    }

    public String getRunId() {
        return runId;
    }
}
