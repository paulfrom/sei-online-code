package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * 优化项目（反馈再入）请求体。契约 Phase 4 §2 端点 26：POST /api/project/optimize。
 *
 * <p>从 PREVIEW 携带非空 feedback 再入：需求 Agent 增量更新 Spec → 新版本，开启新回合。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "优化项目请求")
public class OptimizeProjectRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "projectId 不能为空")
    @Schema(description = "项目 id")
    private String projectId;

    @NotBlank(message = "feedback 不能为空")
    @Schema(description = "本回合优化诉求（不能为空）", example = "把库存列表加上导出按钮")
    private String feedback;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}
