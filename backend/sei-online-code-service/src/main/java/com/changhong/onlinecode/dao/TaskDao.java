package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Task;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Task DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * @author sei-online-code
 */
@Repository
public interface TaskDao extends BaseEntityDao<Task> {

    /**
     * 按迭代 id 查询任务，seq 升序（分派/合并顺序）。
     *
     * @param iterationId 迭代 id
     * @return Task 列表
     */
    List<Task> findByIterationIdOrderBySeqAsc(String iterationId);
}
