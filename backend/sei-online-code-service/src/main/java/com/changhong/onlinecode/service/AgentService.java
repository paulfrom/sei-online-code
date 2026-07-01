package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.AgentDao;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResult;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Agent 服务（B20）。契约 Phase 3 §2 端点 20/23/24、§4 内置守卫。
 *
 * <p>职责：CRUD（save 继承自 BaseEntityService）；内置 agent 删除守卫（builtin=true 拒绝删除）；
 * 绑定技能整体替换（ep #24）；按名称解析（供 DispatchService 解析 Task.assignedAgent）。
 * 三个内置 agent 由 Flyway V3 种子写入（契约 §4）。</p>
 *
 * @author sei-online-code
 */
@Service
public class AgentService extends BaseEntityService<Agent> {

    private final AgentDao dao;

    public AgentService(AgentDao dao) {
        this.dao = dao;
    }

    @Override
    protected BaseEntityDao<Agent> getDao() {
        return dao;
    }

    /**
     * 附加/替换 agent 绑定的技能 id 列表（ep #24，整体替换）。
     *
     * @param agentId  agent id
     * @param skillIds 待绑定技能 id 列表
     * @return 写操作结果（携带更新后的 agent）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Agent> attachSkills(String agentId, List<String> skillIds) {
        Agent agent = findOne(agentId);
        if (Objects.isNull(agent)) {
            return OperateResultWithData.operationFailure("agent 不存在: " + agentId);
        }
        agent.setSkillIds(skillIds);
        return super.save(agent);
    }

    /**
     * 按名称解析 agent（供 DispatchService 解析 Task.assignedAgent）。
     *
     * @param name agent 名
     * @return 命中的 agent，未命中为 null
     */
    public Agent findByName(String name) {
        return dao.findByName(name);
    }

    /**
     * 删除前置校验：内置 agent（builtin=true）拒绝删除（契约 §2 端点 23）。
     *
     * @param id agent id
     * @return 校验结果
     */
    @Override
    protected OperateResult preDelete(String id) {
        Agent agent = findOne(id);
        if (Objects.nonNull(agent) && Boolean.TRUE.equals(agent.getBuiltin())) {
            return OperateResult.operationFailure("内置 agent 不能删除: " + agent.getName());
        }
        return OperateResult.operationSuccess();
    }
}
