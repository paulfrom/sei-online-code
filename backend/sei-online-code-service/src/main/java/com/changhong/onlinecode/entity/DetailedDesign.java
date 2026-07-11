package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.DetailedDesignStatus;
import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
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
 * DetailedDesign 实体。按模块拆分的详细设计。
 *
 * @author sei-online-code
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "oc_detailed_design", indexes = {
        @Index(name = "idx_detailed_design_project", columnList = "project_id"),
        @Index(name = "idx_detailed_design_requirement", columnList = "requirement_id"),
        @Index(name = "idx_detailed_design_overview", columnList = "overview_design_id"),
        @Index(name = "idx_detailed_design_status", columnList = "status")
})
@Access(AccessType.FIELD)
public class DetailedDesign extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "requirement_id", nullable = false, length = 36)
    private String requirementId;

    @Column(name = "overview_design_id", nullable = false, length = 36)
    private String overviewDesignId;

    @Column(name = "module_id", nullable = false, length = 128)
    private String moduleId;

    @Column(name = "module_title", length = 200)
    private String moduleTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DetailedDesignStatus status;

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
     * 生成详细设计时引用的 RequirementDesignContext id。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §9.4、§15.3。
     * 默认使用 OverviewDesign 引用的 context，为空时回退 Requirement 引用的 context。
     */
    @Column(name = "design_context_id", length = 36)
    private String designContextId;

    /**
     * 记忆校验状态。契约 §9.4、§15.4。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "memory_validation_status", length = 32)
    private MemoryValidationStatus memoryValidationStatus;

    /**
     * 记忆校验结果（JSON 字符串）。契约 §9.4、§19。
     */
    @Column(name = "memory_validation_result_json", columnDefinition = "TEXT")
    private String memoryValidationResultJson;

    @Override
    @Transient
    public String getDisplay() {
        return moduleTitle;
    }
}
