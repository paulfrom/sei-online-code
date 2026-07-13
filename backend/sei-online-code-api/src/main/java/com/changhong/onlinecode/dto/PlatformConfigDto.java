package com.changhong.onlinecode.dto;

import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 平台配置 DTO（B31）。契约 Phase 5 §1.1 —— 单例平台配置行。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "平台配置 DTO（单例）")
public class PlatformConfigDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "工作区根目录；空则默认 ${java.io.tmpdir}/sei-online-code", example = "/tmp/sei-online-code")
    private String workspaceRoot;

    @Schema(description = "模板 GitLab 仓库地址；空即走脚手架生成路径", example = "")
    private String templateGitlabUrl;

    @Schema(description = "交付 GitLab API Base URL", example = "https://gitlab.example.com")
    private String gitlabApiBaseUrl;

    @Schema(description = "交付 GitLab Project ID 或 path", example = "group/project")
    private String gitlabProjectId;

    @Schema(description = "交付目标分支", example = "main")
    private String gitlabTargetBranch;

    @Schema(description = "创建时间")
    private Date createdDate;
}
