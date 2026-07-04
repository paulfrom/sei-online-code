package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.AgentSkillDao;
import com.changhong.onlinecode.dao.SkillDao;
import com.changhong.onlinecode.dto.enums.SkillSourceType;
import com.changhong.onlinecode.entity.AgentSkill;
import com.changhong.onlinecode.entity.Skill;
import com.changhong.onlinecode.exception.ConflictException;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResult;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

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

    public SkillService(SkillDao dao, AgentSkillDao agentSkillDao) {
        this.dao = dao;
        this.agentSkillDao = agentSkillDao;
    }

    @Override
    protected BaseEntityDao<Skill> getDao() {
        return dao;
    }

    /**
     * 导入技能：以 name 为去重键，同名已存在则 409 拒绝，否则 insert。
     *
     * <p>Phase 3 起弃 hash 去重——materialize 目录名以 {@code name} 为准，同名必然冲突，
     * 故同名（无论内容是否相同）直接拒绝。{@code computedHash} 不再持久化，由实体运行时计算。</p>
     *
     * @param name        技能名（唯一，materialize 为目录名）
     * @param description 技能描述
     * @param source      导入来源
     * @param sourceType  来源类型
     * @param content     SKILL.md 正文
     * @return 写操作结果（携带导入的技能）
     * @throws ConflictException 同名技能已存在（HTTP 409）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Skill> importSkill(String name, String description, String source,
                                                    SkillSourceType sourceType, String content) {
        // name 去重：同名已存在直接 409（materialize 目录名冲突）
        Skill existing = dao.findByName(name);
        if (Objects.nonNull(existing)) {
            throw new ConflictException("技能名已存在: " + name + " (id=" + existing.getId() + ")");
        }

        Skill skill = new Skill();
        skill.setName(name);
        skill.setDescription(description);
        skill.setSource(source);
        skill.setSourceType(sourceType);
        skill.setContent(content);
        return super.save(skill);
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
