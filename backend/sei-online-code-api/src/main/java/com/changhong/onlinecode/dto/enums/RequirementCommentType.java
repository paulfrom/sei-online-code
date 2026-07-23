package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Requirement 评论类型。
 */
@Schema(description = "Requirement 评论类型")
public enum RequirementCommentType {
    HUMAN_FEEDBACK,
    EXECUTION_PLAN,
    DEV_RESULT,
    ACCEPTANCE,
    REMEDIATION,
    INTERRUPTION,
    FAILURE,
    VALIDATION_RESULT,
    MR_CREATED,
    MR_UPDATED,
    MR_MERGED,
    MR_FAILED,
    WORKSPACE_SYNCED,
    WORKSPACE_SYNC_FAILED,
    REQUIREMENT_COMPLETED,
    REQUIREMENT_REOPENED,
    MEMORY_UPDATED,
    MEMORY_UPDATE_FAILED,
    CONTEXT_SUMMARY_FAILED
}
