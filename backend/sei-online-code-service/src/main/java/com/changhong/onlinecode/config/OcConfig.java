package com.changhong.onlinecode.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 集中化配置 Bean — 所有 @Value 配置统一在此声明，通过依赖注入获取。
 *
 * <p>业务类不再直接使用 @Value，改为注入 {@code OcConfig} 并调用对应 getter。
 * 各配置项的默认值与原有 @Value 声明完全一致。</p>
 *
 * @author sei-online-code
 */
@Component
public class OcConfig {

    // ==================== Workspace 配置 ====================

    /** 工作区根路径环境覆盖；未配置时兜底空串（backend 规则 #11 env-with-fallback）。 */
    @Value("${oc.workspace.root:}")
    private String workspaceRoot;

    // ==================== GitLab 配置 ====================

    /** GitLab Host 地址。 */
    @Value("${oc.gitlab.host:}")
    private String gitlabHost;

    /** GitLab API Base URL 环境覆盖。 */
    @Value("${oc.gitlab.api-base-url:}")
    private String gitlabApiBaseUrl;

    /** GitLab Personal Access Token。 */
    @Value("${oc.gitlab.token:}")
    private String gitlabToken;

    /** GitLab 项目 ID。 */
    @Value("${oc.gitlab.project-id:}")
    private String gitlabProjectId;

    /** GitLab 目标分支（默认 main）。 */
    @Value("${oc.gitlab.target-branch:}")
    private String gitlabTargetBranch;

    // ==================== 补偿调度 (Compensation) 配置 ====================

    /** Loop 补偿判定的过期阈值（分钟），超过此值时认为需求/任务已 stale。默认 30 分钟。 */
    @Value("${onlinecode.compensation.loop-stale-minutes:30}")
    private long loopStaleMinutes;

    /** Run 执行超时阈值（分钟），超过此值且仍处于 RUNNING 状态则标记为超时。默认 30 分钟。 */
    @Value("${onlinecode.compensation.run-timeout-minutes:30}")
    private long runTimeoutMinutes;

    // ==================== Memory 重建配置 ====================

    /** CodingTask 增量更新降级为全量重建的变更文件数阈值。默认 50。 */
    @Value("${memory.rebuild.large-change-threshold:50}")
    private int largeChangeThreshold;

    /** 关键路径累计命中阈值，达到即降级为全量重建。默认 3。 */
    @Value("${memory.rebuild.critical-hits-threshold:3}")
    private int criticalHitsThreshold;

    /**
     * 触发全量重建的关键路径片段（逗号分隔）。
     * 只保留真正的构建入口、路由根与迁移文件，让普通业务代码变更不触发全量重建。
     * 默认：build.gradle,settings.gradle,package.json,pnpm-workspace.yaml,bootstrap,routes/,.sql
     */
    @Value("${memory.rebuild.critical-paths:build.gradle,settings.gradle,package.json,pnpm-workspace.yaml,bootstrap,routes/,.sql}")
    private String criticalPathsCsv;

    // ==================== Workspace Getter ====================

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    // ==================== GitLab Getter ====================

    public String getGitlabHost() {
        return gitlabHost;
    }

    public String getGitlabApiBaseUrl() {
        return gitlabApiBaseUrl;
    }

    public String getGitlabToken() {
        return gitlabToken;
    }

    public String getGitlabProjectId() {
        return gitlabProjectId;
    }

    public String getGitlabTargetBranch() {
        return gitlabTargetBranch;
    }

    // ==================== Compensation Getter ====================

    public long getLoopStaleMinutes() {
        return loopStaleMinutes;
    }

    public long getRunTimeoutMinutes() {
        return runTimeoutMinutes;
    }

    // ==================== Memory Rebuild Getter ====================

    public int getLargeChangeThreshold() {
        return largeChangeThreshold;
    }

    public int getCriticalHitsThreshold() {
        return criticalHitsThreshold;
    }

    /** 返回逗号分隔的 critical paths 原始值。若需解析为 Set，调用 {@link #getCriticalPaths()}。 */
    public String getCriticalPathsCsv() {
        return criticalPathsCsv;
    }

    /** 将 criticalPathsCsv 解析为有序去重的路径片段 Set。 */
    public Set<String> getCriticalPaths() {
        Set<String> paths = new LinkedHashSet<>();
        if (criticalPathsCsv == null || criticalPathsCsv.isBlank()) {
            return paths;
        }
        for (String s : criticalPathsCsv.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return paths;
    }
}
