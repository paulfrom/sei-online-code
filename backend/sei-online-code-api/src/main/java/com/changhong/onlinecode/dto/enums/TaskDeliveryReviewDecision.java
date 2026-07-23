package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PM 对单次任务交付的决策。
 */
@Schema(description = "任务交付审阅决策")
public enum TaskDeliveryReviewDecision {
    /** 批准交付：执行成功且交付证据完整。 */
    APPROVE,
    /** 重试原任务：仅限瞬态基础设施问题或可纠正的执行偏差。 */
    RETRY,
    /** 重新规划：代码缺陷、测试失败、上游交付不完整、任务契约错误。 */
    REPLAN,
    /** 等待人工：PM 输出无效、无法安全判断、达补救上限或需需求方决策。 */
    WAIT_HUMAN
}
