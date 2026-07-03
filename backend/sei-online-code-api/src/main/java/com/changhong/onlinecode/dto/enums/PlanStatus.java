package com.changhong.onlinecode.dto.enums;

/**
 * Plan 状态。
 *
 * <p>GENERATING（生成中）→ DRAFT（草稿）→ CONFIRMED（已确认）；生成失败进入 FAILED。</p>
 *
 * @author sei-online-code
 */
public enum PlanStatus {
    GENERATING,
    DRAFT,
    CONFIRMED,
    FAILED
}
