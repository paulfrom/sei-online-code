package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.TaskDao;
import com.changhong.onlinecode.entity.Task;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Task 服务。查询与状态更新的入口；FeatureDesign 编码运行仍使用 Task 作为运行记录。
 *
 * @author sei-online-code
 */
@Service
@AllArgsConstructor
public class TaskService extends BaseEntityService<Task> {

    private final TaskDao dao;

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
