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
     * 精炼 Spec：项目 DRAFTING/FAILED → SPEC_REFINING → SPEC_REVIEW，产出一个 SPEC_REVIEW 态 Spec。
     *
     * <p>版本号按项目已有最大版本 + 1 递增。
     *
     * <p>FAILED 重试回流：先回 DRAFTING，再走原 DRAFTING→SPEC_REFINING→SPEC_REVIEW。
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
        LifecycleState entry = project.getState();
        if (entry != LifecycleState.DRAFTING && entry != LifecycleState.FAILED) {
            return OperateResultWithData.operationFailure(
                    "仅 DRAFTING/FAILED 状态可精炼 Spec，当前为 " + entry);
        }
        // FAILED 重试回流：先回 DRAFTING，再走原 DRAFTING→SPEC_REFINING→SPEC_REVIEW
        if (entry == LifecycleState.FAILED) {
            OperateResultWithData<Project> back = projectService.transitionState(projectId, LifecycleState.DRAFTING);
            if (back.notSuccessful()) return OperateResultWithData.operationFailure(back.getMessage());
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
            // 业务失败：显式置 FAILED（SPEC_REFINING→FAILED，UNIVERSAL 允许），避免卡死 SPEC_REFINING
            projectService.transitionState(projectId, LifecycleState.FAILED);
            return saved;
        }

        // 项目挂载当前 Spec 并进入 SPEC_REVIEW
        projectService.transitionState(projectId, LifecycleState.SPEC_REVIEW);
        project = projectService.findOne(projectId);
        project.setCurrentSpecId(saved.getData().getId());
        projectService.save(project);
        // TODO(oma-deferred): 异常路径下 @Transactional 整笔回滚到入口态（DRAFTING/FAILED），
        //   不显式置 FAILED；接入 Agent 后若需异常显式 FAILED，用 REQUIRES_NEW 自注入 markFailed。
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
     * 重新生成 Spec：从 SPEC_REVIEW 产出新版本 Spec（version+1，不可变历史）。
     *
     * <p>项目保持 SPEC_REVIEW 状态，不经过 SPEC_REFINING。</p>
     *
     * @param projectId  项目 id
     * @param modifyHint 修改提示（当前不持久化，仅签名接收）
     * @return 写操作结果（携带新建 Spec）
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

        Spec spec = new Spec();
        spec.setProjectId(projectId);
        spec.setVersion(nextVersion(projectId));
        spec.setState(SpecState.SPEC_REVIEW);
        // TODO(oma-deferred): 接入 Requirement Agent 后基于 prior 版本 + modifyHint 增量填充
        //   pages/components/entities/apiContract；本轮仅做版本递增 + 状态编织（与 refineNextVersion 一致）
        OperateResultWithData<Spec> saved = super.save(spec);
        if (saved.notSuccessful()) {
            return saved;
        }

        project.setCurrentSpecId(saved.getData().getId());
        projectService.save(project);
        // 项目保持 SPEC_REVIEW（自环合法），不经过 SPEC_REFINING
        return saved;
    }
}
