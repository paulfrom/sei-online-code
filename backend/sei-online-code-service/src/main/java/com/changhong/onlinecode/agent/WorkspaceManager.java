package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.WorkspaceSource;
import com.changhong.onlinecode.entity.PlatformConfig;
import com.changhong.onlinecode.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

/**
 * WorkspaceManager（B35）。契约 Phase 5 §3，蓝图参照 multica {@code daemon/local_directory.go} + {@code repocache/cache.go}。
 *
 * <p>在可配置的 Workspace Root 下解析每个项目的工作区目录，语义为 <b>clone-once + reuse</b>：
 * 目录已存在即复用（后续 Build Loop 回合原地增量编辑，绝不重复 clone/生成）；不存在则按平台配置
 * 是否设置 templateGitlabUrl 决定 provision 来源——有地址走 CLONE、空则走 SCAFFOLD 生成 canonical SUID 脚手架。</p>
 *
 * <p>路径安全：{@link #isSafeRoot(String)} 黑名单拒绝把系统根/盘符根作为工作区根（参照
 * {@code local_directory.go} 黑名单），防止误删/误写系统目录。</p>
 *
 * <p>本轮 compile-only：解析 + clone-vs-scaffold 决策 + 安全判定为真实可单测逻辑；真实
 * git clone 与 fs 写入挂 {@code TODO(oma-deferred)}。</p>
 *
 * @author sei-online-code
 */
@Component
public class WorkspaceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceManager.class);

    /**
     * 禁止作为工作区根的黑名单（参照 local_directory.go）：系统根 / 盘符根 / 常见系统目录。
     * 命中任一即拒绝——工作区在其下按 projectId 建/删子目录，落到这些根会危及系统。
     */
    private static final Set<String> BLACKLIST_ROOTS = Set.of(
            "/", "/root", "/home", "/etc", "/usr", "/bin", "/boot", "/dev", "/lib",
            "/proc", "/sys", "/var", "/opt", "/sbin",
            "c:\\", "c:", "d:\\", "d:");

    private final ConfigService configService;
    private final ScaffoldGenerator scaffoldGenerator;

    public WorkspaceManager(ConfigService configService, ScaffoldGenerator scaffoldGenerator) {
        this.configService = configService;
        this.scaffoldGenerator = scaffoldGenerator;
    }

    /**
     * 解析项目工作区目录（契约 §3）：
     * <pre>
     * root = configService.resolveWorkspaceRoot(config)   // env-with-fallback
     * dir  = root / projectId
     * if exists(dir):  return {dir, provisioned:true, source:decideSource}   // clone-once 复用
     * else:            clone(if url) 或 scaffold(else) → {dir, provisioned:false, source}
     * </pre>
     *
     * @param projectId 项目 id
     * @return 解析结果（path/provisioned/source）
     */
    public WorkspaceResolveResult resolve(String projectId) {
        PlatformConfig config = configService.get();
        String root = configService.resolveWorkspaceRoot(config);

        if (!isSafeRoot(root)) {
            throw new IllegalStateException("不安全的工作区根，拒绝解析: " + root);
        }

        WorkspaceSource source = decideSource(config);
        String dir = Path.of(root, projectId).toString();

        // clone-once 复用：目录已存在则不再 clone/生成，直接复用。
        if (new File(dir).exists()) {
            return new WorkspaceResolveResult(dir, true, source);
        }

        if (source == WorkspaceSource.CLONE) {
            // TODO(oma-deferred): 真实 git clone(config.templateGitlabUrl, dir)（参照 repocache/cache.go clone-once）
            LOGGER.info("workspace: 决定 CLONE（模板地址已配置），真实 clone 待运行期接入 dir={}", dir);
        } else {
            // TODO(oma-deferred): 按 scaffoldGenerator.generate() 清单真实落盘到 dir
            LOGGER.info("workspace: 决定 SCAFFOLD（无模板地址），清单条目数={}，真实落盘待运行期接入 dir={}",
                    scaffoldGenerator.generate().size(), dir);
        }
        return new WorkspaceResolveResult(dir, false, source);
    }

    /**
     * 决定 provision 来源：配置了 templateGitlabUrl → CLONE；否则 SCAFFOLD（契约 §3）。
     *
     * @param config 平台配置（可空，视为无模板地址）
     * @return CLONE 或 SCAFFOLD
     */
    public WorkspaceSource decideSource(PlatformConfig config) {
        if (config != null && config.getTemplateGitlabUrl() != null
                && !config.getTemplateGitlabUrl().isBlank()) {
            return WorkspaceSource.CLONE;
        }
        return WorkspaceSource.SCAFFOLD;
    }

    /**
     * 工作区根安全判定（黑名单，参照 local_directory.go）：拒绝空/系统根/盘符根/系统目录。
     *
     * @param root 工作区根路径
     * @return 安全（非黑名单、非空、绝对路径）则 true
     */
    public boolean isSafeRoot(String root) {
        if (root == null || root.isBlank()) {
            return false;
        }
        String normalized = normalize(root);
        if (BLACKLIST_ROOTS.contains(normalized)) {
            return false;
        }
        // 必须是绝对路径（相对路径无法确定落点，保守拒绝）
        return new File(root).isAbsolute();
    }

    /**
     * 归一化用于黑名单比对：去尾部分隔符、转小写（Windows 盘符大小写不敏感）。
     *
     * @param root 原始路径
     * @return 归一化后的比对键
     */
    private String normalize(String root) {
        String s = root.trim().toLowerCase();
        // 去除尾部多余分隔符，但保留纯 "/" 本身
        while (s.length() > 1 && (s.endsWith("/") || s.endsWith("\\"))) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
