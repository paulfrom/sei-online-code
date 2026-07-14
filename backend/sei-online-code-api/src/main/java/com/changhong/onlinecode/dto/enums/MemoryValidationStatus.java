package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档记忆校验状态。用于 PRD/概述设计/详细设计。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §9.4、§15.4、§19。
 *
 * <p>{@link #NOT_RUN} 异步审阅尚未执行；{@link #PASSED} 暂无差异提醒；
 * {@link #WARNING} 有差异提醒。{@link #FAILED} 仅为历史数据兼容保留，不作为确认门禁。</p>
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

    /** 历史校验失败状态，仅为数据兼容保留，不阻止确认。 */
    FAILED
}
