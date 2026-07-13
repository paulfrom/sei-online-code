package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * CodingTask DAO。
 *
 * @author sei-online-code
 */
@Repository
public interface CodingTaskDao extends BaseEntityDao<CodingTask> {

    /**
     * 按需求 ID 查询所有编码任务。
     *
     * @param requirementId 需求 ID
     * @return 编码任务列表
     */
    List<CodingTask> findByRequirementId(String requirementId);

    /**
     * 按状态查询编码任务。
     *
     * @param status 状态
     * @return 编码任务列表
     */
    List<CodingTask> findByStatus(com.changhong.onlinecode.dto.enums.CodingTaskStatus status);

    /**
     * 按需求 ID、loop id、任务 key 查询唯一任务（用于 remediation 时复用旧任务）。
     */
    CodingTask findByRequirementIdAndLoopIdAndPlanTaskKey(String requirementId, String loopId, String planTaskKey);

    /**
     * 仅当任务仍处于期望状态时切换状态，用于补偿/执行抢占。
     *
     * @param id 任务 ID
     * @param expected 期望旧状态
     * @param target 目标状态
     * @return 更新条数
     */
    @Modifying
    @Query("UPDATE CodingTask t SET t.status = :target, t.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE t.id = :id AND t.status = :expected")
    int updateStatusIfMatch(@Param("id") String id,
                            @Param("expected") com.changhong.onlinecode.dto.enums.CodingTaskStatus expected,
                            @Param("target") com.changhong.onlinecode.dto.enums.CodingTaskStatus target);
}
