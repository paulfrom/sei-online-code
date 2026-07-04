package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.AgentSkill;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Agent↔Skill 关联 DAO。Spring Data 派生查询。
 *
 * @author sei-online-code
 */
@Repository
public interface AgentSkillDao extends BaseEntityDao<AgentSkill> {

    /** 按 agent 查全部绑定（AgentService.populateSkillIds / attachSkills 用）。 */
    List<AgentSkill> findByAgentId(String agentId);

    /** 批量按 agent 查（findByPage 列表 populate 用，单次 IN 查询避免 N+1）。 */
    List<AgentSkill> findByAgentIdIn(Collection<String> agentIds);

    /** 按 skill 查绑定（SkillService.preDelete 删除前置校验用，取代全表扫描）。 */
    List<AgentSkill> findBySkillId(String skillId);

    /** 整体替换第一步：删 agent 的全部旧绑定。 */
    void deleteByAgentId(String agentId);
}
