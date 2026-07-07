package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DetailedDesign 状态。
 *
 * @author sei-online-code
 */
@Schema(description = "详细设计状态")
public enum DetailedDesignStatus {

    /** 生成中。 */
    GENERATING,

    /** 待审阅。 */
    REVIEW,

    /** 已确认，已创建 CodingTask。 */
    CONFIRMED,

    /** 生成失败。 */
    FAILED
}
