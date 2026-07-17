package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.ExecutionCheckpointType;
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

/**
 * ExecutionCheckpoint 实体。契约 ADR-001 §4 / 数据模型 §7 —— 不可变进度 journal，只允许 INSERT。
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_execution_checkpoint", indexes = {
        @Index(name = "idx_checkpoint_step_seq", columnList = "step_id,sequence_no"),
        @Index(name = "idx_checkpoint_run_seq", columnList = "run_id,sequence_no"),
        @Index(name = "idx_checkpoint_git_head", columnList = "git_head")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionCheckpoint extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "execution_id", length = 36, nullable = false)
    private String executionId;

    /** 可空：PLAN 等执行级 checkpoint 可无 step。 */
    @Column(name = "step_id", length = 36)
    private String stepId;

    @Column(name = "run_id", length = 36, nullable = false)
    private String runId;

    /** Execution 内单调递增。 */
    @Column(name = "sequence_no", nullable = false)
    private Long sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkpoint_type", nullable = false, length = 32)
    private ExecutionCheckpointType checkpointType;

    @Column(name = "claim_token", length = 64)
    private String claimToken;

    @Column(name = "workspace_fencing_token", nullable = false)
    private Long workspaceFencingToken;

    /** 版本化 JSON。 */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "evidence_digest", length = 64)
    private String evidenceDigest;

    @Column(name = "git_head", length = 64)
    private String gitHead;

    @Column(name = "parent_git_head", length = 64)
    private String parentGitHead;

    @Override
    @Transient
    public String getDisplay() {
        return checkpointType + " #" + sequenceNo;
    }
}
