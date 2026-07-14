package com.changhong.onlinecode.service;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 自动化领域事件适配器；耗时的 PM 规划异步执行，调度完成判断保持同步事务语义。 */
@Component
public class RequirementAutomationLoopListener {

    private final RequirementAutomationService automationService;

    public RequirementAutomationLoopListener(RequirementAutomationService automationService) {
        this.automationService = automationService;
    }

    @Async
    @EventListener
    public void onLoopPrepared(RequirementAutomationLoopEvent event) {
        automationService.executePreparedLoop(event.requirementId(), event.loopId(),
                event.planType(), event.summary());
    }

    @EventListener
    public void onSchedulingPassCompleted(CodingTaskScheduler.SchedulingPassCompletedEvent event) {
        automationService.onPlanTasksSettled(event.requirementId());
    }
}
