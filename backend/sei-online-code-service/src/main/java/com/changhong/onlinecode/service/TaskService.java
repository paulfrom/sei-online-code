package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.TaskDao;
import com.changhong.onlinecode.entity.Task;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Task 服务。查询与状态更新的入口，分派切分逻辑见 {@link DispatchService}。
 *
 * @author sei-online-code
 */
@Service
public class TaskService extends BaseEntityService<Task> {

    private final TaskDao dao;

    public TaskService(TaskDao dao) {
        this.dao = dao;
    }

    @Override
    protected BaseEntityDao<Task> getDao() {
        return dao;
    }

    /**
     * 按迭代 id 取任务列表（seq 升序，即分派/合并顺序）。
     *
     * @param iterationId 迭代 id
     * @return Task 列表
     */
    public List<Task> findByIteration(String iterationId) {
        return dao.findByIterationIdOrderBySeqAsc(iterationId);
    }
}
