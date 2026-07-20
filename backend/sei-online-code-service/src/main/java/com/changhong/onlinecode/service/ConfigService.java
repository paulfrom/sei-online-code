package com.changhong.onlinecode.service;

import com.changhong.onlinecode.config.OcConfig;
import com.changhong.onlinecode.dao.PlatformConfigDao;
import com.changhong.onlinecode.entity.PlatformConfig;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Objects;

/**
 * 平台配置服务（B34）。契约 Phase 5 §1.1、§2 端点 31/32。
 *
 * <p>单例：{@link #get()} 缺失时补建默认行、{@link #save} 幂等 upsert 固定主键
 * {@link PlatformConfig#FIXED_ID} 的那一行。工作区根目录和 GitLab 配置均采用 env-with-fallback（backend 规则 #11）：
 * 配置行非空优先；否则回退同名环境变量（{@code oc.gitlab.api-base-url} / {@code oc.gitlab.token} / 等）；
 * 仍空则按各字段语义兜底（如 targetBranch 默认 {@code main}）。</p>
 *
 * <p>框架 {@code BaseEntityService.save} 对「预置且尚不存在的主键」会拒绝（视为非法更新），
 * 故固定主键单例的首次插入直接走 {@link EntityManager#persist}；后续更新走 {@code dao.save}（merge）。</p>
 *
 * @author sei-online-code
 */
@Service
public class ConfigService extends BaseEntityService<PlatformConfig> {

    /** 工作区根默认子目录名。 */
    private static final String DEFAULT_WORKSPACE_DIR = "sei-online-code";

    private final PlatformConfigDao dao;
    private final OcConfig ocConfig;

    @PersistenceContext
    private EntityManager entityManager;

    public ConfigService(PlatformConfigDao dao, OcConfig ocConfig) {
        this.dao = dao;
        this.ocConfig = ocConfig;
    }

    @Override
    protected BaseEntityDao<PlatformConfig> getDao() {
        return dao;
    }

    /**
     * 读取单例平台配置；缺失时补建默认行（workspaceRoot 空、templateGitlabUrl 空）。
     *
     * @return 单例配置行
     */
    @Transactional(rollbackFor = Exception.class)
    public PlatformConfig get() {
        PlatformConfig existing = dao.findOne(PlatformConfig.FIXED_ID);
        if (Objects.nonNull(existing)) {
            return existing;
        }
        PlatformConfig created = new PlatformConfig();
        created.setId(PlatformConfig.FIXED_ID);
        created.setWorkspaceRoot(null);
        created.setTemplateGitlabUrl("");
        created.setGitlabTargetBranch("main");
        entityManager.persist(created);
        return created;
    }

