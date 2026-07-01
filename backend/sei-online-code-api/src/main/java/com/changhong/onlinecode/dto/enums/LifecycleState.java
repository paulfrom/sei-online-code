package com.changhong.onlinecode.dto.enums;

/**
 * 项目/迭代生命周期状态机。契约 §4（权威来源 CONTEXT.md）。
 *
 * <p>正常流转：
 * DRAFTING → SPEC_REFINING → SPEC_REVIEW → DISPATCHING → DEVELOPING
 * → MERGING → DEPLOYING → PREVIEW → ACCEPTED</p>
 *
 * <p>任一阶段可进入 FAILED；用户主动中止进入 CANCELLED。
 * PREVIEW 可回退到 SPEC_REFINING（用户优化）或前进到 ACCEPTED（验收）。</p>
 *
 * <p>Phase 1 串行单路径：DISPATCHING/DEVELOPING/MERGING 折叠为一次串行
 * ClaudeRunner 执行；这些状态仍保留在枚举中以向前兼容。</p>
 *
 * @author sei-online-code
 */
public enum LifecycleState {
    DRAFTING,
    SPEC_REFINING,
    SPEC_REVIEW,
    DISPATCHING,
    DEVELOPING,
    MERGING,
    DEPLOYING,
    PREVIEW,
    ACCEPTED,
    FAILED,
    CANCELLED
}
