package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Run;
import com.changhong.sei.core.dao.BaseEntityDao;
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

    /**
     * 按编码任务 ID 查询运行记录。
     *
     * @param codingTaskId 编码任务 ID
     * @return Run 列表
     */
    List<Run> findByCodingTaskId(String codingTaskId);

    /**
     * 按运行状态查询运行记录。
     *
     * @param state 运行状态
     * @return Run 列表
     */
    List<Run> findByState(com.changhong.onlinecode.dto.enums.RunState state);
}
