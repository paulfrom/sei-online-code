package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

import java.io.Serializable;

/**
 * 保存平台配置请求体（B33）。契约 Phase 5 §2 端点 32：POST /api/config/save。
 *
 * <p>两字段均可为空：{@code workspaceRoot} 空 → 走 env-fallback 默认；
 * {@code templateGitlabUrl} 空 → 走脚手架生成路径。故不加 {@code @NotBlank}。
 * 但 {@code workspaceRoot} 非空时须为绝对路径（{@code @Pattern}）——相对路径在 DTO 边界即拒，
 * 避免落入 DB 后到 resolve 期才抛 {@code IllegalStateException: 不安全的工作区根}（500，原因不直观）。
 * 黑名单根（如 {@code /etc}）的深度校验仍由 {@code WorkspaceManager.isSafeRoot} 在 resolve 期兜底。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "保存平台配置请求")
public class SavePlatformConfigRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Pattern(regexp = "^(?:\\s*|/.*|[A-Za-z]:[\\\\/].*)$",
            message = "workspaceRoot 必须为绝对路径（如 /tmp/sei-online-code），空则走默认；相对路径将被拒绝")
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
