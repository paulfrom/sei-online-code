package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OverviewDesign 状态。
 *
 * @author sei-online-code
 */
@Schema(description = "概览设计状态")
public enum OverviewDesignStatus {

    /** 生成中。 */
    GENERATING,

    /** 草稿，待确认。 */
    DRAFT,

    /** 已确认，已拆分为详细设计。 */
    CONFIRMED,

    /** 生成失败。 */
    FAILED
}