    /**
     * upsert 单例平台配置。首次（行不存在）走 persist，后续走 merge。
     *
     * @param workspaceRoot     工作区根目录（可空，空则运行期 env-fallback）
     * @param templateGitlabUrl 模板仓库地址（可空，空即走脚手架生成路径）
     * @return 写操作结果（携带保存后的单例行）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<PlatformConfig> save(String workspaceRoot, String templateGitlabUrl) {
        return save(workspaceRoot, templateGitlabUrl, null, null, null, null);
    }

    /**
     * upsert 单例平台配置，包含 GitLab 交付配置。gitlabToken 为空时保留旧值。
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<PlatformConfig> save(String workspaceRoot,
                                                      String templateGitlabUrl,
                                                      String gitlabApiBaseUrl,
                                                      String gitlabToken,
                                                      String gitlabProjectId,
                                                      String gitlabTargetBranch) {
        PlatformConfig existing = dao.findOne(PlatformConfig.FIXED_ID);
        if (Objects.isNull(existing)) {
            PlatformConfig created = new PlatformConfig();
            created.setId(PlatformConfig.FIXED_ID);
            created.setWorkspaceRoot(workspaceRoot);
            created.setTemplateGitlabUrl(templateGitlabUrl);
            created.setGitlabApiBaseUrl(gitlabApiBaseUrl);
            created.setGitlabToken(gitlabToken);
            created.setGitlabProjectId(gitlabProjectId);
            created.setGitlabTargetBranch(isNotBlank(gitlabTargetBranch) ? gitlabTargetBranch : "main");
            entityManager.persist(created);
            return OperateResultWithData.operationSuccessWithData(created);
        }
        existing.setWorkspaceRoot(workspaceRoot);
        existing.setTemplateGitlabUrl(templateGitlabUrl);
        existing.setGitlabApiBaseUrl(gitlabApiBaseUrl);
        if (isNotBlank(gitlabToken)) {
            existing.setGitlabToken(gitlabToken);
        }
        existing.setGitlabProjectId(gitlabProjectId);
        existing.setGitlabTargetBranch(isNotBlank(gitlabTargetBranch) ? gitlabTargetBranch : "main");
        return super.save(existing);
    }

    /**
     * 解析生效的工作区根目录（env-with-fallback，backend 规则 #11）：
     * 配置行 workspaceRoot 非空优先 → 环境变量 {@code oc.workspace.root} → {@code ${java.io.tmpdir}/sei-online-code}。
     *
     * <p>DB 与 env 两个来源返回前均 {@code trim()}：incidental 前后空白（多行 env 导出、YAML 缩进等引入）
     * 会让 {@code WorkspaceManager.isSafeRoot} 的 {@code new File(root).isAbsolute()} 误判为相对路径而拒绝。
     * 默认值（{@code java.io.tmpdir} 拼接）为系统生成，不 trim。</p>
     *
     * @param config 已加载的单例配置行（可空）
     * @return 生效的工作区根目录绝对路径
     */
    public String resolveWorkspaceRoot(PlatformConfig config) {
        if (config != null && isNotBlank(config.getWorkspaceRoot())) {
            return config.getWorkspaceRoot().trim();
        }
        if (isNotBlank(ocConfig.getWorkspaceRoot())) {
            return ocConfig.getWorkspaceRoot().trim();
        }
        return System.getProperty("java.io.tmpdir") + File.separator + DEFAULT_WORKSPACE_DIR;
    }

    /**
     * 解析生效的 GitLab API Base URL（env-with-fallback）：
     * 配置行 gitlabApiBaseUrl 非空优先 → 环境变量 {@code oc.gitlab.api-base-url} → {@code null}。
     */
    public String resolveGitlabApiBaseUrl(PlatformConfig config) {
        if (config != null && isNotBlank(config.getGitlabApiBaseUrl())) {
            return config.getGitlabApiBaseUrl().trim();
        }
        if (isNotBlank(ocConfig.getGitlabApiBaseUrl())) {
            return ocConfig.getGitlabApiBaseUrl().trim();
        }
        return null;
    }

    /**
     * 解析生效的 GitLab token（env-with-fallback）：
     * 配置行 gitlabToken 非空优先 → 环境变量 {@code oc.gitlab.token} → {@code null}。
     */
    public String resolveGitlabToken(PlatformConfig config) {
        if (config != null && isNotBlank(config.getGitlabToken())) {
            return config.getGitlabToken().trim();
        }
        if (isNotBlank(ocConfig.getGitlabToken())) {
            return ocConfig.getGitlabToken().trim();
        }
        return null;
    }

    /**
     * 解析生效的 GitLab Project ID（env-with-fallback）：
     * 配置行 gitlabProjectId 非空优先 → 环境变量 {@code oc.gitlab.project-id} → {@code null}。
     */
    public String resolveGitlabProjectId(PlatformConfig config) {
        if (config != null && isNotBlank(config.getGitlabProjectId())) {
            return config.getGitlabProjectId().trim();
        }
        if (isNotBlank(ocConfig.getGitlabProjectId())) {
            return ocConfig.getGitlabProjectId().trim();
        }
        return null;
    }

    /**
     * 解析生效的 GitLab 目标分支（env-with-fallback）：
     * 配置行 gitlabTargetBranch 非空优先 → 环境变量 {@code oc.gitlab.target-branch} → {@code main}。
     */
    public String resolveGitlabTargetBranch(PlatformConfig config) {
        if (config != null && isNotBlank(config.getGitlabTargetBranch())) {
            return config.getGitlabTargetBranch().trim();
        }
        if (isNotBlank(ocConfig.getGitlabTargetBranch())) {
            return ocConfig.getGitlabTargetBranch().trim();
        }
        return "main";
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
