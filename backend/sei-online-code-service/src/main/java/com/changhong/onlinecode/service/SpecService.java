package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.SpecDao;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Spec 服务。承载「需求 → Spec」精炼与状态流转（契约 §4 端点 4/6）。
 *
 * <p>Phase 1 compile-only：refineSpec 生成的是最小占位 Spec（DRAFT），
 * 真正的 Requirement Agent 精炼逻辑属后续接入项。</p>
 *
 * @author sei-online-code
 */
@Service
public class SpecService extends BaseEntityService<Spec> {

    private final SpecDao dao;
    private final ProjectService projectService;

    public SpecService(SpecDao dao, ProjectService projectService) {
        this.dao = dao;
        this.projectService = projectService;
    }

    @Override
    protected BaseEntityDao<Spec> getDao() {
        return dao;
    }

    /**
     * 精炼 Spec：项目 DRAFTING → SPEC_REFINING → SPEC_REVIEW，产出一个 SPEC_REVIEW 态 Spec。
     *
     * <p>版本号按项目已有最大版本 + 1 递增。</p>
     *
     * @param projectId 项目 id
     * @return 写操作结果（携带新建 Spec）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Spec> refineSpec(String projectId) {
        Project project = projectService.findOne(projectId);
        if (Objects.isNull(project)) {
            return OperateResultWithData.operationFailure("项目不存在: " + projectId);
        }

        // 生命周期推进：DRAFTING → SPEC_REFINING → SPEC_REVIEW
        projectService.transitionState(projectId, LifecycleState.SPEC_REFINING);

        Spec spec = new Spec();
        spec.setProjectId(projectId);
        spec.setVersion(nextVersion(projectId));
        spec.setState(SpecState.SPEC_REVIEW);
        // TODO(oma-deferred): 接入 Requirement Agent 后由其填充 pages/components/entities/apiContract
        OperateResultWithData<Spec> saved = super.save(spec);
        if (saved.notSuccessful()) {
            return saved;
        }

        // 项目挂载当前 Spec 并进入 SPEC_REVIEW
        projectService.transitionState(projectId, LifecycleState.SPEC_REVIEW);
        project = projectService.findOne(projectId);
        project.setCurrentSpecId(saved.getData().getId());
        projectService.save(project);

        return saved;
    }

    /**
     * 计算项目下一个 Spec 版本号。
     *
     * @param projectId 项目 id
     * @return 下一个版本号（首个为 1）
     */
    private Integer nextVersion(String projectId) {
        List<Spec> specs = dao.findByProjectIdOrderByVersionDesc(projectId);
        if (specs == null || specs.isEmpty() || specs.get(0).getVersion() == null) {
            return 1;
        }
        return specs.get(0).getVersion() + 1;
    }
}
