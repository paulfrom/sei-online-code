package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WorkspaceMemory 新鲜度。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.2、§12.1。
 *
 * <p>标识当前版本相对最新输入是否过期，用于 PRD 生成前判断是否需要刷新记忆。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "工作区记忆新鲜度")
public enum WorkspaceMemoryFreshness {

    /** 新鲜，输入未变化。 */
    FRESH,

    /** 代码源变更导致过期。 */
    STALE_BY_SOURCE_CHANGE,

    /** memorySpecVersion 变更导致过期。 */
    STALE_BY_SPEC_CHANGE,

    /** agent-memory/memory-rules.md 变更导致过期。 */
    STALE_BY_RULE_CHANGE,

    /** project-memory 等自维护记忆变更导致过期。 */
    STALE_BY_PROJECT_MEMORY_CHANGE,

    /** 检测到 platform-memory 被手动修改。 */
    PLATFORM_MEMORY_DRIFT
}