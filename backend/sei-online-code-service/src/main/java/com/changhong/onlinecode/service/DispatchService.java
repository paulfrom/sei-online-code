package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.ClaudeRunner;
import com.changhong.onlinecode.agent.WorktreeManager;
import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TaskState;
import com.changhong.onlinecode.entity.Iteration;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.onlinecode.entity.Task;
import com.changhong.onlinecode.dto.spec.SpecPage;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Dispatch Agent 服务（B12/B14）。契约 Phase 2 §2 端点 10/15。
 *
 * <p>职责：将确认后的 Spec 按 fileScope 切分为非重叠 {@link Task}（每个 page 一个任务，
 * 落在互斥的 per-page 路由文件与 per-feature mock 文件上，保证并行 worktree 少冲突，
 * 符合 ADR-0001 A-primary 策略），为每个任务在隔离 worktree 中并行 fan-out 一次
 * {@link ClaudeRunner} 执行；合并阶段将各任务分支 fast-forward 回主干，冲突则回退串行重解。</p>
 *
 * <p>本轮 compile-only：worktree 创建与 claude spawn 的真实执行由 {@link WorktreeManager}
 * / {@link ClaudeRunner} 骨架承载，不在本轮运行；此处编织并行编排结构并推进状态机。</p>
 *
 * @author sei-online-code
 */
