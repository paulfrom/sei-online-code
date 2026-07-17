package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * observation 来源类型。对应 oc_run_observation.source_type。
 */
@Schema(description = "observation 来源类型")
public enum ObservationSourceType {

    /** 系统/平台。 */
    SYSTEM,

    /** Agent。 */
    AGENT,

    /** 用户。 */
    USER,

    /** 对账器。 */
    RECONCILER
}
