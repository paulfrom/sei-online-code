package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.entity.Run;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Run 服务。承载运行记录的查询与状态更新（由 {@link DispatchService} 并行 fan-out 驱动）。
 *
 * @author sei-online-code
 */
@Service
public class RunService extends BaseEntityService<Run> {

    private final RunDao dao;

    public RunService(RunDao dao) {
        this.dao = dao;
    }

    @Override
    protected BaseEntityDao<Run> getDao() {
        return dao;
    }

    /**
     * 按迭代 id 取运行记录（取消级联用）。
     *
     * @param iterationId 迭代 id
     * @return Run 列表
     */
    public List<Run> findByIteration(String iterationId) {
        return dao.findByIterationId(iterationId);
    }
}
