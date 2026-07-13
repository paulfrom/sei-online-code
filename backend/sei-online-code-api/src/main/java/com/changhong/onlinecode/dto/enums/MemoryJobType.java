package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * MemoryJob 类型。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.4、§12。
 *
 * @author sei-online-code
 */
@Schema(description = "记忆任务类型")
public enum MemoryJobType {

    /** 初始化工作区记忆。 */
    MEMORY_INITIALIZE,

    /** 全量重建工作区记忆。 */
    MEMORY_REBUILD,

    /** 代码源变更触发的增量刷新。 */
    MEMORY_REFRESH_BY_SOURCE_CHANGE,

    /** project-memory 变更触发的刷新。 */
    MEMORY_REFRESH_BY_PROJECT_MEMORY_CHANGE,

    /** memory-rules 变更触发的刷新。 */
    MEMORY_REFRESH_BY_RULE_CHANGE,

    /** memorySpecVersion 变更触发的刷新。 */
    MEMORY_REFRESH_BY_SPEC_CHANGE,

    /** CodingTask 成功后的增量回写。 */
    MEMORY_UPDATE_AFTER_CODING_TASK,

    /** 记忆校验任务。 */
    MEMORY_VALIDATE,

    /** Requirement 交付成功后的长期记忆回写。 */
    MEMORY_UPDATE_AFTER_REQUIREMENT_DELIVERY
}
