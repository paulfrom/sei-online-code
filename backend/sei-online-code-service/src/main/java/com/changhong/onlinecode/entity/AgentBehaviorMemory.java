package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.AgentBehaviorMemoryStatus;
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
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * PM 从多次证据中归纳出的、有作用域和效果反馈的 Agent 行为记忆。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "oc_agent_behavior_memory", indexes = {
        @Index(name = "idx_behavior_memory_lookup", columnList = "project_id,agent_name,area,status"),
        @Index(name = "idx_behavior_memory_scope", columnList = "scope_key,status")
})
@Access(AccessType.FIELD)
public class AgentBehaviorMemory extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "agent_name", length = 100)
    private String agentName;

    @Column(name = "area", length = 100)
    private String area;

    @Column(name = "scope_key", nullable = false, length = 200)
    private String scopeKey;

    @Column(name = "rule_text", nullable = false, columnDefinition = "TEXT")
    private String ruleText;

    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "source_review_id", length = 36)
    private String sourceReviewId;

    @Column(name = "source_evidence_id", length = 36)
    private String sourceEvidenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentBehaviorMemoryStatus status;

    @Column(name = "occurrence_count", nullable = false)
    private Integer occurrenceCount = 1;

    @Column(name = "helpful_count", nullable = false)
    private Integer helpfulCount = 0;

    @Column(name = "ineffective_count", nullable = false)
    private Integer ineffectiveCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_outcome", nullable = false, length = 32)
    private BehaviorMemoryOutcome lastOutcome = BehaviorMemoryOutcome.UNKNOWN;

    @Column(name = "last_applied_at")
    private Date lastAppliedAt;

    @Column(name = "expires_at")
    private Date expiresAt;

    @Override
    @Transient
    public String getDisplay() {
        return scopeKey + "[" + status + "]";
    }
}
