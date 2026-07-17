package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.RunObservation;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RunObservation DAO。observation 只允许 INSERT；分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * @author sei-online-code
 */
@Repository
public interface RunObservationDao extends BaseEntityDao<RunObservation> {

    /**
     * 取 Run 内 sequence_no 最大的 observation，用于追加分配下一个序号。
     *
     * @param runId Run ID
     * @return 最新 observation（存在时）
     */
    Optional<RunObservation> findTopByRunIdOrderBySequenceNoDesc(String runId);

    /**
     * 某 Run 的 observation 列表，按序号倒序（最近在前）。
     *
     * @param runId Run ID
     * @return observation 列表
     */
    List<RunObservation> findByRunIdOrderBySequenceNoDesc(String runId);
}
