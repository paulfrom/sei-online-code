package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.AgentSkillDao;
import com.changhong.onlinecode.dao.SkillDao;
import com.changhong.onlinecode.dao.SkillFileDao;
import com.changhong.onlinecode.dto.skill.SkillConfig;
import com.changhong.onlinecode.dto.skill.SkillFileDto;
import com.changhong.onlinecode.entity.AgentSkill;
import com.changhong.onlinecode.entity.Skill;
import com.changhong.onlinecode.entity.SkillFile;
import com.changhong.onlinecode.exception.ConflictException;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResult;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Skill 服务（B19）。契约 Phase 3 §2 端点 16/19。
 *
 * <p>职责：导入技能，以 {@code name} 为去重键——同名已存在则抛 {@link ConflictException}（409），
 * 否则 insert。Phase 3 起弃持久化 hash，{@code computedHash} 改运行时计算（见 {@link Skill#getComputedHash()}），
 * 不再按 hash 幂等。删除受保护——被任一 agent 绑定则拒绝（经 oc_agent_skill join 表单查询校验）。</p>
 *
 * @author sei-online-code
 */
@Service
public class SkillService extends BaseEntityService<Skill> {

    private final SkillDao dao;
    private final AgentSkillDao agentSkillDao;
    private final SkillFileDao skillFileDao;

    public SkillService(SkillDao dao, AgentSkillDao agentSkillDao, SkillFileDao skillFileDao) {
        this.dao = dao;
        this.agentSkillDao = agentSkillDao;
        this.skillFileDao = skillFileDao;
    }

    @Override
    protected BaseEntityDao<Skill> getDao() {
        return dao;
    }

    /**
     * 导入技能：以 name 为去重键，同名已存在则 409 拒绝，否则 insert。
     *
     * <p>Phase 3 起弃 hash 去重——materialize 目录名以 {@code name} 为准，同名必然冲突，
     * 故同名（无论内容是否相同）直接拒绝。{@code computedHash} 不再持久化，由实体运行时计算。
     * Phase 4 起来源信息改入 {@code config}（multica 维度 d）。</p>
     *
     * @param name        技能名（唯一，materialize 为目录名）
     * @param description 技能描述
     * @param config      技能配置（承载来源 origin）
     * @param content     SKILL.md 正文
     * @param files       辅助文件列表（可空；Phase 5 / multica 维度 e，随 SKILL.md 一并 materialize）
     * @return 写操作结果（携带导入的技能，files 已回填）
     * @throws ConflictException 同名技能已存在（HTTP 409）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Skill> importSkill(String name, String description, SkillConfig config,
                                                    String content, List<SkillFileDto> files) {
        // name 去重：同名已存在直接 409（materialize 目录名冲突）
        Skill existing = dao.findByName(name);
        if (Objects.nonNull(existing)) {
            throw new ConflictException("技能名已存在: " + name + " (id=" + existing.getId() + ")");
        }

        Skill skill = new Skill();
        skill.setName(name);
        skill.setDescription(description);
        skill.setConfig(config);
        skill.setContent(content);
        OperateResultWithData<Skill> result = super.save(skill);
        if (result.notSuccessful()) {
            return result;
        }

        // 持久化辅助文件（path 已由 ImportSkillRequest @Valid 级联校验）
        List<SkillFile> fileEntities = persistFiles(result.getData().getId(), files);
        result.getData().setFiles(fileEntities);
        return result;
    }

    /**
     * 持久化技能辅助文件。null/空 → 返回空列表（不调 DAO）。
     *
     * @param skillId 所属技能 id
     * @param files   辅助文件 DTO（path 已校验）
     * @return 已持久化的实体列表
     */
    private List<SkillFile> persistFiles(String skillId, List<SkillFileDto> files) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }
        List<SkillFile> entities = files.stream()
                .filter(Objects::nonNull)
                .map(f -> {
                    SkillFile sf = new SkillFile();
                    sf.setSkillId(skillId);
                    sf.setPath(f.getPath());
                    sf.setContent(f.getContent());
                    return sf;
                })
                .collect(Collectors.toList());
        if (entities.isEmpty()) {
            return entities;
        }
        return skillFileDao.saveAll(entities);
    }

    /**
     * 单个 skill populate files（从 oc_skill_file）。findOne 返回前调用，保证 SkillDto.files[] 契约。
     */
    @Override
    public Skill findOne(String id) {
        Skill skill = super.findOne(id);
        populateFiles(skill);
        return skill;
    }

    /**
     * 分页 populate files（单次 IN 查询避免 N+1）。findByPage 返回前调用。
     */
    @Override
    public PageResult<Skill> findByPage(Search search) {
        PageResult<Skill> page = super.findByPage(search);
        populateFiles(page.getRows());
        return page;
    }

    private void populateFiles(Skill skill) {
        if (skill == null) {
            return;
        }
        skill.setFiles(skillFileDao.findBySkillId(skill.getId()));
    }

    private void populateFiles(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return;
        }
        List<String> ids = skills.stream()
                .map(Skill::getId).filter(Objects::nonNull).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        Map<String, List<SkillFile>> bySkill = skillFileDao.findBySkillIdIn(ids).stream()
                .collect(Collectors.groupingBy(SkillFile::getSkillId));
        for (Skill skill : skills) {
            skill.setFiles(bySkill.getOrDefault(skill.getId(), new ArrayList<>()));
        }
    }

    /**
     * 删除前置校验：被任一 agent 绑定则拒绝删除（契约 §2 端点 19）。
     *
     * <p>对齐 multica 维度 a：经 oc_agent_skill 单查询校验，取代原全表扫描 agent.skillIds。</p>
     *
     * @param id 技能 id
     * @return 校验结果
     */
    @Override
    protected OperateResult preDelete(String id) {
        List<AgentSkill> bindings = agentSkillDao.findBySkillId(id);
        if (!bindings.isEmpty()) {
            return OperateResult.operationFailure("技能已绑定到 agent，不能删除 (skillId=" + id + ")");
        }
        return OperateResult.operationSuccess();
    }
}
