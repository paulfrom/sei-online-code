package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Requirement 自动化状态。
 */
@Schema(description = "Requirement 自动化状态")
public enum RequirementAutomationStatus {
    IDLE,
    PLANNING,
    DEVELOPING,
    VALIDATING,
    ACCEPTING,
    DELIVERING,
    INTERRUPTED,
    WAITING_HUMAN,
    COMPLETED,
    FAILED
}
