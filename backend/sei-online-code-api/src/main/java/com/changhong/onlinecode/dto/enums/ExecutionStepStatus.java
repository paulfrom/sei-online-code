package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ExecutionStep 状态。对应 oc_execution_step.status。ADR-001 §2。
 *
 * <p>状态迁移由服务端命令控制（数据模型 §6），VERIFIED 不可原地回退。</p>
 */
@Schema(description = "ExecutionStep 状态")
public enum ExecutionStepStatus {

    /** 待处理。 */
    PENDING,

    /** 进行中（已 claim）。 */
    IN_PROGRESS,

    /** 动作已产生效果，尚未验证。 */
    APPLIED,

    /** 已验证，后续 Run 永久跳过。 */
    VERIFIED,

    /** 结果不确定，必须先对账，不得直接重做。 */
    UNKNOWN,

    /** 阻塞。 */
    BLOCKED,

    /** 失败。 */
    FAILED,

    /** 被新版本取代。 */
    SUPERSEDED
}
