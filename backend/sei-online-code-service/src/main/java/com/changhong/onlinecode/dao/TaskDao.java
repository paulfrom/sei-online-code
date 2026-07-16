package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Task;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

/**
 * Task DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * @author sei-online-code
 */
@Repository
public interface TaskDao extends BaseEntityDao<Task> {
}
