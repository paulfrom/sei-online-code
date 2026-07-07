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

    /** PRD 生成失败。 */
    FAILED
}
