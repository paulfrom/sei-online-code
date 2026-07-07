package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.util.Date;
import java.util.List;

/**
 * CodingTask 实体。每个详细设计对应一个编码任务。
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_coding_task", indexes = {
        @Index(name = "idx_coding_task_project", columnList = "project_id"),
        @Index(name = "idx_coding_task_requirement", columnList = "requirement_id"),
        @Index(name = "idx_coding_task_detailed_design", columnList = "detailed_design_id"),
        @Index(name = "idx_coding_task_status", columnList = "status")
})
@Access(AccessType.FIELD)
public class CodingTask extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "requirement_id", nullable = false, length = 36)
    private String requirementId;

    @Column(name = "detailed_design_id", nullable = false, length = 36)
    private String detailedDesignId;

    @Column(name = "detailed_design_version", nullable = false)
    private Integer detailedDesignVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CodingTaskStatus status;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Convert(converter = com.changhong.onlinecode.entity.converter.StringListConverter.class)
    @Column(name = "file_scope", columnDefinition = "TEXT")
    private List<String> fileScope;

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

    public String getDetailedDesignId() {
        return detailedDesignId;
    }

    public void setDetailedDesignId(String detailedDesignId) {
        this.detailedDesignId = detailedDesignId;
    }

    public Integer getDetailedDesignVersion() {
        return detailedDesignVersion;
    }

    public void setDetailedDesignVersion(Integer detailedDesignVersion) {
        this.detailedDesignVersion = detailedDesignVersion;
    }

    public CodingTaskStatus getStatus() {
        return status;
    }

    public void setStatus(CodingTaskStatus status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getFileScope() {
        return fileScope;
    }

    public void setFileScope(List<String> fileScope) {
        this.fileScope = fileScope;
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
        return title;
    }
}
