package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dto.ExecutionPlanDto;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ExecutionPlan 服务。
 */
@Service
public class ExecutionPlanService extends BaseEntityService<ExecutionPlan> {

    private final ExecutionPlanDao dao;

    public ExecutionPlanService(ExecutionPlanDao dao) {
        this.dao = dao;
    }

    @Override
    protected BaseEntityDao<ExecutionPlan> getDao() {
        return dao;
    }

    public List<ExecutionPlan> findByRequirementId(String requirementId) {
        return dao.findByRequirementIdOrderByVersionAsc(requirementId);
    }

    public ExecutionPlan findLatestByRequirementId(String requirementId) {
        return dao.findTopByRequirementIdOrderByVersionDesc(requirementId);
    }

    public ExecutionPlan findLatestByRequirementIdAndLoopId(String requirementId, String loopId) {
        return dao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(requirementId, loopId);
    }

    public ExecutionPlanDto convertToDto(ExecutionPlan plan) {
        if (plan == null) {
            return null;
        }
        ExecutionPlanDto dto = new ExecutionPlanDto();
        dto.setId(plan.getId());
        dto.setRequirementId(plan.getRequirementId());
        dto.setLoopId(plan.getLoopId());
        dto.setVersion(plan.getVersion());
        dto.setPlanType(plan.getPlanType());
        dto.setStatus(plan.getStatus());
        dto.setPlanJson(plan.getPlanJson());
        dto.setSummary(plan.getSummary());
        dto.setCreatedByAgent(plan.getCreatedByAgent());
        dto.setMemoryContextId(plan.getMemoryContextId());
        dto.setWorkspaceMemoryId(plan.getWorkspaceMemoryId());
        dto.setCreatedDate(plan.getCreatedDate());
        return dto;
    }
}
