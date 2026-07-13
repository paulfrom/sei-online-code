package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ExecutionPlan DAO。
 */
@Repository
public interface ExecutionPlanDao extends BaseEntityDao<ExecutionPlan> {

    List<ExecutionPlan> findByRequirementIdOrderByVersionAsc(String requirementId);

    ExecutionPlan findTopByRequirementIdOrderByVersionDesc(String requirementId);

    ExecutionPlan findTopByRequirementIdAndLoopIdOrderByVersionDesc(String requirementId, String loopId);
}
