package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 保存平台配置请求体（B33）。契约 Phase 5 §2 端点 32：POST /api/config/save。
 *
 * <p>两字段均可为空：{@code workspaceRoot} 空 → 走 env-fallback 默认；
 * {@code templateGitlabUrl} 空 → 走脚手架生成路径。故不加 {@code @NotBlank}。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "保存平台配置请求")
public class SavePlatformConfigRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "工作区根目录；空则默认 ${java.io.tmpdir}/sei-online-code", example = "/tmp/sei-online-code")
    private String workspaceRoot;

    @Schema(description = "模板 GitLab 仓库地址；空即走脚手架生成路径", example = "")
    private String templateGitlabUrl;

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public String getTemplateGitlabUrl() {
        return templateGitlabUrl;
    }

    public void setTemplateGitlabUrl(String templateGitlabUrl) {
        this.templateGitlabUrl = templateGitlabUrl;
    }
}
