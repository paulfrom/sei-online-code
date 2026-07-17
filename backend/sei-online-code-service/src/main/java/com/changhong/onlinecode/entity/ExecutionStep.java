package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.ExecutionPhase;
import com.changhong.onlinecode.dto.enums.ExecutionStepStatus;
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
 * ExecutionStep 实体。契约 ADR-001 §2 / 数据模型 §6 —— Execution 当前态账本。
 *
 * <p>状态迁移仅由服务端命令控制；VERIFIED 不可原地回退，只能在计划变更时生成新版本或标 SUPERSEDED。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_execution_step", indexes = {
        @Index(name = "idx_step_exec_planver_status", columnList = "execution_id,plan_version,status"),
        @Index(name = "idx_step_owner_lease", columnList = "owner_run_id,lease_expires_at")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionStep extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "execution_id", length = 36, nullable = false)
    private String executionId;

    /** 语义稳定、不含 Run ID；同 Execution 计划版本内唯一。 */
    @Column(name = "step_key", length = 160, nullable = false)
    private String stepKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 32)
    private ExecutionPhase phase;

    @Column(name = "plan_version", nullable = false)
    private Integer planVersion;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "input_hash", length = 64, nullable = false)
    private String inputHash;

    @Column(name = "required_step", nullable = false)
    private Boolean requiredStep = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExecutionStepStatus status;

    @Column(name = "owner_run_id", length = 36)
    private String ownerRunId;

    /** 每次 claim 重新生成，写 checkpoint/effect 时必须携带并重新校验。 */
    @Column(name = "claim_token", length = 64)
    private String claimToken;

    @Column(name = "workspace_fencing_token")
    private Long workspaceFencingToken;

    @Column(name = "lease_expires_at")
    private Date leaseExpiresAt;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    /** 仅展示用。 */
    @Column(name = "progress_percent")
    private Integer progressPercent;

    @Column(name = "latest_checkpoint_id", length = 36)
    private String latestCheckpointId;

    @Column(name = "checkpoint_data", columnDefinition = "TEXT")
    private String checkpointData;

    @Column(name = "evidence_data", columnDefinition = "TEXT")
    private String evidenceData;

    @Column(name = "last_heartbeat_at")
    private Date lastHeartbeatAt;

    @Column(name = "started_at")
    private Date startedAt;

    @Column(name = "applied_at")
    private Date appliedAt;

    @Column(name = "completed_at")
    private Date completedAt;

    /** CAS/乐观锁版本。 */
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Override
    @Transient
    public String getDisplay() {
        return phase + "/" + stepKey + " [" + status + "]";
    }
}
