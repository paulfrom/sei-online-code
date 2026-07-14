package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Agent CLI 调用的 token usage 可用性状态。
 */
@Schema(description = "Token usage 可用性状态")
public enum UsageStatus {

    /** 没有取得任何可信的 token 数值。 */
    UNAVAILABLE,

    /** 只取得中间累计值，或最终 usage 中只有部分核心字段可用。 */
    PARTIAL,

    /** 已经取得 Provider 对本次调用给出的最终 usage。 */
    COMPLETE
}
