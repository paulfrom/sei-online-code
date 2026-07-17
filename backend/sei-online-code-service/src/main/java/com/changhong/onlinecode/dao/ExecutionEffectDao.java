package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.dto.enums.ExecutionEffectStatus;
import com.changhong.onlinecode.entity.ExecutionEffect;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ExecutionEffect DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * <p>effect_key 唯一：相同 key+requestHash 返回首次结果；key 相同 hash 不同稳定冲突，不覆盖原请求。</p>
 *
 * @author sei-online-code
 */
@Repository
public interface ExecutionEffectDao extends BaseEntityDao<ExecutionEffect> {

    /**
     * 按 effect_key 唯一查找。
     *
     * @param effectKey effect 键
     * @return effect（存在时）
     */
    Optional<ExecutionEffect> findByEffectKey(String effectKey);

    /**
     * 某 Execution 某状态下的 effect 列表。
     *
     * @param executionId Execution ID
     * @param status      effect 状态
     * @return effect 列表
     */
    List<ExecutionEffect> findByExecutionIdAndStatus(String executionId, ExecutionEffectStatus status);
}
