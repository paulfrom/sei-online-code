package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Run 记录类型。
 */
@Schema(description = "Run 记录类型")
public enum RunType {

    /** 一次真实 Agent CLI 调用尝试。 */
    AGENT,

    /** 非 Agent 的系统动作记录，例如交付 MR。 */
    SYSTEM
}
