package com.changhong.onlinecode.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 将执行侧发布的任务状态事件转交给调度器，保持执行服务与调度器单向依赖。 */
@Component
public class CodingTaskSchedulingEventListener {

    private final CodingTaskScheduler scheduler;

    public CodingTaskSchedulingEventListener(CodingTaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @EventListener
    public void onScheduleRequested(CodingTaskSchedulingEvents.ScheduleRequested event) {
        scheduler.schedule(event.requirementId());
    }

    @EventListener
    public void onDevelopmentFinished(CodingTaskSchedulingEvents.DevelopmentFinished event) {
        scheduler.onDevelopmentRunFinished(event.codingTaskId(), event.success(), event.failureReason());
    }
}
