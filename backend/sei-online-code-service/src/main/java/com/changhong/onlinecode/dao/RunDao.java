package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Run;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Run DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * @author sei-online-code
 */
@Repository
public interface RunDao extends BaseEntityDao<Run> {

    /**
     * 按迭代 id 查询运行记录（取消级联用）。
     *
     * @param iterationId 迭代 id
     * @return Run 列表
     */
    List<Run> findByIterationId(String iterationId);

    List<Run> findByTaskId(String taskId);

    /**
     * 按编码任务 ID 查询运行记录。
     *
     * @param codingTaskId 编码任务 ID
     * @return Run 列表
     */
    List<Run> findByCodingTaskId(String codingTaskId);

    /**
     * 按需求查询全部运行记录，包含未绑定编码任务的 PM、计划级验证和交付 Run。
     *
     * @param requirementId 需求 ID
     * @return 按创建时间倒序排列的 Run 列表
     */
    List<Run> findByRequirementIdOrderByCreatedDateDesc(String requirementId);

    /**
     * 按运行状态查询运行记录。
     *
     * @param state 运行状态
     * @return Run 列表
     */
    List<Run> findByState(com.changhong.onlinecode.dto.enums.RunState state);

    List<Run> findByRequirementIdAndState(String requirementId,
                                          com.changhong.onlinecode.dto.enums.RunState state);

    List<Run> findByRequirementIdAndLoopId(String requirementId, String loopId);

    /**
     * 仅当运行仍处于期望状态时切换状态，用于终态保护。
     *
     * @param id 运行 ID
     * @param expected 期望旧状态
     * @param target 目标状态
     * @return 更新条数
     */
    @Modifying
    @Query("UPDATE Run r SET r.state = :target, r.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE r.id = :id AND r.state = :expected")
    int updateStateIfMatch(@Param("id") String id,
                           @Param("expected") com.changhong.onlinecode.dto.enums.RunState expected,
                           @Param("target") com.changhong.onlinecode.dto.enums.RunState target);
}
