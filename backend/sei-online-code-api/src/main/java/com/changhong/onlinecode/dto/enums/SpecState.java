package com.changhong.onlinecode.dto.enums;

/**
 * Spec 状态。契约 §2.2 SpecDto.state。
 *
 * <p>DRAFT（草稿）→ SPEC_REVIEW（评审中）→ CONFIRMED（已确认）。</p>
 *
 * @author sei-online-code
 */
public enum SpecState {
    DRAFT,
    SPEC_REVIEW,
    CONFIRMED
}
