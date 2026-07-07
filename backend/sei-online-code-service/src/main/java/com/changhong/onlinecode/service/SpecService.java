package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.SpecDao;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.dto.plan.PlanContent;
import com.changhong.onlinecode.dto.plan.PlanFeature;
import com.changhong.onlinecode.dto.plan.PlanModule;
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
 * Spec 服务。承载模块详细设计生成与状态流转。
 *
 * <p>接入 Requirement Agent：{@link #refineSpec} / {@link #regenerate} 建 GENERATING 行后
 * 由 {@link SpecAgentService#spawnRequirement} 异步填充 content 并收口到 SPEC_REVIEW / FAILED。</p>
 *
 * @author sei-online-code
 */
@Service
public class SpecService extends BaseEntityService<Spec> {

    private final SpecDao dao;
    private final ProjectService projectService;
    private final SpecAgentService specAgentService;
    private final PlanService planService;
    private final PlanAgentService planAgentService;

    public SpecService(SpecDao dao,
                       ProjectService projectService,
                       SpecAgentService specAgentService,
                       PlanService planService,
                       PlanAgentService planAgentService) {
        this.dao = dao;
        this.projectService = projectService;
        this.specAgentService = specAgentService;
        this.planService = planService;
        this.planAgentService = planAgentService;
    }

    @Override
    protected BaseEntityDao<Spec> getDao() {
        return dao;
    }

    /**
     * 确认模块详细设计：Spec → CONFIRMED，然后为该模块功能项生成 FeatureDesign。
     *
     * @param specId Spec id
     * @return 写操作结果（携带确认后的 Spec）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Spec> confirmSpec(String specId) {
        Spec spec = findOne(specId);
        if (Objects.isNull(spec)) {
            return OperateResultWithData.operationFailure("Spec 不存在: " + specId);
        }
        if (spec.getState() != SpecState.SPEC_REVIEW) {
            return OperateResultWithData.operationFailure(
                    "仅 SPEC_REVIEW 状态可确认 Spec，当前为 " + spec.getState());
        }

        spec.setState(SpecState.CONFIRMED);
        OperateResultWithData<Spec> saved = save(spec);
        if (saved.notSuccessful()) {
            return OperateResultWithData.operationFailure(saved.getMessage());
        }

        List<PlanFeature> features = featuresForModule(spec);
        if (!features.isEmpty()) {
            planAgentService.spawnFeatureDesigns(spec.getProjectId(), features);
        }
        return saved;
    }

    private List<PlanFeature> featuresForModule(Spec spec) {
        PlanDto plan = planService.findLatestConfirmed(spec.getProjectId());
        if (plan == null) {
            plan = planService.findLatest(spec.getProjectId());
            if (plan == null || plan.getStatus() != PlanStatus.CONFIRMED) {
                return List.of();
            }
        }
        PlanContent content = plan.getContent();
        if (content == null) {
            return List.of();
        }
        if (content.getModules() != null && !content.getModules().isEmpty()) {
            return content.getModules().stream()
                    .filter(module -> Objects.equals(module.getModuleId(), spec.getModuleId()))
                    .findFirst()
                    .map(PlanModule::getFeatures)
                    .filter(Objects::nonNull)
                    .orElse(List.of());
        }
        return content.getFeatures() == null ? List.of() : content.getFeatures();
    }

    /**
     * 兼容旧模块详细设计生成入口：项目 DRAFTING/FAILED → SPEC_REFINING，产出 GENERATING 态 Spec 并 spawn Requirement Agent。
     *
     * <p>版本号按项目已有最大版本 + 1 递增。项目在生成期间停在 SPEC_REFINING，
     * 由 {@link SpecAgentService} 回调推进到 SPEC_REVIEW（成功）/ FAILED（失败）。</p>
     *
     * <p>FAILED 重试回流：先回 DRAFTING，再走原 DRAFTING→SPEC_REFINING→（异步）SPEC_REVIEW。</p>
     *
     * @param projectId 项目 id
     * @return 写操作结果（携带新建 GENERATING Spec）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Spec> refineSpec(String projectId) {
        Project project = projectService.findOne(projectId);
        if (Objects.isNull(project)) {
            return OperateResultWithData.operationFailure("项目不存在: " + projectId);
        }
        LifecycleState entry = project.getState();
        if (entry != LifecycleState.DRAFTING && entry != LifecycleState.FAILED) {
            return OperateResultWithData.operationFailure(
                    "仅 DRAFTING/FAILED 状态可生成模块详细设计，当前为 " + entry);
        }
        // FAILED 重试回流：先回 DRAFTING，再走原 DRAFTING→SPEC_REFINING
        if (entry == LifecycleState.FAILED) {
            OperateResultWithData<Project> back = projectService.transitionState(projectId, LifecycleState.DRAFTING);
            if (back.notSuccessful()) return OperateResultWithData.operationFailure(back.getMessage());
        }
        // 生命周期推进：DRAFTING → SPEC_REFINING（生成期间停在此态，回调推进到 SPEC_REVIEW）
        projectService.transitionState(projectId, LifecycleState.SPEC_REFINING);

        Spec spec = new Spec();
        spec.setProjectId(projectId);
        spec.setVersion(nextVersion(projectId));
        spec.setState(SpecState.GENERATING);
        spec.setModifyHint(null);
        OperateResultWithData<Spec> saved = super.save(spec);
        if (saved.notSuccessful()) {
            // 业务失败：显式置 FAILED（SPEC_REFINING→FAILED，UNIVERSAL 允许），避免卡死 SPEC_REFINING
            projectService.transitionState(projectId, LifecycleState.FAILED);
            return saved;
        }

        // 项目挂载当前 Spec（仍处 SPEC_REFINING），spawn Requirement Agent 异步填充 + 收口
        project = projectService.findOne(projectId);
        project.setCurrentSpecId(saved.getData().getId());
        projectService.save(project);
        specAgentService.spawnRequirement(projectId, null, saved.getData().getId());
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

    /**
     * 增量精炼下一个 Spec 版本（Phase 4 §1 反馈再入的 Requirement Agent 接入点）。
     *
     * <p>不修改任何既有版本（既有版本不可变，构成可 diff 历史）；产出一个 SPEC_REVIEW 态、
     * version = prior+1 的新 Spec。feedback 作为需求 Agent 的增量诉求 seed。</p>
     *
     * @param projectId 项目 id
     * @param feedback  本回合优化诉求（Requirement Agent 增量输入）
     * @return 写操作结果（携带新版本 Spec）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Spec> refineNextVersion(String projectId, String feedback) {
        Spec spec = new Spec();
        spec.setProjectId(projectId);
        spec.setVersion(nextVersion(projectId));
        spec.setState(SpecState.SPEC_REVIEW);
        // TODO(oma-deferred): 接入 Requirement Agent 后基于 prior 版本 + feedback 增量填充
        //   pages/components/entities/apiContract；本轮仅做版本递增 + 状态编织。
        return super.save(spec);
    }

    /**
     * 项目的 Spec 版本历史，按 version 升序（ep #30，可 diff 的不可变历史）。
     *
     * @param projectId 项目 id
     * @return Spec 列表（version 升序）
     */
    public List<Spec> findByProject(String projectId) {
        return dao.findByProjectIdOrderByVersionAsc(projectId);
    }

    /**
     * 重新生成 Spec：从 SPEC_REVIEW 产出 GENERATING 新版本 Spec（version+1，不可变历史），
     * spawn Requirement Agent 基于上版本 + modifyHint 异步填充。
     *
     * <p>项目保持 SPEC_REVIEW 状态，不经过 SPEC_REFINING（自环合法）。</p>
     *
     * @param projectId  项目 id
     * @param modifyHint 修改提示（持久化到 Spec.modifyHint，备历史追溯）
     * @return 写操作结果（携带新建 GENERATING Spec）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Spec> regenerate(String projectId, String modifyHint) {
        Project project = projectService.findOne(projectId);
        if (Objects.isNull(project)) {
            return OperateResultWithData.operationFailure("项目不存在: " + projectId);
        }
        if (project.getState() != LifecycleState.SPEC_REVIEW) {
            return OperateResultWithData.operationFailure("仅 SPEC_REVIEW 状态可重新生成 Spec，当前为 " + project.getState());
        }
        // 并发守卫：latest Spec 为 GENERATING 时拒绝（对齐 PlanService.regenerate）
        List<Spec> existing = dao.findByProjectIdOrderByVersionDesc(projectId);
        if (existing != null && !existing.isEmpty()) {
            Spec latest = existing.get(0);
            if (latest.getState() == SpecState.GENERATING) {
                return OperateResultWithData.operationFailure("Spec 正在生成中，不可重复发起");
            }
        }

        Spec spec = new Spec();
        spec.setProjectId(projectId);
        spec.setVersion(nextVersion(projectId));
        spec.setState(SpecState.GENERATING);
        spec.setModifyHint(modifyHint);
        OperateResultWithData<Spec> saved = super.save(spec);
        if (saved.notSuccessful()) {
            return saved;
        }

        project.setCurrentSpecId(saved.getData().getId());
        projectService.save(project);
        // 项目保持 SPEC_REVIEW（自环合法）；agent 回调收口 Spec GENERATING → SPEC_REVIEW / FAILED
        specAgentService.spawnRequirement(projectId, modifyHint, saved.getData().getId());
        return saved;
    }
}
