package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.entity.Project;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
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
    private final ConfigService configService;
    private final ProjectLifecycleService lifecycleService;

    public ProjectService(ProjectDao dao,
                          PlanService planService,
                          ConfigService configService,
                          ProjectLifecycleService lifecycleService) {
        this.dao = dao;
        this.planService = planService;
        this.configService = configService;
        this.lifecycleService = lifecycleService;
    }

    @Override
    protected BaseEntityDao<Project> getDao() {
        return dao;
    }

    /**
     * 新建项目：仅保存元数据，不再触发旧规划流程。
     * 若未指定 workspacePath，则按平台配置自动生成。
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
        if (Objects.isNull(entity.getAutoRunCodingTask())) {
            entity.setAutoRunCodingTask(Boolean.FALSE);
        }
        if (Objects.isNull(entity.getWorkspacePath()) || entity.getWorkspacePath().isBlank()) {
            String root = configService.resolveWorkspaceRoot(configService.get());
            entity.setWorkspacePath(root + File.separator + entity.getId());
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
        return lifecycleService.transitionState(projectId, target);
    }

    /**
     * 兼容旧 refineSpec 入口：发起概要设计生成。
     *
     * @param projectId 项目 id
     * @return 写操作结果（携带新建 GENERATING 概要设计）
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
