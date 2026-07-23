package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
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
@Data
@Schema(description = "保存平台配置请求")
public class SavePlatformConfigRequest implements Serializable {

    @Schema(description = "工作区根目录；空则默认 ${java.io.tmpdir}/sei-online-code", example = "/tmp/sei-online-code")
    @Pattern(regexp = "^(\\s*|/.*|[A-Za-z]:\\\\.*)$",
            message = "workspaceRoot 为空或绝对路径")
    private String workspaceRoot;

    @Schema(description = "模板 GitLab 仓库地址；空即走脚手架生成路径", example = "")
    private String templateGitlabUrl;

    @Schema(description = "GitLab Host", example = "https://gitlab.example.com")
    private String gitlabHost;

    @Schema(description = "交付 GitLab token；为空表示保留现有值", example = "")
    private String gitlabToken;

    @Schema(description = "交付 GitLab Project ID 或 path", example = "group/project")
    private String gitlabProjectId;

    @Schema(description = "交付目标分支", example = "main")
    private String gitlabTargetBranch;
}
