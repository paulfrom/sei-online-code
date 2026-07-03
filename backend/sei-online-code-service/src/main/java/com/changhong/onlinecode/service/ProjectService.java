package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.PlanDao;
import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.entity.Plan;
import com.changhong.onlinecode.entity.Project;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.context.annotation.Lazy;
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
    private final PlanDao planDao;
    private final PlanAgentService planAgentService;

    public ProjectService(ProjectDao dao, PlanDao planDao, @Lazy PlanAgentService planAgentService) {
        this.dao = dao;
        this.planDao = planDao;
        this.planAgentService = planAgentService;
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
        boolean isNew = entity.getId() == null;
        if (Objects.isNull(entity.getState())) {
            entity.setState(LifecycleState.DRAFTING);
        }
        OperateResultWithData<Project> result = super.save(entity);
        if (result.successful() && isNew) {
            // T9b (P1, D2): 新项目持久化后触发规划——建初始 Plan 行(GENERATING, v1, isLatest) + spawn
            Plan plan = new Plan();
            plan.setProjectId(entity.getId());
            plan.setVersion(1);
            plan.setStatus(PlanStatus.GENERATING);
            plan.setIsLatest(true);
            planDao.save(plan);
            planAgentService.spawnPlanning(entity.getId(), null);
        }
        return result;
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
