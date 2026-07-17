package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Requirement 工作区状态。对应 oc_requirement_workspace.state。
 *
 * <p>ADR-001 §3：同一 Requirement 只有一个 workspace 和一个写 owner；终态后按配置保留。</p>
 */
@Schema(description = "Requirement 工作区状态")
public enum RequirementWorkspaceState {

    /** 活跃，可被有效 lease 写入。 */
    ACTIVE,

    /** 阻塞，存在未解决 UNKNOWN/BLOCKED，禁止 GC 与接管。 */
    BLOCKED,

    /** 交付中，正在整合并提交 MR。 */
    DELIVERING,

    /** 终态完成（MR 已提交，未必已合入）。 */
    COMPLETED
}
