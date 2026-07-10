package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
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
    private final PlanService planService;
    private final ProjectLifecycleService lifecycleService;
    private final WorkspaceManager workspaceManager;

    public ProjectService(ProjectDao dao,
                          PlanService planService,
                          ProjectLifecycleService lifecycleService,
                          WorkspaceManager workspaceManager) {
        this.dao = dao;
        this.planService = planService;
        this.lifecycleService = lifecycleService;
        this.workspaceManager = workspaceManager;
    }

    @Override
    protected BaseEntityDao<Project> getDao() {
        return dao;
    }

    /**
     * 新建/更新项目。
     *
     * <p>项目保存后立即解析并 provision 物理工作区，确保从项目创建时起就有稳定的落盘目录；
     * 后续代码执行、Agent brief、运行物料都落在同一 workspace。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Project> save(Project entity) {
        if (Objects.isNull(entity.getState())) {
            entity.setState(LifecycleState.DRAFTING);
        }
        if (Objects.isNull(entity.getAutoRunCodingTask())) {
            entity.setAutoRunCodingTask(Boolean.FALSE);
        }
        OperateResultWithData<Project> result = super.save(entity);
        if (!result.successful() || result.getData() == null || result.getData().getId() == null) {
            return result;
        }
        Project saved = result.getData();
        WorkspaceResolveResult workspace = workspaceManager.resolve(saved.getId());
        if (!Objects.equals(saved.getWorkspacePath(), workspace.getPath())) {
            saved.setWorkspacePath(workspace.getPath());
            result = super.save(saved);
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
        return lifecycleService.transitionState(projectId, target);
    }

    /**
     * 兼容旧 refineSpec 入口：实际触发的是 Plan 重生成，而不是旧 Spec 流程。
     *
     * <p>仅用于兼容旧客户端；新的需求驱动流程应走 RequirementWorkspace 下的 PRD/设计链路。</p>
     *
     * @param projectId 项目 id
     * @return 写操作结果（携带新建 GENERATING Plan）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<PlanDto> refineSpec(String projectId) {
        Project project = dao.findOne(projectId);
        if (Objects.isNull(project)) {
            return OperateResultWithData.operationFailure("项目不存在: " + projectId);
        }
        return planService.regenerate(projectId, null);
    }
}
