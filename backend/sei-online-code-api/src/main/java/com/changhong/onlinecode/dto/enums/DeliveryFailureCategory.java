package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PM 对任务交付失败原因的分类。
 *
 * <p>决定该失败是否允许自动补偿（见方案 §8）。</p>
 */
@Schema(description = "交付失败分类")
public enum DeliveryFailureCategory {
    /** 无失败：任务成功交付。 */
    NONE,
    /** 瞬态基础设施问题（工作区/进程/网络抖动），允许自动重试。 */
    TRANSIENT_INFRA,
    /** 交付不完整：agent 完成但未落地必要变更或证据。禁止自动重试。 */
    DELIVERY_INCOMPLETE,
    /** 验证失败：test-agent 判定 passed=false。禁止自动重试。 */
    VALIDATION_FAILED,
    /** 上游交付不完整：依赖任务的产出缺失或不兼容。禁止自动重试。 */
    UPSTREAM_INCOMPLETE,
    /** 计划缺陷：任务契约、依赖或分配存在错误。禁止自动重试。 */
    PLAN_DEFECT
}
