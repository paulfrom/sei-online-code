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
}
