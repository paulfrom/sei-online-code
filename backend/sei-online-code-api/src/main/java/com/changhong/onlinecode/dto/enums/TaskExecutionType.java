package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * TaskExecution 业务类型。对应 oc_task_execution.task_type。
 */
@Schema(description = "TaskExecution 业务类型")
public enum TaskExecutionType {

    /** 编码任务执行。 */
    CODING_TASK,

    /** 验证类执行。 */
    VALIDATION,

    /** 交付类执行（MR 创建/更新）。 */
    DELIVERY
}
