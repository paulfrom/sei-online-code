package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * RequirementDesignContext 上下文状态。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.3、§14。
 *
 * <p>{@link #READY} 可用于生成；{@link #STALE} 需求/记忆/规范变化已失效，需重建；
 * {@link #FAILED} 生成失败。STALE 禁止确认。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "需求设计上下文状态")
public enum RequirementDesignContextStatus {

    /** 可用于生成。 */
    READY,

    /** 已失效，需重建上下文。 */
    STALE,

    /** 生成失败。 */
    FAILED
}