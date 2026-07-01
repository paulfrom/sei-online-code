package com.changhong.onlinecode.dto.enums;

/**
 * Task 状态。契约 Phase 2 §1.1 / §4 task-level 状态机。
 *
 * <p>PENDING → RUNNING → MERGING → MERGED；任一态可进入 FAILED；用户中止进入 CANCELLED。</p>
 *
 * @author sei-online-code
 */
public enum TaskState {
    PENDING,
    RUNNING,
    MERGING,
    MERGED,
    FAILED,
    CANCELLED
}
