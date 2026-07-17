package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Effect 状态。对应 oc_execution_effect.status。ADR-001 §5。
 */
@Schema(description = "Effect 状态")
public enum ExecutionEffectStatus {

    /** 已准备，未执行。 */
    PREPARED,

    /** 已执行，尚未确认。 */
    APPLIED,

    /** 已确认（首次结果或查询结果）。 */
    CONFIRMED,

    /** 结果不确定，需对账。 */
    UNKNOWN,

    /** 失败。 */
    FAILED
}
