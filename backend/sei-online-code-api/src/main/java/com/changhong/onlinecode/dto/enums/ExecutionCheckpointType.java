package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Checkpoint 类型。对应 oc_execution_checkpoint.checkpoint_type。ADR-001 §4。
 */
@Schema(description = "Checkpoint 类型")
public enum ExecutionCheckpointType {

    /** 执行级计划 checkpoint（可无 step）。 */
    PLAN,

    /** claim 步骤时写入。 */
    CLAIM,

    /** 过程进展。 */
    PROGRESS,

    /** 步骤 APPLIED。 */
    APPLIED,

    /** 步骤 VERIFIED。 */
    VERIFIED,

    /** 对账后补记。 */
    RECONCILED
}
