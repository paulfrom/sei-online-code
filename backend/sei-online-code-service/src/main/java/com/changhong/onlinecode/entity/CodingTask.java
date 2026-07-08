package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.sei.core.entity.BaseAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
@Data
@EqualsAndHashCode(callSuper = true)
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

    @Column(name = "last_retry_at")
    private Date lastRetryAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private Date nextRetryAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_trigger_source", length = 32)
    private TriggerSource lastTriggerSource;

    @Override
    @Transient
    public String getDisplay() {
        return title;
    }
}