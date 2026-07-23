package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Requirement / PRD 生命周期状态。
 *
 * @author sei-online-code
 */
@Schema(description = "需求 PRD 状态")
public enum RequirementStatus {

    /** PRD 生成中。 */
    PRD_GENERATING,

    /** PRD 生成完成，待审阅/编辑/确认。 */
    PRD_REVIEW,

    /** PRD 已确认，下游概览设计已生成或生成中；PRD 冻结。 */
    PRD_CONFIRMED,

    /** 当前 Loop 的 MR 已合并，等待用户反馈或确认整个需求完成。 */
    WAITING_FEEDBACK,

    /** 用户已确认整个需求完成；继续评论前必须重新打开。 */
    COMPLETED,

    /** PRD 生成失败。 */
    FAILED
}
