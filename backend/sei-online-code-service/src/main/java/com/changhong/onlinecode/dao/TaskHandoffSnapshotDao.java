package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.TaskHandoffSnapshot;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 任务成果交接快照 DAO。
 */
@Repository
public interface TaskHandoffSnapshotDao extends BaseEntityDao<TaskHandoffSnapshot> {

    Optional<TaskHandoffSnapshot> findByCodingTaskIdAndRevisionSeq(String codingTaskId, Long revisionSeq);

    Optional<TaskHandoffSnapshot> findTopByCodingTaskIdOrderByRevisionSeqDesc(String codingTaskId);

    List<TaskHandoffSnapshot> findByRequirementIdAndRevisionSeqOrderByCreatedDateAsc(
            String requirementId, Long revisionSeq);
}
