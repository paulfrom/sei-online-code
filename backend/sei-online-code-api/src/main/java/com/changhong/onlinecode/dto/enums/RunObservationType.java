package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Run observation 类型。对应 oc_run_observation.observation_type。ADR-001 §4。
 *
 * <p>observation 只追加，更正通过 supersedes_observation_id 指向旧记录。</p>
 */
@Schema(description = "Run observation 类型")
public enum RunObservationType {

    /** 调度派发。 */
    DISPATCH,

    /** Agent 已接受。 */
    ACCEPTED,

    /** 过程进展。 */
    PROGRESS,

    /** checkpoint 写入。 */
    CHECKPOINT,

    /** 终态。 */
    TERMINAL,

    /** 对账。 */
    RECONCILIATION,

    /** 人工审核追加。 */
    MANUAL_REVIEW
}
