package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.IterationDao;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.entity.Iteration;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 迭代服务。承载「确认 Spec → 启动迭代」与「部署 → 预览」（契约 §4 端点 6/7）。
 *
 * <p>Phase 1 串行单路径：DISPATCHING/DEVELOPING/MERGING 折叠为一次串行执行；
 * 真正的 ClaudeRunner spawn 与 vite build 属后续接入项。</p>
 *
 * @author sei-online-code
 */
@Service
public class IterationService extends BaseEntityService<Iteration> {

    private final IterationDao dao;
    private final ProjectService projectService;
    private final SpecService specService;
    private final PlanService planService;

    public IterationService(IterationDao dao,
                            ProjectService projectService,
                            SpecService specService,
                            PlanService planService) {
        this.dao = dao;
        this.projectService = projectService;
        this.specService = specService;
        this.planService = planService;
    }

    @Override
    protected BaseEntityDao<Iteration> getDao() {
        return dao;
    }

    /**
     * 确认 Spec 并启动规划：Spec → CONFIRMED，然后创建 GENERATING 态 Plan 并
     * spawn planning-agent。迭代/构建项目不在 Spec 确认时创建，等任务审批后由后续
     * build/dispatch 流程触发。
     *
     * @param specId Spec id
     * @return 写操作结果（携带新建 GENERATING Plan）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<PlanDto> confirmSpec(String specId) {
        Spec spec = specService.findOne(specId);
        if (Objects.isNull(spec)) {
            return OperateResultWithData.operationFailure("Spec 不存在: " + specId);
        }
        // 状态守卫：仅 SPEC_REVIEW 可确认（GENERATING/FAILED 拒绝，避免确认空 Spec）
        if (spec.getState() != SpecState.SPEC_REVIEW) {
            return OperateResultWithData.operationFailure(
                    "仅 SPEC_REVIEW 状态可确认 Spec，当前为 " + spec.getState());
        }

        spec.setState(SpecState.CONFIRMED);
        OperateResultWithData<Spec> saved = specService.save(spec);
        if (saved.notSuccessful()) {
            return OperateResultWithData.operationFailure(saved.getMessage());
        }

        return planService.regenerate(spec.getProjectId(), null);
    }

    /**
     * 部署迭代：迭代进入 DEPLOYING，生成 previewUrl，随后迭代与项目均置为 PREVIEW。
     *
     * <p>Phase 1 compile-only：previewUrl 由静态端口约定生成，
     * 真正的 vite build 由 Deploy Agent 后续接入。</p>
     *
     * @param iterationId 迭代 id
     * @return 写操作结果（携带更新后迭代）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Iteration> deploy(String iterationId) {
        Iteration iteration = dao.findOne(iterationId);
        if (Objects.isNull(iteration)) {
            return OperateResultWithData.operationFailure("迭代不存在: " + iterationId);
        }

        iteration.setState(LifecycleState.DEPLOYING);
        // TODO(oma-deferred): 接入 Deploy Agent 执行 vite build，此处仅按静态端口约定生成 previewUrl
        iteration.setPreviewUrl(buildPreviewUrl(iteration));
        iteration.setState(LifecycleState.PREVIEW);
        OperateResultWithData<Iteration> saved = super.save(iteration);
        if (saved.notSuccessful()) {
            return saved;
        }

        // 项目串行推进至 PREVIEW：DISPATCHING → DEVELOPING → MERGING → DEPLOYING → PREVIEW
        advanceProjectToPreview(iteration.getProjectId());

        return saved;
    }

    /**
     * 按项目分配的静态端口生成预览地址（Phase 1 占位规则）。
     *
     * @param iteration 迭代
     * @return 预览地址
     */
    private String buildPreviewUrl(Iteration iteration) {
        // Phase 1 占位：真实端口注册表属 Phase 2。以迭代 id 的 hash 落在 41000+ 段以避免冲突。
        int port = 41000 + Math.abs(Objects.hashCode(iteration.getId())) % 1000;
        return "http://localhost:" + port;
    }

    /**
     * 将项目从 DISPATCHING 串行推进到 PREVIEW（Phase 1 折叠中间态）。
     *
     * @param projectId 项目 id
     */
    private void advanceProjectToPreview(String projectId) {
        projectService.transitionState(projectId, LifecycleState.DEVELOPING);
        projectService.transitionState(projectId, LifecycleState.MERGING);
        projectService.transitionState(projectId, LifecycleState.DEPLOYING);
        projectService.transitionState(projectId, LifecycleState.PREVIEW);
    }
}
