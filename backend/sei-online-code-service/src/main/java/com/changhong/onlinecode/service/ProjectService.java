package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.entity.Project;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 项目服务。生命周期状态机转换作为 service 方法（契约 §4）。
 *
 * @author sei-online-code
 */
@Service
public class ProjectService extends BaseEntityService<Project> {

    private final ProjectDao dao;

    public ProjectService(ProjectDao dao) {
        this.dao = dao;
    }

    @Override
    protected BaseEntityDao<Project> getDao() {
        return dao;
    }

    /**
     * 新建项目：初始状态置为 DRAFTING（契约 §3 端点 1）。
     *
     * @param entity 项目实体
     * @return 写操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Project> save(Project entity) {
        if (Objects.isNull(entity.getState())) {
            entity.setState(LifecycleState.DRAFTING);
        }
        return super.save(entity);
    }

    /**
     * 生命周期状态流转。校验合法后落库。
     *
     * @param projectId 项目 id
     * @param target    目标状态
     * @return 写操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Project> transitionState(String projectId, LifecycleState target) {
        Project project = dao.findOne(projectId);
        if (Objects.isNull(project)) {
            return OperateResultWithData.operationFailure("项目不存在: " + projectId);
        }
        if (!LifecycleTransitions.canTransition(project.getState(), target)) {
            return OperateResultWithData.operationFailure(
                    "非法状态流转: " + project.getState() + " → " + target);
        }
        project.setState(target);
        return super.save(project);
    }
}
