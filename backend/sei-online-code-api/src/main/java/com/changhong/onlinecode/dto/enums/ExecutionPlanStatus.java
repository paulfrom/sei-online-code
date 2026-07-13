package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 执行计划状态。
 */
@Schema(description = "执行计划状态")
public enum ExecutionPlanStatus {
    PLANNING,
    READY,
    DEVELOPING,
    ACCEPTING,
    NEEDS_REMEDIATION,
    ACCEPTED,
    INTERRUPTED,
    FAILED
}
