package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.DetailedDesignStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * DetailedDesign DTO。契约 §2.4。
 *
 * @author sei-online-code
 */
@Schema(description = "详细设计 DTO")
public class DetailedDesignDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "项目 ID")
    private String projectId;

    @Schema(description = "需求 ID")
    private String requirementId;

    @Schema(description = "概览设计 ID")
    private String overviewDesignId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "模块标题")
    private String moduleTitle;

    @Schema(description = "功能 ID")
    private String featureId;

    @Schema(description = "功能标题")
    private String featureTitle;

    @Schema(description = "状态")
    private DetailedDesignStatus status;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "内容（JSON 字符串）")
    private String content;

    @Schema(description = "失败摘要")
    private String failureSummary;

    @Schema(description = "创建时间")
    private Date createdDate;

    @Schema(description = "最后编辑时间")
    private Date lastEditedDate;

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
}
