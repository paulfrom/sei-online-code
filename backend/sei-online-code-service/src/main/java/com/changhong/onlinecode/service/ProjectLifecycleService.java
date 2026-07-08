package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.entity.Project;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 项目生命周期流转服务。
 *
 * <p>职责单一：项目状态机转换。不依赖规划/详细设计等服务，避免与上层编排服务形成循环依赖。</p>
 *
 * @author sei-online-code
 */
@Service
public class ProjectLifecycleService {

    private final ProjectDao projectDao;

    public ProjectLifecycleService(ProjectDao projectDao) {
        this.projectDao = projectDao;
    }

    /**
     * 按 id 查找项目。
     *
     * @param projectId 项目 id
     * @return 项目实体，不存在时返回 null
     */
    public Project findById(String projectId) {
        return projectDao.findOne(projectId);
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
        Project project = projectDao.findOne(projectId);
        if (Objects.isNull(project)) {
            return OperateResultWithData.operationFailure("项目不存在: " + projectId);
        }
        if (!LifecycleTransitions.canTransition(project.getState(), target)) {
            return OperateResultWithData.operationFailure(
                    "非法状态流转: " + project.getState() + " → " + target);
        }
        project.setState(target);
        return OperateResultWithData.operationSuccessWithData(projectDao.save(project));
    }
}
