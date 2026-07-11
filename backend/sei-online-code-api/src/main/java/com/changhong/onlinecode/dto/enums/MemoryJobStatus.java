package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * MemoryJob 状态。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.4。
 *
 * @author sei-online-code
 */
@Schema(description = "记忆任务状态")
public enum MemoryJobStatus {

    /** 已投递待执行。 */
    PENDING,

    /** 执行中。 */
    RUNNING,

    /** 执行成功。 */
    SUCCEEDED,

    /** 执行失败。 */
    FAILED,

    /** 已取消。 */
    CANCELLED
}