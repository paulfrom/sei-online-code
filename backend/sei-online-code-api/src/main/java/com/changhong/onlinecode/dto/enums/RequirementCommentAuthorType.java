package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Requirement 评论作者类型。
 */
@Schema(description = "Requirement 评论作者类型")
public enum RequirementCommentAuthorType {
    HUMAN,
    PM_AGENT,
    FRONTEND_AGENT,
    BACKEND_AGENT,
    TEST_AGENT,
    SYSTEM
}
