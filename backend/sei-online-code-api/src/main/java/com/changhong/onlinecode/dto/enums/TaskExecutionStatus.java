package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * TaskExecution 状态。对应 oc_task_execution.status。
 *
 * <p>ADR-001 §6：Execution 完成需当前计划版本 required 步骤全部 VERIFIED 且必要 effect 全部 CONFIRMED。</p>
 */
@Schema(description = "TaskExecution 状态")
public enum TaskExecutionStatus {

    /** 已创建，尚未开始处理。 */
    PENDING,

    /** 正在处理，存在活跃 Run。 */
    ACTIVE,

    /** 收口中，等待最终验证与 settlement。 */
    COMPLETING,

    /** 成功完成。 */
    SUCCEEDED,

    /** 失败。 */
    FAILED,

    /** 阻塞，等待对账或人工。 */
    BLOCKED
}
