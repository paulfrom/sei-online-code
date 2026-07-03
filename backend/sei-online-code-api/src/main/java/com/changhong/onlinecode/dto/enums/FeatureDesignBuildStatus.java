package com.changhong.onlinecode.dto.enums;

/**
 * FeatureDesign 构建状态。
 *
 * <p>IDLE（空闲）→ BUILDING（构建中）→ BUILT（已构建）；
 * 构建失败进入 BUILD_FAILED；长时间未重新构建进入 STALE。</p>
 *
 * @author sei-online-code
 */
public enum FeatureDesignBuildStatus {
    IDLE,
    BUILDING,
    BUILT,
    BUILD_FAILED,
    STALE
}
