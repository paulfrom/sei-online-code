package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.DetailedDesignStatus;
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

import java.util.Date;

/**
 * DetailedDesign 实体。按 feature 拆分的详细设计。
 *
 * @author sei-online-code
 */
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

    @Column(name = "feature_id", nullable = false, length = 128)
    private String featureId;

    @Column(name = "feature_title", length = 200)
    private String featureTitle;

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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(String requirementId) {
        this.requirementId = requirementId;
    }

    public String getOverviewDesignId() {
        return overviewDesignId;
    }

    public void setOverviewDesignId(String overviewDesignId) {
        this.overviewDesignId = overviewDesignId;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public String getModuleTitle() {
        return moduleTitle;
    }

    public void setModuleTitle(String moduleTitle) {
        this.moduleTitle = moduleTitle;
    }

    public String getFeatureId() {
        return featureId;
    }

    public void setFeatureId(String featureId) {
        this.featureId = featureId;
    }

    public String getFeatureTitle() {
        return featureTitle;
    }

    public void setFeatureTitle(String featureTitle) {
        this.featureTitle = featureTitle;
    }

    public DetailedDesignStatus getStatus() {
        return status;
    }

    public void setStatus(DetailedDesignStatus status) {
        this.status = status;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFailureSummary() {
        return failureSummary;
    }

    public void setFailureSummary(String failureSummary) {
        this.failureSummary = failureSummary;
    }

    public String getFailureDetail() {
        return failureDetail;
    }

    public void setFailureDetail(String failureDetail) {
        this.failureDetail = failureDetail;
    }

    public Date getLastFailedAt() {
        return lastFailedAt;
    }

    public void setLastFailedAt(Date lastFailedAt) {
        this.lastFailedAt = lastFailedAt;
    }

    @Override
    @Transient
    public String getDisplay() {
        return featureTitle;
    }
}
