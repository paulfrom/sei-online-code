package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 平台统一 seed 记忆模板状态。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §6.1.0、§8.1。
 *
 * <p>{@link #DRAFT} 草稿不可被项目选择；{@link #ACTIVE} 已发布，可供项目显式选择；
 * {@link #ARCHIVED} 已归档，不可供新项目选择，但已绑定该版本的项目仍可沿用其 seed 补齐缺失文件。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "seed 记忆模板状态")
public enum MemorySeedTemplateStatus {

    /** 草稿，不可被项目选择。 */
    DRAFT,

    /** 已发布，可供项目选择。 */
    ACTIVE,

    /** 已归档，不可供新项目选择，已绑定版本仍可沿用。 */
    ARCHIVED
}