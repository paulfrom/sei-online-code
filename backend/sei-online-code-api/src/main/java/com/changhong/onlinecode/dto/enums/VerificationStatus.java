package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 验证状态。被 oc_run.verification_status 与 oc_run_observation.verification_status 复用。
 */
@Schema(description = "验证状态")
public enum VerificationStatus {

    /** 尚未验证。 */
    UNVERIFIED,

    /** 已确认（证据满足）。 */
    CONFIRMED,

    /** 证据不足，结论不确定。 */
    INCONCLUSIVE,

    /** 证据相互矛盾。 */
    CONTRADICTED
}
