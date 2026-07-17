package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 平台受控副作用类型。对应 oc_execution_effect.effect_type。ADR-001 §5。
 */
@Schema(description = "Effect 类型")
public enum ExecutionEffectType {

    /** 评论。 */
    COMMENT,

    /** 事件发布。 */
    EVENT,

    /** 记忆任务。 */
    MEMORY_JOB,

    /** Git push。 */
    PUSH,

    /** MR 创建/更新。 */
    MR,

    /** 外部调用。 */
    EXTERNAL_CALL
}
