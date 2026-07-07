package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.Date;

/**
 * Requirement DTO。契约 §2.2。
 *
 * @author sei-online-code
 */
@Schema(description = "需求 DTO")
public class RequirementDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "项目 ID 不能为空")
    @Schema(description = "所属项目 ID")
    private String projectId;

    @NotBlank(message = "需求标题不能为空")
    @Schema(description = "需求标题")
    private String title;

    @Schema(description = "需求描述")
    private String description;

    @Schema(description = "PRD 状态")
    private RequirementStatus status;

    @Schema(description = "PRD 版本号")
    private Integer prdVersion;

    @Schema(description = "PRD 内容（JSON 字符串）")
    private String prdContent;

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

    public RequirementStatus getStatus() {
        return status;
    }

    public void setStatus(RequirementStatus status) {
        this.status = status;
    }

    public Integer getPrdVersion() {
        return prdVersion;
    }

    public void setPrdVersion(Integer prdVersion) {
        this.prdVersion = prdVersion;
    }

    public String getPrdContent() {
        return prdContent;
    }

    public void setPrdContent(String prdContent) {
        this.prdContent = prdContent;
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
