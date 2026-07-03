package com.changhong.onlinecode.dto.enums;

/**
 * FeatureDesign 状态。
 *
 * <p>PENDING（待处理）→ GENERATING（生成中）→ DRAFT（草稿）→ CONFIRMED（已确认）；
 * 长时间未变更进入 STALE；生成失败进入 FAILED。</p>
 *
 * @author sei-online-code
 */
public enum FeatureDesignStatus {
    PENDING,
    GENERATING,
    DRAFT,
    CONFIRMED,
    STALE,
    FAILED
}
