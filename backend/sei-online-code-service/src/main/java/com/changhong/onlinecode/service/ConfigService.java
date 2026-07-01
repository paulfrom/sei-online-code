package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.PlatformConfigDao;
import com.changhong.onlinecode.entity.PlatformConfig;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Objects;

/**
 * 平台配置服务（B34）。契约 Phase 5 §1.1、§2 端点 31/32。
 *
 * <p>单例：{@link #get()} 缺失时补建默认行、{@link #save} 幂等 upsert 固定主键
 * {@link PlatformConfig#FIXED_ID} 的那一行。工作区根目录采用 env-with-fallback（backend 规则 #11）：
 * 配置行 workspaceRoot 非空优先；否则回退环境变量 {@code oc.workspace.root}；仍空回退
 * {@code ${java.io.tmpdir}/sei-online-code}。</p>
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

    @PersistenceContext
    private EntityManager entityManager;

    /** 工作区根环境覆盖；未配置时兜底空串（backend 规则 #11 env-with-fallback）。 */
    @Value("${oc.workspace.root:}")
    private String envWorkspaceRoot;

    public ConfigService(PlatformConfigDao dao) {
        this.dao = dao;
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
        PlatformConfig existing = dao.findOne(PlatformConfig.FIXED_ID);
        if (Objects.isNull(existing)) {
            PlatformConfig created = new PlatformConfig();
            created.setId(PlatformConfig.FIXED_ID);
            created.setWorkspaceRoot(workspaceRoot);
            created.setTemplateGitlabUrl(templateGitlabUrl);
            entityManager.persist(created);
            return OperateResultWithData.operationSuccessWithData(created);
        }
        existing.setWorkspaceRoot(workspaceRoot);
        existing.setTemplateGitlabUrl(templateGitlabUrl);
        return super.save(existing);
    }

    /**
     * 解析生效的工作区根目录（env-with-fallback，backend 规则 #11）：
     * 配置行 workspaceRoot 非空优先 → 环境变量 {@code oc.workspace.root} → {@code ${java.io.tmpdir}/sei-online-code}。
     *
     * @param config 已加载的单例配置行（可空）
     * @return 生效的工作区根目录绝对路径
     */
    public String resolveWorkspaceRoot(PlatformConfig config) {
        if (config != null && isNotBlank(config.getWorkspaceRoot())) {
            return config.getWorkspaceRoot();
        }
        if (isNotBlank(envWorkspaceRoot)) {
            return envWorkspaceRoot;
        }
        return System.getProperty("java.io.tmpdir") + File.separator + DEFAULT_WORKSPACE_DIR;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