@Service
public class DispatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchService.class);

    /** Phase 2 单一内置开发 agent（自定义 agent 属 Phase 3）。 */
    private static final String DEV_AGENT = "dev-agent";

    private final IterationService iterationService;
    private final SpecService specService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final RunService runService;
    private final WorktreeManager worktreeManager;
    private final ClaudeRunner claudeRunner;

    public DispatchService(IterationService iterationService,
                           SpecService specService,
                           ProjectService projectService,
                           TaskService taskService,
                           RunService runService,
                           WorktreeManager worktreeManager,
                           ClaudeRunner claudeRunner) {
        this.iterationService = iterationService;
        this.specService = specService;
        this.projectService = projectService;
        this.taskService = taskService;
        this.runService = runService;
        this.worktreeManager = worktreeManager;
        this.claudeRunner = claudeRunner;
    }

    /**
     * 派发迭代：确认后的 Spec → 非重叠 Task[]，每任务并行 fan-out 一次执行。
     * 项目状态 DISPATCHING → DEVELOPING。
     *
     * @param iterationId 迭代 id
     * @return 写操作结果（携带切分出的任务列表）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<List<Task>> dispatch(String iterationId) {
        Iteration iteration = iterationService.findOne(iterationId);
        if (Objects.isNull(iteration)) {
            return OperateResultWithData.operationFailure("迭代不存在: " + iterationId);
        }
        Spec spec = specService.findOne(iteration.getSpecId());
        if (Objects.isNull(spec)) {
            return OperateResultWithData.operationFailure("Spec 不存在: " + iteration.getSpecId());
        }

        List<Task> tasks = cutTasks(iteration, spec);
        if (tasks.isEmpty()) {
            return OperateResultWithData.operationFailure("Spec 无可切分的 page，无法派发");
        }

        List<Task> saved = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            OperateResultWithData<Task> r = taskService.save(task);
            if (r.notSuccessful()) {
                return OperateResultWithData.operationFailure(r.getMessage());
            }
            saved.add(r.getData());
        }

        // 项目推进 DISPATCHING → DEVELOPING，随后并行 fan-out 各任务执行。
        projectService.transitionState(iteration.getProjectId(), LifecycleState.DEVELOPING);
        fanOut(iteration, saved);

        return OperateResultWithData.operationSuccessWithData(saved);
    }

    /**
     * 合并迭代：各任务分支 fast-forward 回主干，冲突回退串行重解。
     * 项目状态 DEVELOPING → MERGING → DEPLOYING（后续由 deploy 推进到 PREVIEW）。
     *
     * @param iterationId 迭代 id
     * @return 写操作结果（携带迭代）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<Iteration> merge(String iterationId) {
        Iteration iteration = iterationService.findOne(iterationId);
        if (Objects.isNull(iteration)) {
            return OperateResultWithData.operationFailure("迭代不存在: " + iterationId);
        }

        projectService.transitionState(iteration.getProjectId(), LifecycleState.MERGING);

        List<Task> tasks = taskService.findByIteration(iterationId);
        for (Task task : tasks) {
            // TODO(oma-deferred): gitRoot 取自 WorkspaceManager 解析的工作区路径（Phase 2 运行期接入）
            String gitRoot = null;
            boolean ok = worktreeManager.mergeFastForward(gitRoot, task.getWorktreeBranch());
            task.setState(ok ? TaskState.MERGED : TaskState.MERGING);
            taskService.save(task);
            if (!ok) {
                // 冲突：责任任务需串行重解后重试，此处不阻断编译期骨架
                LOGGER.warn("dispatch: 任务合并冲突，待串行重解 taskId={}", task.getId());
            }
        }

        projectService.transitionState(iteration.getProjectId(), LifecycleState.DEPLOYING);
        return OperateResultWithData.operationSuccessWithData(iteration);
    }

    /**
     * 将 Spec 按 page 切分为非重叠任务：每个 page 认领 per-page 路由文件 + per-feature mock 文件，
     * 文件边界互斥，符合 ADR-0001 冲突规避约定。
     *
     * @param iteration 迭代
     * @param spec      已确认的 Spec
     * @return 待持久化的任务列表（seq 从 1 递增）
     */
    private List<Task> cutTasks(Iteration iteration, Spec spec) {
        List<Task> tasks = new ArrayList<>();
        List<SpecPage> pages = spec.getPages();
        if (pages == null) {
            return tasks;
        }
        int seq = 1;
        for (SpecPage page : pages) {
            Task task = new Task();
            task.setIterationId(iteration.getId());
            task.setTitle(page.getTitle());
            task.setDescription("实现页面 " + page.getRoute() + "：" + page.getDescription());
            // 非重叠 fileScope：per-page 路由文件 + per-feature mock 文件（glob 聚合入口，互不触碰）
            task.setFileScope(Arrays.asList(
                    "src/pages/" + page.getKey() + "/index.tsx",
                    "src/mocks/" + page.getKey() + ".ts"));
            task.setAssignedAgent(DEV_AGENT);
            task.setState(TaskState.PENDING);
            task.setSeq(seq++);
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * 为每个任务并行 fan-out 一次执行：建 worktree → 起 RUNNING 态 Run → 异步 spawn ClaudeRunner。
     *
     * <p>本轮 compile-only：并行结构（每任务一个 {@link CompletableFuture}）已就绪，
     * gitRoot/baseRef 的真实解析与结果回收属运行期接入项。</p>
     *
     * @param iteration 迭代
     * @param tasks     已持久化的任务
     */
    private void fanOut(Iteration iteration, List<Task> tasks) {
        List<CompletableFuture<String>> futures = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            String branch = "task/" + iteration.getId() + "-" + String.format("%04d", task.getSeq());
            task.setWorktreeBranch(branch);
            task.setState(TaskState.RUNNING);
            taskService.save(task);

            Run run = new Run();
            run.setTaskId(task.getId());
            run.setIterationId(iteration.getId());
            run.setState(RunState.RUNNING);
            run.setStartedDate(new Date());
            // TODO(oma-deferred): worktreePath 由 WorkspaceManager + WorktreeManager.addWorktree 实际创建后回填
            run.setWorktreePath(null);
            OperateResultWithData<Run> savedRun = runService.save(run);

            String runId = savedRun.successful() ? savedRun.getData().getId() : null;
            // 并行 spawn：每任务独立 future，互不阻塞（ADR-0001 并行 worktree 模型）
            CompletableFuture<String> future =
                    claudeRunner.execute(iteration.getId(), task.getId(), runId, task.getDescription(), null);
            futures.add(future);
        }
        // 本轮不阻塞等待（compile-only）；运行期由编排层 join 并回收各 Run 终态。
        LOGGER.info("dispatch: 已 fan-out {} 个并行任务 iterationId={}", futures.size(), iteration.getId());
    }
}
