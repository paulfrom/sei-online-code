package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.TriggerSource;
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
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * Requirement 实体。需求的 PRD 聚合根。
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_requirement", indexes = {
        @Index(name = "idx_requirement_project", columnList = "project_id"),
        @Index(name = "idx_requirement_status", columnList = "status")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class Requirement extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "requirement_no", nullable = false, length = 32)
    private String requirementNo;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RequirementStatus status;

    @Column(name = "prd_version", nullable = false)
    private Integer prdVersion = 1;

    @Column(name = "prd_content", columnDefinition = "TEXT")
    private String prdContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 64)
    private FailureCode failureCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage", length = 32)
    private FailureStage failureStage;

    @Column(name = "failure_summary", columnDefinition = "TEXT")
    private String failureSummary;

    @Column(name = "failure_detail", columnDefinition = "TEXT")
    private String failureDetail;

    @Column(name = "last_failed_at")
    private Date lastFailedAt;

    @Column(name = "last_retry_at")
    private Date lastRetryAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private Date nextRetryAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_trigger_source", length = 32)
    private TriggerSource lastTriggerSource;

    @Column(name = "generation_token", length = 64)
    private String generationToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "automation_status", nullable = false, length = 32)
    private RequirementAutomationStatus automationStatus = RequirementAutomationStatus.IDLE;

    /** 当前交付周期 ID，用于隔离其他 loop 的过期 agent 回调。 */
    @Column(name = "active_loop_id", length = 64)
    private String activeLoopId;

    /** 当前 loop 内最新收到的计划修订序号。 */
    @Column(name = "revision_seq", nullable = false)
    private Long revisionSeq = 0L;

    /** 当前 loop 内已经原子应用成功的计划修订序号。 */
    @Column(name = "applied_revision_seq", nullable = false)
    private Long appliedRevisionSeq = 0L;

    /** 独立于 automationStatus 的计划修订状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "revision_state", nullable = false, length = 32)
    private RequirementRevisionState revisionState = RequirementRevisionState.NONE;

    @Column(name = "revision_trigger_comment_id", length = 36)
    private String revisionTriggerCommentId;

    @Column(name = "revision_failure_reason", columnDefinition = "TEXT")
    private String revisionFailureReason;

    @Column(name = "accepted_at")
    private Date acceptedAt;

    @Column(name = "accepted_by_agent", length = 100)
    private String acceptedByAgent;

    @Column(name = "delivery_branch", length = 200)
    private String deliveryBranch;

    @Column(name = "delivery_commit_hash", length = 64)
    private String deliveryCommitHash;

    @Column(name = "delivery_mr_url", length = 500)
    private String deliveryMrUrl;

    @Column(name = "delivery_target_branch", length = 200)
    private String deliveryTargetBranch;

    /**
     * 生成 PRD 时引用的 RequirementDesignContext id。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §9.2、§15.1。
     */
    @Column(name = "design_context_id", length = 36)
    private String designContextId;

    /**
     * 记忆校验状态。新生成初始 NOT_RUN，校验后更新。契约 §9.2、§15.4。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "memory_validation_status", length = 32)
    private MemoryValidationStatus memoryValidationStatus;

    /**
     * 记忆校验结果（JSON 字符串）。契约 §9.2、§19。
     */
    @Column(name = "memory_validation_result_json", columnDefinition = "TEXT")
    private String memoryValidationResultJson;

    @Override
    @Transient
    public String getDisplay() {
        return title;
    }
}
