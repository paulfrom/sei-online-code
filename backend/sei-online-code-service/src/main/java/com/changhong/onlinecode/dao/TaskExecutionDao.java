package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.TaskExecution;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// (paged/derived queries below)

/**
 * TaskExecution DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * <p>execution_key 唯一：重复 Run 共享同一 Execution；并发 insert 冲突后用 findByExecutionKey 回读。</p>
 *
 * @author sei-online-code
 */
@Repository
public interface TaskExecutionDao extends BaseEntityDao<TaskExecution> {

    /**
     * 按 execution_key 唯一查找。
     *
     * @param executionKey 逻辑执行键
     * @return Execution（存在时）
     */
    Optional<TaskExecution> findByExecutionKey(String executionKey);

    /**
     * 按完成收口幂等键查找（settlement_key 非空时唯一）。
     *
     * @param settlementKey 收口键
     * @return Execution（存在时）
     */
    Optional<TaskExecution> findBySettlementKey(String settlementKey);

    /**
     * 某需求最近一次 Execution（按创建时间倒序）。
     */
    Optional<TaskExecution> findFirstByRequirementIdOrderByCreatedDateDesc(String requirementId);
}
