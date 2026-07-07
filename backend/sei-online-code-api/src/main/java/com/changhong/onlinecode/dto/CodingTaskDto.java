package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.List;

/**
 * CodingTask DTO。契约 §2.5。
 *
 * @author sei-online-code
 */
@Schema(description = "编码任务 DTO")
public class CodingTaskDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "项目 ID")
    private String projectId;

    @Schema(description = "需求 ID")
    private String requirementId;

    @Schema(description = "详细设计 ID")
    private String detailedDesignId;

    @Schema(description = "详细设计版本")
    private Integer detailedDesignVersion;

    @Schema(description = "状态")
    private CodingTaskStatus status;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "文件范围")
    private List<String> fileScope;

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
