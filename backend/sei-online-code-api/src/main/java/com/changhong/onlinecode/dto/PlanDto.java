package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.plan.PlanContent;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * PlanDto DTO。契约 §2.1 —— 规划书。
 *
 * @author sei-online-code
 */
@Schema(description = "规划书 DTO")
public class PlanDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "所属项目 id")
    private String projectId;

    @Schema(description = "版本号（从 1 起，递增）", example = "1")
    private Integer version;

    @Schema(description = "规划状态", example = "DRAFT")
    private PlanStatus status;

    @Schema(description = "规划内容")
    private PlanContent content;

    @Schema(description = "上次重生时的修改提示", example = "增加导出功能")
    private String modifyHint;

    @Schema(description = "是否最新版本", example = "true")
    private Boolean isLatest;

    @Schema(description = "创建时间")
    private Date createdDate;

    @Schema(description = "最后编辑时间")
    private Date lastEditedDate;

    @Schema(description = "失败码")
    private FailureCode failureCode;

    @Schema(description = "失败阶段")
    private FailureStage failureStage;

    @Schema(description = "失败摘要")
    private String failureSummary;

    @Schema(description = "失败详情")
    private String failureDetail;

    @Schema(description = "最近失败时间")
    private Date lastFailedAt;

    @Schema(description = "最近重试时间")
    private Date lastRetryAt;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @Schema(description = "下次可重试时间")
    private Date nextRetryAt;

    @Schema(description = "最近触发来源")
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

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getLastEditedDate() {
        return lastEditedDate;
    }

    public void setLastEditedDate(Date lastEditedDate) {
        this.lastEditedDate = lastEditedDate;
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
}
