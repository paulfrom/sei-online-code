package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.RequirementWorkspaceState;
import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * Requirement 工作区实体。契约 ADR-001 §3 / 数据模型 §3 —— 每个项目需求唯一的 worktree、feature branch 与写 owner。
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_requirement_workspace", indexes = {
        @Index(name = "idx_req_ws_owner_run", columnList = "owner_run_id"),
        @Index(name = "idx_req_ws_state_retention", columnList = "state,retention_until")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class RequirementWorkspace extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;

    @Column(name = "requirement_id", length = 36, nullable = false)
    private String requirementId;

    @Column(name = "workspace_path", length = 500, nullable = false)
    private String workspacePath;

    @Column(name = "branch_name", length = 200, nullable = false)
    private String branchName;

    @Column(name = "base_commit", length = 64, nullable = false)
    private String baseCommit;

    @Column(name = "current_head", length = 64, nullable = false)
    private String currentHead;

    @Column(name = "active_loop_id", length = 64)
    private String activeLoopId;

    @Column(name = "owner_run_id", length = 36)
    private String ownerRunId;

    @Column(name = "owner_execution_id", length = 36)
    private String ownerExecutionId;

    @Column(name = "lease_expires_at")
    private Date leaseExpiresAt;

    /** 接管时递增；旧 token 的写操作必须被拒绝。 */
    @Column(name = "fencing_token", nullable = false)
    private Long fencingToken = 0L;

    /** 任一可观测进度提交时递增，前端据此刷新权威快照。 */
    @Column(name = "snapshot_version", nullable = false)
    private Long snapshotVersion = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private RequirementWorkspaceState state;

    @Column(name = "last_progress_at")
    private Date lastProgressAt;

    @Column(name = "retention_until")
    private Date retentionUntil;

    @Override
    @Transient
    public String getDisplay() {
        return branchName + " @ " + currentHead + " [" + state + "]";
    }
}
