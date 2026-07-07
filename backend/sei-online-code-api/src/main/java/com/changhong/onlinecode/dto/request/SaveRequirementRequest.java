package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建/编辑 Requirement 请求。
 *
 * @author sei-online-code
 */
@Schema(description = "保存 Requirement 请求")
public class SaveRequirementRequest {

    @Schema(description = "需求 ID（新建时为空）")
    private String id;

    @NotBlank(message = "项目 ID 不能为空")
    @Schema(description = "所属项目 ID")
    private String projectId;

    @NotBlank(message = "需求标题不能为空")
    @Schema(description = "需求标题")
    private String title;

    @Schema(description = "需求描述")
    private String description;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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
}
