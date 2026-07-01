package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Agent;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

/**
 * Agent DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * @author sei-online-code
 */
@Repository
public interface AgentDao extends BaseEntityDao<Agent> {

    /**
     * 按名称查询 agent（Task.assignedAgent 解析、内置种子 upsert 用）。
     *
     * @param name agent 名
     * @return 命中的 agent，未命中为 null
     */
    Agent findByName(String name);
}
