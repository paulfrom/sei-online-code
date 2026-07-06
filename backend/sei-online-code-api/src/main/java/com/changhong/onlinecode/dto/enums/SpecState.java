package com.changhong.onlinecode.dto.enums;

/**
 * Spec 状态。契约 §2.2 SpecDto.state。
 *
 * <p>GENERATING（智能体生成中）→ SPEC_REVIEW（评审中）→ CONFIRMED（已确认）；
 * 生成失败进入 FAILED。对齐 {@link PlanStatus} 的状态控制语义。</p>
 *
 * @author sei-online-code
 */
public enum SpecState {
    GENERATING,
    DRAFT,
    SPEC_REVIEW,
    CONFIRMED,
    FAILED
}
