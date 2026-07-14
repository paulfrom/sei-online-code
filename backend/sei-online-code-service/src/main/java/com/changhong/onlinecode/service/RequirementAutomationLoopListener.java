package com.changhong.onlinecode.service;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 在独立线程中执行耗时的 PM 规划，避免阻塞确认 PRD 或追加评论请求。 */
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
}
