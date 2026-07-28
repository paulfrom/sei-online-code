package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.dto.enums.AgentBehaviorMemoryStatus;
import com.changhong.onlinecode.entity.AgentBehaviorMemory;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentBehaviorMemoryDao extends BaseEntityDao<AgentBehaviorMemory> {

    List<AgentBehaviorMemory> findByProjectIdAndStatusOrderByLastEditedDateDesc(
            String projectId, AgentBehaviorMemoryStatus status);

    List<AgentBehaviorMemory> findByProjectIdAndAgentNameAndStatusOrderByLastEditedDateDesc(
            String projectId, String agentName, AgentBehaviorMemoryStatus status);

    Optional<AgentBehaviorMemory> findFirstByProjectIdAndScopeKeyAndRuleText(
            String projectId, String scopeKey, String ruleText);
}
