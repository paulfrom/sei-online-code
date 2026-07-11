package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * seed 记忆模板来源类型。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §6.1.0、§8.1。
 *
 * <p>{@link #BUILTIN} 平台内置（首次启动或 DB 缺失时从 classpath bootstrap）；
 * {@link #USER_CONFIG} 平台用户配置产出。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "seed 记忆模板来源类型")
public enum MemorySeedTemplateSourceType {

    /** 平台内置，classpath bootstrap。 */
    BUILTIN,

    /** 平台用户配置。 */
    USER_CONFIG
}