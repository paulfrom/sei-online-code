package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Run 终止原因。
 */
@Schema(description = "Run 终止原因")
public enum RunTerminalReason {

    /** 正常成功完成。 */
    SUCCEEDED,

    /** Agent 或系统动作执行失败。 */
    FAILED,

    /** 超过运行时间窗口后由补偿器收口。 */
    TIMEOUT,

    /** 用户或流程请求取消。 */
    CANCELLED,

    /** 被更新的一轮请求替代，结果不再采纳。 */
    SUPERSEDED
}
