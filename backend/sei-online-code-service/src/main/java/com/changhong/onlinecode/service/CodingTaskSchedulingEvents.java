package com.changhong.onlinecode.service;

/** CodingTask 调度边界事件。 */
public final class CodingTaskSchedulingEvents {

    private CodingTaskSchedulingEvents() {
    }

    /** 请求重新评估指定需求下可运行的任务。 */
    public record ScheduleRequested(String requirementId) {
    }

    /** 调度器管理的开发 Run 已结束。 */
    public record DevelopmentFinished(String codingTaskId, boolean success, String failureReason) {
    }
}
