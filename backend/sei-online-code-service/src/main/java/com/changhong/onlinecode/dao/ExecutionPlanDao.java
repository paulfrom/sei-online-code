package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ExecutionPlan DAO。
 */
@Repository
public interface ExecutionPlanDao extends BaseEntityDao<ExecutionPlan> {

    List<ExecutionPlan> findByRequirementIdOrderByVersionAsc(String requirementId);

    ExecutionPlan findTopByRequirementIdOrderByVersionDesc(String requirementId);

    ExecutionPlan findTopByRequirementIdAndLoopIdOrderByVersionDesc(String requirementId, String loopId);

    long countByRequirementIdAndLoopId(String requirementId, String loopId);

    /** 同一 loop 下的所有计划版本（用于统计 REMEDIATION 计划数量）。 */
    List<ExecutionPlan> findByRequirementIdAndLoopId(String requirementId, String loopId);

    Optional<ExecutionPlan> findByRequirementIdAndLoopIdAndRevisionSeq(
            String requirementId, String loopId, Long revisionSeq);
}
