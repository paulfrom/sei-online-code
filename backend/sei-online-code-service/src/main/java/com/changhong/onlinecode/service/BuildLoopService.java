package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TaskState;
import com.changhong.onlinecode.entity.Iteration;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.onlinecode.entity.Task;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Build Loop 服务（B26–B28）。契约 Phase 4 §2 端点 25/26/28/29、§3 状态机。
 *
 * <p>闭合「PREVIEW 反馈 → 增量 Spec → 再确认 → 再派发」这一回合循环：
 * <ul>
 *   <li>{@link #accept} —— 验收：PREVIEW → ACCEPTED（终态），置 finishedDate。</li>
 *   <li>{@link #optimize} —— 反馈再入：从 PREVIEW 产出新 Spec 版本（既有版本不可变）+
 *       新回合迭代（round+1、parentIterationId 指向上回合、记录 feedback），状态 → SPEC_REVIEW。</li>
 *   <li>{@link #cancel} —— 中止：非终态 → CANCELLED，同一事务内级联 RUNNING task/run → CANCELLED。</li>
 *   <li>{@link #retry} —— 重试：FAILED → DISPATCHING，以同版 Spec 重新派发（新 task/run）。</li>
 * </ul>
 * 非法状态流转一律回 {@link OperateResultWithData#operationFailure}（契约 §3）。</p>
 *
 * @author sei-online-code
 */
@Service
public class BuildLoopService {

    private final IterationService iterationService;
    private final SpecService specService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final RunService runService;
    private final DispatchService dispatchService;

    public BuildLoopService(IterationService iterationService,
                            SpecService specService,
                            ProjectService projectService,
                            TaskService taskService,
                            RunService runService,
                            DispatchService dispatchService) {
        this.iterationService = iterationService;
        this.specService = specService;
        this.projectService = projectService;
        this.taskService = taskService;
        this.runService = runService;
        this.dispatchService = dispatchService;
    }

    /**
     * 验收迭代：PREVIEW → ACCEPTED（终态），置 finishedDate（ep #25）。
     *
     * @param iterationId 迭代 id
     * @return 写操作结果（携带更新后迭代）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Iteration> accept(String iterationId) {
        Iteration iteration = iterationService.findOne(iterationId);
        if (Objects.isNull(iteration)) {
            return OperateResultWithData.operationFailure("迭代不存在: " + iterationId);
        }
        if (iteration.getState() != LifecycleState.PREVIEW) {
            return OperateResultWithData.operationFailure(
                    "仅 PREVIEW 态可验收，当前: " + iteration.getState());
        }

        iteration.setState(LifecycleState.ACCEPTED);
        iteration.setFinishedDate(new Date());
        OperateResultWithData<Iteration> saved = iterationService.save(iteration);
        if (saved.notSuccessful()) {
            return saved;
        }
        // 项目镜像终态
        projectService.transitionState(iteration.getProjectId(), LifecycleState.ACCEPTED);
        return saved;
    }

    /**
     * 优化项目（反馈再入）：从 PREVIEW 产出下一 Spec 版本 + 下一回合迭代，状态 → SPEC_REVIEW（ep #26）。
     *
     * <p>不修改上一回合 Spec/迭代（既有版本不可变，构成可 diff 历史）；feedback 必须非空
     * （契约 §3：Spec 是唯一真源，feedback 不得绕过 Spec 直接改代码）。</p>
     *
     * @param projectId 项目 id
     * @param feedback  本回合优化诉求（非空）
     * @return 写操作结果（携带新回合迭代）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Iteration> optimize(String projectId, String feedback) {
        if (feedback == null || feedback.isBlank()) {
            return OperateResultWithData.operationFailure("feedback 不能为空");
        }
        Project project = projectService.findOne(projectId);
        if (Objects.isNull(project)) {
            return OperateResultWithData.operationFailure("项目不存在: " + projectId);
        }
        Iteration prior = iterationService.findOne(project.getCurrentIterationId());
        if (Objects.isNull(prior)) {
            return OperateResultWithData.operationFailure("项目无活动迭代: " + projectId);
        }
        if (prior.getState() != LifecycleState.PREVIEW) {
            return OperateResultWithData.operationFailure(
                    "仅 PREVIEW 态可优化，当前: " + prior.getState());
        }

        // 增量精炼下一 Spec 版本（version = prior+1，既有版本不可变）
        OperateResultWithData<Spec> specResult = specService.refineNextVersion(projectId, feedback);
        if (specResult.notSuccessful()) {
            return OperateResultWithData.operationFailure(specResult.getMessage());
        }
        Spec nextSpec = specResult.getData();

        // 项目回退再入：PREVIEW → SPEC_REFINING → SPEC_REVIEW（生命周期表约束）
        OperateResultWithData<Project> back =
                projectService.transitionState(projectId, LifecycleState.SPEC_REFINING);
        if (back.notSuccessful()) {
            return OperateResultWithData.operationFailure(back.getMessage());
        }
        projectService.transitionState(projectId, LifecycleState.SPEC_REVIEW);

        // 新回合迭代：round+1、父回合指向 prior、记录 feedback，挂新 Spec 版本
        Iteration next = new Iteration();
        next.setProjectId(projectId);
        next.setSpecId(nextSpec.getId());
        next.setSpecVersion(nextSpec.getVersion());
        next.setRound((prior.getRound() == null ? 1 : prior.getRound()) + 1);
        next.setParentIterationId(prior.getId());
        next.setFeedback(feedback);
        next.setState(LifecycleState.SPEC_REVIEW);
        OperateResultWithData<Iteration> saved = iterationService.save(next);
        if (saved.notSuccessful()) {
            return saved;
        }

        // 项目挂载新 Spec 与新回合迭代
        project = projectService.findOne(projectId);
        project.setCurrentSpecId(nextSpec.getId());
        project.setCurrentIterationId(saved.getData().getId());
        projectService.save(project);

        return saved;
    }

    /**
     * 取消迭代：非终态 → CANCELLED（终态），同一事务内级联 RUNNING task/run → CANCELLED（ep #28）。
     *
     * @param iterationId 迭代 id
     * @return 写操作结果（携带更新后迭代）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Iteration> cancel(String iterationId) {
        Iteration iteration = iterationService.findOne(iterationId);
        if (Objects.isNull(iteration)) {
            return OperateResultWithData.operationFailure("迭代不存在: " + iterationId);
        }
        if (isTerminal(iteration.getState())) {
            return OperateResultWithData.operationFailure(
                    "终态迭代不可取消，当前: " + iteration.getState());
        }

        // 级联：RUNNING 任务/运行记录一并置 CANCELLED（同一工作单元，backend 规则 #9）
        List<Task> tasks = taskService.findByIteration(iterationId);
        for (Task task : tasks) {
            if (task.getState() == TaskState.RUNNING) {
                task.setState(TaskState.CANCELLED);
                taskService.save(task);
            }
        }
        List<Run> runs = runService.findByIteration(iterationId);
        for (Run run : runs) {
            if (run.getState() == RunState.RUNNING) {
                run.setState(RunState.CANCELLED);
                run.setFinishedDate(new Date());
                runService.save(run);
            }
        }

        iteration.setState(LifecycleState.CANCELLED);
        iteration.setFinishedDate(new Date());
        OperateResultWithData<Iteration> saved = iterationService.save(iteration);
        if (saved.notSuccessful()) {
            return saved;
        }
        projectService.transitionState(iteration.getProjectId(), LifecycleState.CANCELLED);
        return saved;
    }

    /**
     * 重试迭代：FAILED → DISPATCHING，以同版 Spec 重新派发（新 task/run）（ep #29）。
     *
     * @param iterationId 迭代 id
     * @return 写操作结果（携带更新后迭代）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Iteration> retry(String iterationId) {
        Iteration iteration = iterationService.findOne(iterationId);
        if (Objects.isNull(iteration)) {
            return OperateResultWithData.operationFailure("迭代不存在: " + iterationId);
        }
        if (iteration.getState() != LifecycleState.FAILED) {
            return OperateResultWithData.operationFailure(
                    "仅 FAILED 态可重试，当前: " + iteration.getState());
        }

        // 迭代与项目均回到 DISPATCHING（FAILED → DISPATCHING，生命周期表约束）
        iteration.setState(LifecycleState.DISPATCHING);
        iteration.setFinishedDate(null);
        OperateResultWithData<Iteration> saved = iterationService.save(iteration);
        if (saved.notSuccessful()) {
            return saved;
        }
        OperateResultWithData<Project> back =
                projectService.transitionState(iteration.getProjectId(), LifecycleState.DISPATCHING);
        if (back.notSuccessful()) {
            return OperateResultWithData.operationFailure(back.getMessage());
        }

        // 以同版 Spec 重新派发（新 task/run，随后 DISPATCHING → DEVELOPING）
        dispatchService.dispatch(iterationId);
        return saved;
    }

    /**
     * 是否为终态（ACCEPTED/FAILED/CANCELLED）。
     *
     * @param state 生命周期状态
     * @return 终态则 true
     */
    private boolean isTerminal(LifecycleState state) {
        return state == LifecycleState.ACCEPTED
                || state == LifecycleState.FAILED
                || state == LifecycleState.CANCELLED;
    }
}
