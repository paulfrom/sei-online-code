package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.dto.enums.OverviewDesignStatus;
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

import java.util.Date;

/**
 * OverviewDesign 实体。每个 Requirement 一份概览设计。
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_overview_design", indexes = {
        @Index(name = "idx_overview_design_project", columnList = "project_id"),
        @Index(name = "idx_overview_design_requirement", columnList = "requirement_id")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class OverviewDesign extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "requirement_id", nullable = false, length = 36)
    private String requirementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OverviewDesignStatus status;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

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

    /**
     * 生成概览设计时引用的 RequirementDesignContext id。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §9.3、§15.2。
     */
    @Column(name = "design_context_id", length = 36)
    private String designContextId;

    /**
     * 记忆校验状态。契约 §9.3、§15.4。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "memory_validation_status", length = 32)
    private MemoryValidationStatus memoryValidationStatus;

    /**
     * 记忆校验结果（JSON 字符串）。契约 §9.3、§19。
     */
    @Column(name = "memory_validation_result_json", columnDefinition = "TEXT")
    private String memoryValidationResultJson;

    @Override
    @Transient
    public String getDisplay() {
        return requirementId;
    }
}
