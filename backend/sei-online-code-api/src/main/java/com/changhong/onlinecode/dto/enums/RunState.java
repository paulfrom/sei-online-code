package com.changhong.onlinecode.dto.enums;

/**
 * Run 状态。契约 Phase 2 §1.2 / §4 run-level 状态机；ADR-001 扩展 at-least-once 续作语义。
 *
 * <p>QUEUED → RUNNING → SUCCEEDED；失败和超时进入 FAILED（超时由 terminalReason=TIMEOUT 区分）；
 * 用户中止进入 CANCELLED。UNKNOWN 仅保留给无法确认结果的兼容场景，不表示 Run 仍在执行。</p>
 *
 * @author sei-online-code
 */
public enum RunState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    UNKNOWN
}
