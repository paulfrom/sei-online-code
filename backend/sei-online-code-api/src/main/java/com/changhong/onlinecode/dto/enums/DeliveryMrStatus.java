package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** GitLab merge request lifecycle, independent from automation execution state. */
@Schema(description = "GitLab MR 状态")
public enum DeliveryMrStatus {
    NOT_SUBMITTED,
    OPEN,
    MERGED,
    CLOSED
}
