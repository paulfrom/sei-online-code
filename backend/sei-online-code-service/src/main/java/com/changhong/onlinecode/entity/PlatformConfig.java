package com.changhong.onlinecode.entity;

import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 平台配置实体（B31）。契约 Phase 5 §1.1 —— 全局单例配置行。
 *
 * <p><b>单例</b>：整表恒定一行，主键固定为 {@link #FIXED_ID}（"CONFIG"），{@code save} 幂等 upsert
 * 该行、{@code get} 缺失时补建默认行。承载两项平台级配置：</p>
 * <ul>
 *   <li>{@code workspaceRoot} —— 工作区根目录；空则运行期回退到 {@code oc.workspace.root} 环境变量，
 *       仍空则回退到 {@code ${java.io.tmpdir}/sei-online-code}（backend 规则 #11 env-with-fallback）。</li>
 *   <li>{@code templateGitlabUrl} —— 模板仓库地址；<b>无默认</b>，空即走 no-template 脚手架生成路径（day-one 路径）。</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_platform_config")
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class PlatformConfig extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    /** 单例行固定主键。 */
    public static final String FIXED_ID = "CONFIG";

    /** 工作区根目录；空则运行期按 env-fallback 解析（见类注释）。 */
    @Column(name = "workspace_root", length = 500)
    private String workspaceRoot;

    /** 模板 GitLab 仓库地址；空即走脚手架生成路径。无默认。 */
    @Column(name = "template_gitlab_url", length = 500)
    private String templateGitlabUrl;

    /** 交付 GitLab API Base URL。 */
    @Column(name = "gitlab_api_base_url", length = 500)
    private String gitlabApiBaseUrl;

    /** 交付 GitLab token。 */
    @Column(name = "gitlab_token", length = 500)
    private String gitlabToken;

    /** 交付 GitLab Project ID 或 path。 */
    @Column(name = "gitlab_project_id", length = 300)
    private String gitlabProjectId;

    /** 交付目标分支。 */
    @Column(name = "gitlab_target_branch", length = 200)
    private String gitlabTargetBranch;

    @Override
    @Transient
    public String getDisplay() {
        return "PlatformConfig[" + FIXED_ID + "]";
    }
}
