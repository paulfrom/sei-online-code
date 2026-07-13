package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * CodingTask 状态。
 *
 * @author sei-online-code
 */
@Schema(description = "编码任务状态")
public enum CodingTaskStatus {

    /** 待执行。 */
    PENDING,

    /** 执行中。 */
    RUNNING,

    /** 验证中。 */
    VALIDATING,

    /** 成功。 */
    SUCCEEDED,

    /** 失败。 */
    FAILED,

    /** 验证失败。 */
    VALIDATION_FAILED,

    /** 已取消。 */
    CANCELLED,

    /** 阻塞（依赖未满足或冲突）。 */
    BLOCKED,

    /** 已过期（详细设计版本升级或 loopId 变化）。 */
    STALE
}
