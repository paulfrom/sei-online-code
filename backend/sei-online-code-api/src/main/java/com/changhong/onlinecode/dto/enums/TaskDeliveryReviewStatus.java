package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务交付审阅状态。
 *
 * <p>与 CodingTask 执行状态分离，记录 pm-agent 对单次交付 Run 的审阅生命周期。</p>
 */
@Schema(description = "任务交付审阅状态")
public enum TaskDeliveryReviewStatus {
    /** 审阅待处理：交付已结算，等待 pm-agent 分析。 */
    PENDING,
    /** 审阅进行中：pm-agent 正在分析。 */
    REVIEWING,
    /** 已决策：PM 已返回 APPROVE/RETRY/REPLAN/WAIT_HUMAN 之一。 */
    DECIDED,
    /** 等待人工：PM 无法决策或达补救上限，转入人工。 */
    WAITING_HUMAN
}
