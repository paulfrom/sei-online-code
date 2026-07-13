package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 执行计划类型。
 */
@Schema(description = "执行计划类型")
public enum ExecutionPlanType {
    INITIAL,
    REMEDIATION,
    CHANGE_REQUEST
}
