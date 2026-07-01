package com.changhong.onlinecode.dto.enums;

/**
 * Run 状态。契约 Phase 2 §1.2 / §4 run-level 状态机。
 *
 * <p>RUNNING → SUCCEEDED；任一态可进入 FAILED；用户中止进入 CANCELLED。</p>
 *
 * @author sei-online-code
 */
public enum RunState {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
