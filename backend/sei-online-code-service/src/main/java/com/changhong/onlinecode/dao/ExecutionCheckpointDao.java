package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.ExecutionCheckpoint;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ExecutionCheckpoint DAO。journal 只允许 INSERT；分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * @author sei-online-code
 */
@Repository
public interface ExecutionCheckpointDao extends BaseEntityDao<ExecutionCheckpoint> {

    /**
     * 取 Execution 内 sequence_no 最大的 checkpoint，用于追加分配下一个序号。
     *
     * @param executionId Execution ID
     * @return 最新 checkpoint（存在时）
     */
    Optional<ExecutionCheckpoint> findTopByExecutionIdOrderBySequenceNoDesc(String executionId);

    /**
     * 某 Run 的 checkpoint 列表，按序号升序。
     *
     * @param runId Run ID
     * @return checkpoint 列表
     */
    List<ExecutionCheckpoint> findByRunIdOrderBySequenceNoAsc(String runId);
}
