package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.AgentSkillDao;
import com.changhong.onlinecode.dao.SkillDao;
import com.changhong.onlinecode.dto.enums.SkillSourceType;
import com.changhong.onlinecode.entity.AgentSkill;
import com.changhong.onlinecode.entity.Skill;
import com.changhong.onlinecode.service.support.SkillHasher;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResult;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Skill 服务（B19）。契约 Phase 3 §2 端点 16/19、§6 hash-lock。
 *
 * <p>职责：导入并按 §6 recipe 计算 {@code computedHash} 锁定，按 hash 幂等（相同内容不建新行，
 * 返回既有行）；删除受保护——被任一 agent 绑定则拒绝（经 oc_agent_skill join 表单查询校验）。</p>
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
     * 导入技能：按 §6 recipe 计算内容锁，按 hash 幂等去重。
     *
     * <p>相同 (source|name|description|content) → 相同 hash → 命中既有行直接返回，不新增。</p>
     *
     * @param name        技能名（唯一，materialize 为目录名）
     * @param description 技能描述
     * @param source      导入来源
     * @param sourceType  来源类型
     * @param content     SKILL.md 正文
     * @return 写操作结果（携带导入/命中的技能）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Skill> importSkill(String name, String description, String source,
                                                    SkillSourceType sourceType, String content) {
        String computedHash = SkillHasher.compute(source, name, description, content);

        // 幂等：内容锁命中即返回既有行，不建新行
        Skill existing = dao.findByComputedHash(computedHash);
        if (Objects.nonNull(existing)) {
            return OperateResultWithData.operationSuccessWithData(existing);
        }

        // name 唯一：同名但内容不同视为冲突，拒绝（materialize 目录名冲突）
        Skill sameName = dao.findByName(name);
        if (Objects.nonNull(sameName)) {
            return OperateResultWithData.operationFailure("技能名已存在但内容不同，拒绝导入: " + name);
        }

        Skill skill = new Skill();
        skill.setName(name);
        skill.setDescription(description);
        skill.setSource(source);
        skill.setSourceType(sourceType);
        skill.setContent(content);
        skill.setComputedHash(computedHash);
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
