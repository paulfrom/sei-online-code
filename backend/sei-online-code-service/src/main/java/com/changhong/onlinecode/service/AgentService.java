package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.AgentDao;
import com.changhong.onlinecode.dao.AgentSkillDao;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.AgentSkill;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResult;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Agent 服务（B20）。契约 Phase 3 §2 端点 20/21/22/23/24、§4 内置守卫。
 *
 * <p>职责：CRUD（save 继承自 BaseEntityService）；内置 agent 删除守卫（builtin=true 拒绝删除）；
 * 绑定技能整体替换（ep #24，经 oc_agent_skill join 表）；按名称解析供运行编排选择
 * Task.assignedAgent。三个内置 agent 由 Flyway V3/V6 种子写入（契约 §4）。</p>
 *
 * <p>skillIds 派生：{@link Agent#getSkillIds()} 为 @Transient，由本服务从 oc_agent_skill
 * populate；findOne/findByPage/save/findByName 均在返回前 populate，保证 AgentDto 内联
 * skillIds[] 契约不变（对齐 multica 维度 a 的"前端零改动"决策）。</p>
 *
 * @author sei-online-code
 */
@Service
public class AgentService extends BaseEntityService<Agent> {

    private final AgentDao dao;
    private final AgentSkillDao agentSkillDao;

    public AgentService(AgentDao dao, AgentSkillDao agentSkillDao) {
        this.dao = dao;
        this.agentSkillDao = agentSkillDao;
    }

    @Override
    protected BaseEntityDao<Agent> getDao() {
        return dao;
    }

    @Override
    public Agent findOne(String id) {
        Agent agent = super.findOne(id);
        populateSkillIds(agent);
        return agent;
    }

    @Override
    public PageResult<Agent> findByPage(Search search) {
        PageResult<Agent> page = super.findByPage(search);
        populateSkillIds(page.getRows());
        return page;
    }

    @Override
    public OperateResultWithData<Agent> save(Agent agent) {
        OperateResultWithData<Agent> result = super.save(agent);
        if (result != null && result.getData() != null) {
            populateSkillIds(result.getData());
        }
        return result;
    }

    /**
     * 按名称解析 agent（供运行编排解析 Task.assignedAgent；同步 populate skillIds）。
     *
     * @param name agent 名
     * @return 命中的 agent（skillIds 已 populate），未命中为 null
     */
    public Agent findByName(String name) {
        Agent agent = dao.findByName(name);
        populateSkillIds(agent);
        return agent;
    }

    /**
     * 附加/替换 agent 绑定的技能 id 列表（ep #24，整体替换，oc_agent_skill join 表）。
     *
     * <p>整体替换语义：先 {@code deleteByAgentId} 清旧，再逐条 insert 新。skill_id 存在性校验
     * 延后到 Phase 6（{@code builtin:<name>} 经 BuiltInSkillRegistry 校验、DB id 经 SkillDao
     * 校验）；本阶段仅持久化绑定关系。</p>
     *
     * @param agentId  agent id
     * @param skillIds 待绑定技能 id 列表（null/空表示解绑全部）
     * @return 写操作结果（携带 agent，skillIds 已回填为新列表）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Agent> attachSkills(String agentId, List<String> skillIds) {
        Agent agent = dao.findById(agentId).orElse(null);
        if (Objects.isNull(agent)) {
            return OperateResultWithData.operationFailure("agent 不存在: " + agentId);
        }
        agentSkillDao.deleteByAgentId(agentId);
        List<String> normalized = skillIds == null ? List.of()
                : skillIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        for (String skillId : normalized) {
            AgentSkill row = new AgentSkill();
            row.setAgentId(agentId);
            row.setSkillId(skillId);
            agentSkillDao.save(row);
        }
        agent.setSkillIds(normalized);
        return OperateResultWithData.operationSuccessWithData(agent);
    }

    @Override
    protected OperateResult preDelete(String id) {
        Agent agent = findOne(id);
        if (Objects.nonNull(agent) && Boolean.TRUE.equals(agent.getBuiltin())) {
            return OperateResult.operationFailure("内置 agent 不能删除: " + agent.getName());
        }
        return OperateResult.operationSuccess();
    }

    /** 单个 agent populate skillIds（从 oc_agent_skill）。 */
    private void populateSkillIds(Agent agent) {
        if (agent == null || agent.getId() == null) {
            return;
        }
        List<String> ids = agentSkillDao.findByAgentId(agent.getId()).stream()
                .map(AgentSkill::getSkillId)
                .collect(Collectors.toList());
        agent.setSkillIds(ids);
    }

    /** 批量 populate（findByPage 用，单次 IN 查询避免 N+1）。 */
    private void populateSkillIds(List<Agent> agents) {
        if (agents == null || agents.isEmpty()) {
            return;
        }
        List<String> agentIds = agents.stream()
                .map(Agent::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (agentIds.isEmpty()) {
            return;
        }
        List<AgentSkill> rows = agentSkillDao.findByAgentIdIn(agentIds);
        Map<String, List<String>> byAgent = rows.stream()
                .collect(Collectors.groupingBy(AgentSkill::getAgentId,
                        Collectors.mapping(AgentSkill::getSkillId, Collectors.toList())));
        for (Agent agent : agents) {
            agent.setSkillIds(byAgent.getOrDefault(agent.getId(), List.of()));
        }
    }
}
