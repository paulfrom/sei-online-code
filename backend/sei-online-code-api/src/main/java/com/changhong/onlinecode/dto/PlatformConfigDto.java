package com.changhong.onlinecode.dto;

import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * 平台配置 DTO（B31）。契约 Phase 5 §1.1 —— 单例平台配置行。
 *
 * @author sei-online-code
 */
@Schema(description = "平台配置 DTO（单例）")
public class PlatformConfigDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "工作区根目录；空则默认 ${java.io.tmpdir}/sei-online-code", example = "/tmp/sei-online-code")
    private String workspaceRoot;

    @Schema(description = "模板 GitLab 仓库地址；空即走脚手架生成路径", example = "")
    private String templateGitlabUrl;

    @Schema(description = "创建时间")
    private Date createdDate;

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

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }
}
