package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * BLOCKED step 的恢复策略，存放于 ExecutionStep.evidenceData JSON 中。
 */
@Schema(description = "BLOCKED step 恢复策略")
public enum BlockedRecoveryPolicy {

    /** 到 retryAfter 后自动解除阻塞，回到 PENDING，等待新 Run claim。 */
    AUTO_RETRY,

    /** 暂时等待，不自动解除；到期后仍需重新对账。 */
    WAIT,

    /** 需要人工 observation 或受控操作解除。 */
    MANUAL_REVIEW,

    /** 需要创建单独 remediation step，不能原 step 直接重试。 */
    REMEDIATION_STEP
}
