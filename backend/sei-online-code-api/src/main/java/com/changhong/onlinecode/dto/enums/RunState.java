package com.changhong.onlinecode.dto.enums;

/**
 * Run 状态。契约 Phase 2 §1.2 / §4 run-level 状态机；ADR-001 扩展 at-least-once 续作语义。
 *
 * <p>QUEUED → RUNNING → SUCCEEDED；任一态可进入 FAILED；用户中止进入 CANCELLED；
 * 超时/回调丢失进入 UNKNOWN，必须先对账再决定是否续作（不得直接判失败）。</p>
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
