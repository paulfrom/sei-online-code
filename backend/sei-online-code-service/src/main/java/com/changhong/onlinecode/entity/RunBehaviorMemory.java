package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.BehaviorMemoryOutcome;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 记录某次 Run 实际注入了哪些行为记忆及后续效果。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "oc_run_behavior_memory",
        indexes = @Index(name = "idx_run_behavior_memory_run", columnList = "run_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_run_behavior_memory",
                columnNames = {"run_id", "behavior_memory_id"}))
@Access(AccessType.FIELD)
public class RunBehaviorMemory extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(name = "behavior_memory_id", nullable = false, length = 36)
    private String behaviorMemoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private BehaviorMemoryOutcome outcome = BehaviorMemoryOutcome.UNKNOWN;

    @Column(name = "evaluation_evidence_id", length = 36)
    private String evaluationEvidenceId;

    @Override
    @Transient
    public String getDisplay() {
        return runId + ":" + behaviorMemoryId + "[" + outcome + "]";
    }
}
