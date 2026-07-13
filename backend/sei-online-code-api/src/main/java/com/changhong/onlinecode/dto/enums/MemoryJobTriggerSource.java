package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * MemoryJob 触发来源。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.4、§12.1。
 *
 * @author sei-online-code
 */
@Schema(description = "记忆任务触发来源")
public enum MemoryJobTriggerSource {

    /** 项目工作区建设成功。 */
    PROJECT_WORKSPACE_READY,

    /** 用户手动触发。 */
    MANUAL,

    /** PRD 生成前自动准备。 */
    AUTO_BEFORE_PRD,

    /** 代码源变更。 */
    SOURCE_CHANGE,

    /** 项目自维护记忆变更。 */
    PROJECT_MEMORY_CHANGE,

    /** 记忆规则变更。 */
    RULE_CHANGE,

    /** 记忆规范版本变化。 */
    SPEC_CHANGE,

    /** CodingTask 执行成功。 */
    CODING_TASK_SUCCEEDED,

    /** 校验触发。 */
    VALIDATION,

    /** Requirement MR 交付成功。 */
    REQUIREMENT_DELIVERED
}
