package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.MemorySeedTemplateDao;
import com.changhong.onlinecode.dto.enums.MemorySeedTemplateSourceType;
import com.changhong.onlinecode.dto.enums.MemorySeedTemplateStatus;
import com.changhong.onlinecode.entity.MemorySeedTemplate;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 平台 seed 记忆模板服务。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §6.1.0、§8.1、§10.1。
 *
 * <p>职责：管理可配置 seed 模板；首次启动或 DB 默认模板缺失时从 classpath
 * {@code memory-seeds/default} bootstrap；保存草稿、发布新版本、切换全局默认、归档旧模板；
 * 解析项目使用的 seed 模板（显式选择优先，未绑定取全局默认）。</p>
 *
 * <p>不变式：全局同一时间只能有一个 {@code ACTIVE + is_default=true} 模板。由 V24 partial unique index
 * 兜底，并在 {@link #setDefault} 切换事务中维护。</p>
 *
 * @author sei-online-code
 */
@Service
public class MemorySeedTemplateService extends BaseEntityService<MemorySeedTemplate> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemorySeedTemplateService.class);

    /** 内置 default seed 资源根目录（classpath）。 */
    public static final String BUILTIN_RESOURCE_BASE = "memory-seeds/default/agent-memory/";

    /** 内置 default 模板 code 与初始版本。 */
    public static final String BUILTIN_DEFAULT_CODE = "default";
    public static final int BUILTIN_DEFAULT_VERSION = 1;

    /** 四个 agent-memory seed 文件名。 */
    static final String FILE_PROJECT_MEMORY = "project-memory.md";
    static final String FILE_MEMORY_RULES = "memory-rules.md";
    static final String FILE_DECISIONS = "decisions.md";
    static final String FILE_MODULES = "modules.md";

    private final MemorySeedTemplateDao dao;

    @PersistenceContext
    private EntityManager entityManager;

    public MemorySeedTemplateService(MemorySeedTemplateDao dao) {
        this.dao = dao;
    }

    @Override
    protected BaseEntityDao<MemorySeedTemplate> getDao() {
        return dao;
    }

    /**
     * 启动期 bootstrap：若 DB 不存在 {@code ACTIVE + is_default=true} 默认模板，则从 classpath
     * {@code memory-seeds/default} 创建一条 builtin 默认模板。
     *
     * <p>幂等：默认模板已存在则直接返回，classpath 变化不覆盖 DB 配置（契约 §6.1.0）。</p>
     *
     * @return 当前全局默认模板（bootstrap 后保证存在）
     */
    @Transactional(rollbackFor = Exception.class)
    public MemorySeedTemplate bootstrapDefaultIfAbsent() {
        MemorySeedTemplate existing = findActiveDefault();
        if (Objects.nonNull(existing)) {
            return existing;
        }
        LOGGER.info("memory-seed: 默认模板缺失，从 classpath bootstrap default 模板");
        MemorySeedTemplate template = new MemorySeedTemplate();
        template.setCode(BUILTIN_DEFAULT_CODE);
        template.setName("平台默认 seed 模板");
        template.setDescription("classpath 内置 default seed 记忆模板");
        template.setVersion(BUILTIN_DEFAULT_VERSION);
        template.setStatus(MemorySeedTemplateStatus.ACTIVE);
        template.setIsDefault(Boolean.TRUE);
        template.setSourceType(MemorySeedTemplateSourceType.BUILTIN);
        template.setProjectMemoryTemplate(readSeedFile(BUILTIN_DEFAULT_CODE, FILE_PROJECT_MEMORY));
        template.setMemoryRulesTemplate(readSeedFile(BUILTIN_DEFAULT_CODE, FILE_MEMORY_RULES));
        template.setDecisionsTemplate(readSeedFile(BUILTIN_DEFAULT_CODE, FILE_DECISIONS));
        template.setModulesTemplate(readSeedFile(BUILTIN_DEFAULT_CODE, FILE_MODULES));
        template.setPublishedAt(new Date());
        // 固定主键：BaseEntityService.save 对预置主键会拒绝，首行走 persist
        template.setId(BUILTIN_DEFAULT_CODE + ":" + BUILTIN_DEFAULT_VERSION);
        entityManager.persist(template);
        return template;
    }

    /**
     * 查询当前 {@code ACTIVE + is_default=true} 默认模板。
     *
     * @return 默认模板；不存在返回 null
     */
    public MemorySeedTemplate findActiveDefault() {
        return dao.findActiveDefault(MemorySeedTemplateStatus.ACTIVE);
    }

    /**
     * 保存草稿。仅更新草稿态模板内容，不影响新项目。
     *
     * @param id    模板 id
     * @param draft 草稿内容载体（仅取四个模板字段与 name/description）
     * @return 保存后模板
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<MemorySeedTemplate> saveDraft(String id, MemorySeedTemplate draft) {
        MemorySeedTemplate template = dao.findOne(id);
        if (Objects.isNull(template)) {
            return OperateResultWithData.operationFailure("seed 模板不存在: " + id);
        }
        if (template.getStatus() != MemorySeedTemplateStatus.DRAFT
                && template.getStatus() != MemorySeedTemplateStatus.ACTIVE) {
            return OperateResultWithData.operationFailure("仅 DRAFT/ACTIVE 模板可保存草稿: " + template.getStatus());
        }
        applyDraftFields(template, draft);
        return OperateResultWithData.operationSuccessWithData(dao.save(template));
    }

    /**
     * 发布模板新版本：生成新行（ACTIVE、新 version），同 code 旧 ACTIVE 版本改 ARCHIVED。
     * 发布不自动改变全局默认模板（契约 §6.1.0、§17.1）。
     *
     * @param id 待发布的 DRAFT/ACTIVE 模板 id
     * @return 发布后的新版本模板
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<MemorySeedTemplate> publish(String id) {
        MemorySeedTemplate source = dao.findOne(id);
        if (Objects.isNull(source)) {
            return OperateResultWithData.operationFailure("seed 模板不存在: " + id);
        }
        int nextVersion = nextVersionForCode(source.getCode());
        // 发布前判断当前全局默认是否属于同 code：若被发布的就是当前默认模板，新版本必须延续 is_default=true，
        // 否则归档旧默认后系统将失去 ACTIVE+is_default=true 模板，违反不变式（契约 §6.1.0 / §17.1）。
        MemorySeedTemplate currentDefault = findActiveDefault();
        boolean publishDefault = Objects.nonNull(currentDefault)
                && Objects.equals(currentDefault.getCode(), source.getCode());
        // 先归档同 code 的旧 ACTIVE 版本（含当前默认）；归档时复位 isDefault，避免 ARCHIVED 行残留 true 误导语义
        archiveActiveVersionsByCode(source.getCode());
        MemorySeedTemplate published = cloneForPublish(source, nextVersion);
        published.setStatus(MemorySeedTemplateStatus.ACTIVE);
        published.setPublishedAt(new Date());
        published.setIsDefault(publishDefault);
        return OperateResultWithData.operationSuccessWithData(dao.save(published));
    }

    /**
     * 将指定 ACTIVE 模板设为全局默认：事务内取消原默认 isDefault，再置目标为默认。
     * 仅接受 ACTIVE 模板（契约 §17.1）。
     *
     * @param id 模板 id
     * @return 新默认模板
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<MemorySeedTemplate> setDefault(String id) {
        MemorySeedTemplate target = dao.findOne(id);
        if (Objects.isNull(target)) {
            return OperateResultWithData.operationFailure("seed 模板不存在: " + id);
        }
        if (target.getStatus() != MemorySeedTemplateStatus.ACTIVE) {
            return OperateResultWithData.operationFailure("仅 ACTIVE 模板可设为默认: " + target.getStatus());
        }
        MemorySeedTemplate current = findActiveDefault();
        if (Objects.nonNull(current) && Objects.equals(current.getId(), target.getId())) {
            return OperateResultWithData.operationSuccessWithData(target);
        }
        if (Objects.nonNull(current)) {
            current.setIsDefault(Boolean.FALSE);
            dao.save(current);
        }
        target.setIsDefault(Boolean.TRUE);
        return OperateResultWithData.operationSuccessWithData(dao.save(target));
    }

    /**
     * 归档模板。当前默认模板必须先通过 {@link #setDefault} 切换后才能归档（契约 §17.1）。
     *
     * @param id 模板 id
     * @return 归档后模板
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<MemorySeedTemplate> archive(String id) {
        MemorySeedTemplate template = dao.findOne(id);
        if (Objects.isNull(template)) {
            return OperateResultWithData.operationFailure("seed 模板不存在: " + id);
        }
        if (Boolean.TRUE.equals(template.getIsDefault())
                && template.getStatus() == MemorySeedTemplateStatus.ACTIVE) {
            return OperateResultWithData.operationFailure("当前默认模板不可直接归档，请先切换默认");
        }
        template.setStatus(MemorySeedTemplateStatus.ARCHIVED);
        template.setArchivedAt(new Date());
        return OperateResultWithData.operationSuccessWithData(dao.save(template));
    }

    /**
     * 解析项目使用的 seed 模板：优先项目已绑定模板（即使已归档仍可沿用补齐，契约 §6 现有实体修改 §9.1）；
     * 未绑定时回退当前全局默认模板。
     *
     * @param memorySeedTemplateId 项目已绑定的模板 id（可空）
     * @return 解析出的模板；项目已绑定 id 不存在或默认模板均缺失时返回 null
     */
    public MemorySeedTemplate resolveForProject(String memorySeedTemplateId) {
        if (Objects.nonNull(memorySeedTemplateId) && !memorySeedTemplateId.isBlank()) {
            return dao.findOne(memorySeedTemplateId);
        }
        return findActiveDefault();
    }

    /**
     * 覆盖默认 save 以保证业务入口走专用方法；直接 save 仍可用，但仅做基础落库。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<MemorySeedTemplate> save(MemorySeedTemplate entity) {
        // 新建草稿必须提供 code/name（迁移脚本要求 NOT NULL）；code 同版本唯一由 uk_memory_seed_template_code_version 兜底，
        // 这里给出可读业务错误而非 DB 异常。
        if (Objects.isNull(entity.getId()) || entity.getId().isBlank()) {
            if (Objects.isNull(entity.getCode()) || entity.getCode().isBlank()) {
                return OperateResultWithData.operationFailure("seed 模板 code 不能为空");
            }
            if (Objects.isNull(entity.getName()) || entity.getName().isBlank()) {
                return OperateResultWithData.operationFailure("seed 模板 name 不能为空");
            }
            int existingVersion = dao.findByCode(entity.getCode()).stream()
                    .mapToInt(MemorySeedTemplate::getVersion)
                    .max()
                    .orElse(0);
            if (Objects.isNull(entity.getVersion())) {
                entity.setVersion(existingVersion + 1);
            }
            if (dao.findByCode(entity.getCode()).stream()
                    .anyMatch(t -> Objects.equals(t.getVersion(), entity.getVersion()))) {
                return OperateResultWithData.operationFailure("seed 模板 code+version 已存在: "
                        + entity.getCode() + ":" + entity.getVersion());
            }
        }
        if (Objects.isNull(entity.getStatus())) {
            entity.setStatus(MemorySeedTemplateStatus.DRAFT);
        }
        if (Objects.isNull(entity.getIsDefault())) {
            entity.setIsDefault(Boolean.FALSE);
        }
        if (Objects.isNull(entity.getSourceType())) {
            entity.setSourceType(MemorySeedTemplateSourceType.USER_CONFIG);
        }
        if (Objects.isNull(entity.getVersion())) {
            entity.setVersion(1);
        }
        return super.save(entity);
    }

    /**
     * 取所有已发布 ACTIVE 模板，供项目选择。
     *
     * @return ACTIVE 模板列表（含默认）
     */
    public List<MemorySeedTemplate> findActiveTemplates() {
        return dao.findByStatus(MemorySeedTemplateStatus.ACTIVE);
    }

    // ============================ private ============================

    private int nextVersionForCode(String code) {
        List<MemorySeedTemplate> sameCode = dao.findByCode(code);
        return sameCode.stream()
                .mapToInt(MemorySeedTemplate::getVersion)
                .max()
                .orElse(0) + 1;
    }

    private void archiveActiveVersionsByCode(String code) {
        List<MemorySeedTemplate> sameCode = dao.findByCode(code);
        Date now = new Date();
        sameCode.stream()
                .filter(t -> t.getStatus() == MemorySeedTemplateStatus.ACTIVE)
                .forEach(t -> {
                    t.setStatus(MemorySeedTemplateStatus.ARCHIVED);
                    t.setArchivedAt(now);
                    // 归档时复位 isDefault：partial unique index 仅约束 ACTIVE+is_default=true，
                    // 但语义上 is_default 只应在 ACTIVE 行为 true，避免 ARCHIVED 行残留误导默认解析
                    t.setIsDefault(Boolean.FALSE);
                    dao.save(t);
                });
    }

    private MemorySeedTemplate cloneForPublish(MemorySeedTemplate source, int nextVersion) {
        MemorySeedTemplate published = new MemorySeedTemplate();
        published.setCode(source.getCode());
        published.setName(source.getName());
        published.setDescription(source.getDescription());
        published.setVersion(nextVersion);
        published.setSourceType(source.getSourceType());
        published.setProjectMemoryTemplate(source.getProjectMemoryTemplate());
        published.setMemoryRulesTemplate(source.getMemoryRulesTemplate());
        published.setDecisionsTemplate(source.getDecisionsTemplate());
        published.setModulesTemplate(source.getModulesTemplate());
        return published;
    }

    private void applyDraftFields(MemorySeedTemplate template, MemorySeedTemplate draft) {
        if (draft == null) {
            return;
        }
        if (Objects.nonNull(draft.getName())) {
            template.setName(draft.getName());
        }
        if (Objects.nonNull(draft.getDescription())) {
            template.setDescription(draft.getDescription());
        }
        if (Objects.nonNull(draft.getProjectMemoryTemplate())) {
            template.setProjectMemoryTemplate(draft.getProjectMemoryTemplate());
        }
        if (Objects.nonNull(draft.getMemoryRulesTemplate())) {
            template.setMemoryRulesTemplate(draft.getMemoryRulesTemplate());
        }
        if (Objects.nonNull(draft.getDecisionsTemplate())) {
            template.setDecisionsTemplate(draft.getDecisionsTemplate());
        }
        if (Objects.nonNull(draft.getModulesTemplate())) {
            template.setModulesTemplate(draft.getModulesTemplate());
        }
    }

    static String readSeedFile(String code, String fileName) {
        Resource resource = new ClassPathResource(BUILTIN_RESOURCE_BASE + fileName);
        if (!resource.exists()) {
            throw new IllegalStateException("classpath seed 模板文件缺失: " + code + "/" + fileName);
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 classpath seed 模板文件失败: " + code + "/" + fileName, e);
        }
    }
}