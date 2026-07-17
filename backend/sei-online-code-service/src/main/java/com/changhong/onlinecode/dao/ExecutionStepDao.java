package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.ExecutionStep;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ExecutionStep DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * <p>同 Execution 计划版本内 (executionId, stepKey, planVersion) 唯一；冲突后用唯一查找回读。</p>
 *
 * @author sei-online-code
 */
@Repository
public interface ExecutionStepDao extends BaseEntityDao<ExecutionStep> {

    /**
     * 按 Execution + stepKey + planVersion 唯一查找。
     *
     * @param executionId Execution ID
     * @param stepKey     步骤键
     * @param planVersion 计划版本
     * @return 步骤（存在时）
     */
    Optional<ExecutionStep> findByExecutionIdAndStepKeyAndPlanVersion(String executionId, String stepKey, Integer planVersion);

    /**
     * 某 Execution 某计划版本下的全部步骤。
     *
     * @param executionId Execution ID
     * @param planVersion 计划版本
     * @return 步骤列表
     */
    List<ExecutionStep> findByExecutionIdAndPlanVersion(String executionId, Integer planVersion);
}
