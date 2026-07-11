package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档记忆校验状态。用于 PRD/概述设计/详细设计。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §9.4、§15.4、§19。
 *
 * <p>{@link #NOT_RUN} 新生成尚未校验；{@link #PASSED} 校验通过；
 * {@link #WARNING} 有提示但允许确认；{@link #FAILED} 阻止确认。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "文档记忆校验状态")
public enum MemoryValidationStatus {

    /** 新生成尚未校验。 */
    NOT_RUN,

    /** 校验通过。 */
    PASSED,

    /** 有提示项，允许确认。 */
    WARNING,

    /** 校验失败，阻止确认。 */
    FAILED
}