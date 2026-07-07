package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.plan.PlanContent;
import com.changhong.onlinecode.entity.converter.PlanContentConverter;
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

/**
 * Plan 实体。契约 §8.1 —— 规划书（单表多行 + is_latest 版本历史）。
 *
 * <p>每项目一份规划书，重生时 version+1 新增行，旧行 is_latest=FALSE。
 * content 为 PlanContent JSON（TEXT，经 {@link PlanContentConverter}）。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_plan", indexes = {
        @Index(name = "uk_plan_proj_ver", columnList = "project_id,version", unique = true),
        @Index(name = "idx_plan_project", columnList = "project_id")
})
@Access(AccessType.FIELD)
public class Plan extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PlanStatus status;

    @Convert(converter = PlanContentConverter.class)
    @Column(name = "content", columnDefinition = "TEXT")
    private PlanContent content;

    @Column(name = "modify_hint", columnDefinition = "TEXT")
    private String modifyHint;

    @Column(name = "is_latest", nullable = false)
    private Boolean isLatest = Boolean.TRUE;

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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
        this.status = status;
    }

    public PlanContent getContent() {
        return content;
    }

    public void setContent(PlanContent content) {
        this.content = content;
    }

    public String getModifyHint() {
        return modifyHint;
    }

    public void setModifyHint(String modifyHint) {
        this.modifyHint = modifyHint;
    }

    public Boolean getIsLatest() {
        return isLatest;
    }

    public void setIsLatest(Boolean isLatest) {
        this.isLatest = isLatest;
    }

    public FailureCode getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(FailureCode failureCode) {
        this.failureCode = failureCode;
    }

    public FailureStage getFailureStage() {
        return failureStage;
    }

    public void setFailureStage(FailureStage failureStage) {
        this.failureStage = failureStage;
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

    public Date getLastRetryAt() {
        return lastRetryAt;
    }

    public void setLastRetryAt(Date lastRetryAt) {
        this.lastRetryAt = lastRetryAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Date getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Date nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public TriggerSource getLastTriggerSource() {
        return lastTriggerSource;
    }

    public void setLastTriggerSource(TriggerSource lastTriggerSource) {
        this.lastTriggerSource = lastTriggerSource;
    }

    @Override
    @Transient
    public String getDisplay() {
        return projectId;
    }
}
