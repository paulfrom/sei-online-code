package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 平台固定顶层阶段。对应 oc_execution_step.phase。ADR-001 §2。
 */
@Schema(description = "平台固定阶段")
public enum ExecutionPhase {

    /** 需求发现。 */
    DISCOVER,

    /** 计划。 */
    PLAN,

    /** 实现。 */
    IMPLEMENT,

    /** 验证。 */
    VERIFY,

    /** 交付。 */
    DELIVER
}
