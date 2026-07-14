package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dto.enums.ExecutionPlanType;

/** 事务提交后异步执行 PM 规划的事件。 */
public record RequirementAutomationLoopEvent(String requirementId,
                                             String loopId,
                                             ExecutionPlanType planType,
                                             String summary) {
}
