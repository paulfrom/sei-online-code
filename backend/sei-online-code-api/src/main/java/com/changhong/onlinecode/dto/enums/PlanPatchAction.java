package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 计划补丁对任务执行的动作。
 */
@Schema(description = "计划补丁动作")
public enum PlanPatchAction {

    /** 复用原任务及其执行结果。 */
    KEEP,

    /** 以原任务成果为基础创建修订任务。 */
    AMEND,

    /** 创建新任务。 */
    ADD,

    /** 停止原任务后续调度并保留历史。 */
    SUPERSEDE,

    /** 保留实现，仅创建新的验证任务。 */
    REVALIDATE
}
