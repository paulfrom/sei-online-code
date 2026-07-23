package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.AgentWorkspace;
import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.RequirementWorkspaceDao;
import com.changhong.onlinecode.dao.TaskHandoffSnapshotDao;
import com.changhong.onlinecode.dto.CodingTaskDto;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.TaskExecutionType;
import com.changhong.onlinecode.dto.enums.MemoryJobTriggerSource;
import com.changhong.onlinecode.dto.enums.MemoryJobType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.MemoryJob;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.RequirementWorkspace;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.entity.TaskHandoffSnapshot;
import com.changhong.onlinecode.service.memory.CodingTaskChangeCollector;
import com.changhong.onlinecode.service.memory.CodingTaskChangeResult;
import com.changhong.onlinecode.service.agent.AgentExecutionRequest;
import com.changhong.onlinecode.service.agent.AgentExecutionResult;
import com.changhong.onlinecode.service.agent.AgentExecutionService;
import com.changhong.onlinecode.service.agent.CodingTaskProgressIntegrator;
import com.changhong.onlinecode.service.progress.WorkspaceLeaseService;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * CodingTask 执行服务。
 *
 * <p>优先通过 {@link CliRunner} 调用真实 dev-agent 执行编码；CLI 不可用时回退到本地占位执行。
 * CodingTask 成功完成后投递 {@code MEMORY_UPDATE_AFTER_CODING_TASK} job 以增量回写平台记忆，
 * 投递失败不影响 CodingTask 成功状态（契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §16）。</p>
 *
 * @author sei-online-code
 */
@Service
@AllArgsConstructor
@Slf4j
public class CodingTaskExecutionService {

    private final CodingTaskDao codingTaskDao;
    private final RunDao runDao;
    private final RunNumberService runNumberService;
    private final RequirementService requirementService;
    private final ExecutionPlanDao executionPlanDao;
    private final RequirementCommentService requirementCommentService;
    private final WorkspaceManager workspaceManager;
    private final AgentService agentService;
    private final AgentExecutionService agentExecutionService;
    private final FailureInfoSupport failureInfoSupport;
    private final RequirementDesignContextService requirementDesignContextService;
    private final DesignContextPromptAssembler designContextPromptAssembler;
    private final MemoryJobService memoryJobService;
    private final WorkspaceMemoryService workspaceMemoryService;
    private final CodingTaskChangeCollector codingTaskChangeCollector;
    private final ApplicationEventPublisher eventPublisher;
    private final CodingTaskProgressIntegrator codingTaskProgressIntegrator;
    private final WorkspaceLeaseService workspaceLeaseService;
    private final RequirementWorkspaceDao requirementWorkspaceDao;
    private final TaskHandoffSnapshotDao taskHandoffSnapshotDao;
    private final PlatformTransactionManager transactionManager;

    /**
     * Requests logical cancellation and best-effort process termination.
     */
    public void cancelRun(String runId) {
        cancelRun(runId, null);
    }

    /** Requests cancellation and records the user comment that invalidated this run, when applicable. */
    public void cancelRun(String runId, String invalidatedByCommentId) {
        Run run = runDao.findOne(runId);
        if (run == null) {
            return;
        }
        run.setCancelRequested(Boolean.TRUE);
        if (invalidatedByCommentId != null && !invalidatedByCommentId.isBlank()) {
            run.setInvalidatedByCommentId(invalidatedByCommentId);
        }
        runDao.save(run);
        agentExecutionService.cancel(runId);
    }

    /**
     * 执行任务。
     *
     * @param id     任务 ID
     * @param prompt 用户提示词
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<CodingTaskDto> execute(String id, String prompt) {
        CodingTask task = codingTaskDao.findOne(id);
        if (Objects.isNull(task)) {
            return ResultData.fail("编码任务不存在: " + id);
        }
        task.setStatus(CodingTaskStatus.RUNNING);
        codingTaskDao.save(task);

        Agent agent = agentService.findByName(task.getAssignedAgent());
        Run run = new Run();
        linkPreviousAttempt(run, findLatestRun(id));
        run.setCodingTaskId(id);
        run.setRequirementId(task.getRequirementId());
        run.setTriggerSource(prompt == null ? TriggerSource.AUTO : TriggerSource.USER_ACTION);
        run.setUserPrompt(prompt);
        run.setState(RunState.RUNNING);
        run.setStartedDate(new Date());
        // 先解析工作区与基准 commit 再一次性 insert，避免二次 save 触发 preSave 的 existsById 校验失败。
        AgentWorkspace workspace = agentExecutionService.workspace(task.getProjectId(), requirementWorkspaceKey(task));
        run.setWorktreePath(workspace.pathString());
        run.setBaseCommit(codingTaskChangeCollector.resolveHead(workspace.pathString()));
        if (agent != null) {
            run.setAgentId(agent.getId());
            run.setAgentName(agent.getName());
            run.setCliTool(agent.getCliTool());
            run.setModel(agent.getModel());
        }
        bindProgress(run, id, task.getRequirementId(), task.getLoopId(), prompt, run.getBaseCommit());
        runNumberService.assign(run);
        run = runDao.save(run);
        String fullPrompt = buildExecutionPrompt(task, prompt, run) + safeProgressBrief(run.getExecutionId());

        final Run trackedRun = run;
        AgentExecutionRequest request = buildRequest(run, task, fullPrompt, task.getAssignedAgent());
        startAgentAfterCommit(task.getAssignedAgent(), request, trackedRun, task, false);

        CodingTaskDto dto = new CodingTaskDto();
        dto.setId(task.getId());
        dto.setStatus(task.getStatus());
        dto.setRevisionSeq(task.getRevisionSeq());
        dto.setSupersedesTaskId(task.getSupersedesTaskId());
        dto.setDispositionReason(task.getDispositionReason());
        return ResultData.success(dto);
    }

    /**
     * 调度器调用的计划任务执行入口。
     *
     * @param codingTaskId 任务 ID
     * @param agentName    开发代理名称
     * @param prompt       提示词
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultData<CodingTaskDto> executePlanTask(String codingTaskId, String agentName, String prompt) {
        return executePlanTask(codingTaskId, agentName, prompt, TriggerSource.AUTO);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResultData<CodingTaskDto> executePlanTask(String codingTaskId, String agentName, String prompt,
                                                     TriggerSource triggerSource) {
        CodingTask task = codingTaskDao.findOne(codingTaskId);
        if (Objects.isNull(task)) {
            return ResultData.fail("编码任务不存在: " + codingTaskId);
        }
        Agent agent = agentService.findByName(agentName);
        if (agent == null) {
            log.error("coding-task plan agent not found taskId={}, agentName={}", codingTaskId, agentName);
            String failure = "开发代理不存在: " + agentName;
            task.setStatus(CodingTaskStatus.FAILED);
            task.setFailureSummary("开发代理未找到");
            task.setFailureDetail("agentName=" + agentName);
            task.setLastFailedAt(new Date());
            codingTaskDao.save(task);

            Run failedRun = new Run();
            linkPreviousAttempt(failedRun, findPreviousRun(task));
            failedRun.setCodingTaskId(codingTaskId);
            failedRun.setRequirementId(task.getRequirementId());
            failedRun.setLoopId(task.getLoopId());
            failedRun.setTriggerSource(triggerSource == null ? TriggerSource.AUTO : triggerSource);
            failedRun.setAgentName(agentName);
            failedRun.setState(RunState.FAILED);
            failedRun.setTerminalReason(RunTerminalReason.FAILED);
            failedRun.setStartedDate(new Date());
            failedRun.setFinishedDate(new Date());
            failedRun.setSummary(failure);
            failedRun.setFailureReason(failure);
            runNumberService.assign(failedRun);
            runDao.save(failedRun);
            runAfterCurrentTransactionCommit(() -> eventPublisher.publishEvent(
                    new CodingTaskSchedulingEvents.DevelopmentFinished(codingTaskId, false, failure)));
            return ResultData.fail(failure);
        }

        task.setStatus(CodingTaskStatus.RUNNING);
        codingTaskDao.save(task);

        Run run = new Run();
        Run previousRun = findPreviousRun(task);
        linkPreviousAttempt(run, previousRun);
        run.setCodingTaskId(codingTaskId);
        run.setRequirementId(task.getRequirementId());
        run.setLoopId(task.getLoopId());
        run.setTriggerSource(triggerSource == null ? TriggerSource.AUTO : triggerSource);
        ExecutionPlan executionPlan = task.getExecutionPlanId() == null
                ? null : executionPlanDao.findOne(task.getExecutionPlanId());
        if (executionPlan != null) {
            run.setMemoryContextId(executionPlan.getMemoryContextId());
            run.setWorkspaceMemoryId(executionPlan.getWorkspaceMemoryId());
        }
        // 先解析工作区与基准 commit 再一次性 insert，避免 insert 后立即二次 save 触发
        // sei-core preSave 的 existsById 校验（同事务内 persist 尚未 flush，DB 查不到 id）。
        AgentWorkspace workspace = agentExecutionService.workspace(task.getProjectId(), requirementWorkspaceKey(task));
        run.setUserPrompt(prompt);
        run.setState(RunState.RUNNING);
        run.setStartedDate(new Date());
        run.setWorktreePath(workspace.pathString());
        run.setBaseCommit(codingTaskChangeCollector.resolveHead(workspace.pathString()));
        run.setAgentId(agent.getId());
        run.setAgentName(agent.getName());
        run.setCliTool(agent.getCliTool());
        run.setModel(agent.getModel());
        bindProgress(run, codingTaskId, task.getRequirementId(), task.getLoopId(), prompt, run.getBaseCommit());
        runNumberService.assign(run);
        run = runDao.save(run);

        final Run trackedRun = run;
        String fullPrompt = buildExecutionPrompt(task, prompt, run) + safeProgressBrief(run.getExecutionId());
        AgentExecutionRequest request = buildRequest(run, task, fullPrompt, agentName);
        startAgentAfterCommit(agentName, request, trackedRun, task, true);

        CodingTaskDto dto = new CodingTaskDto();
        dto.setId(task.getId());
        dto.setStatus(task.getStatus());
        dto.setRevisionSeq(task.getRevisionSeq());
        dto.setSupersedesTaskId(task.getSupersedesTaskId());
        dto.setDispositionReason(task.getDispositionReason());
        return ResultData.success(dto);
    }

    private void startAgentAfterCommit(String agentName, AgentExecutionRequest request, Run run, CodingTask task,
                                       boolean schedulerManaged) {
        runAfterCurrentTransactionCommit(() -> {
            CompletableFuture<AgentExecutionResult> future = agentExecutionService.executeAsync(agentName, request);
            future.thenAccept(result -> {
                CompletionDecision decision = decideCompletion(run, result);
                if (decision.deferred()) {
                    // 工作区租约繁忙：任务回退 PENDING 并重新调度，不计失败、不消耗重试（方案 §6.2）。
                    boolean deferred = inNewTransaction(() ->
                            handleDeferredRun(run, task, decision.summary()));
                    if (deferred && schedulerManaged) {
                        eventPublisher.publishEvent(new CodingTaskSchedulingEvents.ScheduleRequested(
                                task.getRequirementId()));
                    }
                    return;
                }
                boolean settled = inNewTransaction(() -> finishRun(run, task, decision.success(),
                        decision.summary(), decision.failureReason(), schedulerManaged));
                if (settled && schedulerManaged) {
                    eventPublisher.publishEvent(new CodingTaskSchedulingEvents.DevelopmentFinished(
                            task.getId(), decision.success(), decision.failureReason()));
                }
            }).exceptionally(e -> {
                log.error("coding-task execute failed taskId={}", task.getId(), e);
                String failure = rootMessage(e);
                boolean settled = inNewTransaction(() ->
                        finishRun(run, task, false, failure, failure, schedulerManaged));
                if (settled && schedulerManaged) {
                    eventPublisher.publishEvent(new CodingTaskSchedulingEvents.DevelopmentFinished(
                            task.getId(), false, failure));
                }
                return null;
            });
        });
    }

    /**
     * 处理工作区租约繁忙：把任务从 RUNNING 回退 PENDING 并重发调度事件。
     * Run 标记为 CANCELLED（租约繁忙非真实执行），不写失败摘要、不增加 retryCount。
     */
    private boolean handleDeferredRun(Run run, CodingTask task, String reason) {
        Run persistedRun = runDao.findOne(run.getId());
        if (persistedRun != null && persistedRun.getState() == RunState.RUNNING) {
            persistedRun.setState(RunState.CANCELLED);
            persistedRun.setTerminalReason(RunTerminalReason.CANCELLED);
            persistedRun.setFinishedDate(new Date());
            persistedRun.setSummary("工作区租约繁忙，执行推迟：" + reason);
            runDao.save(persistedRun);
        }
        CodingTask persistedTask = codingTaskDao.findOne(task.getId());
        if (persistedTask != null && persistedTask.getStatus() == CodingTaskStatus.RUNNING) {
            persistedTask.setStatus(CodingTaskStatus.PENDING);
            // 关键：不调用 failureInfoSupport.markRetrying / markCodingTaskFailure，retryCount 保持不变。
            codingTaskDao.save(persistedTask);
            log.info("coding-task deferred due to busy workspace, reverted to PENDING. taskId={}", task.getId());
            return true;
        }
        return false;
    }

    private <T> T inNewTransaction(Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(ignored -> action.get());
    }

    /**
     * 在当前事务真正提交后启动外部进程。
     *
     * <p>不能使用 sei-core {@code TransactionUtil.afterCommit}：当本方法位于另一个
     * after-commit 回调开启的 {@code REQUIRES_NEW} 事务中时，该工具会因其
     * {@code IN_COMMIT} 标记而立即执行，导致 Agent 在 Run 提交前查询不到记录。</p>
     */
    private void runAfterCurrentTransactionCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private CompletionDecision decideCompletion(Run run, AgentExecutionResult result) {
        if (result == null) {
            return CompletionDecision.failed("执行返回空结果", "执行返回空结果");
        }
        // 工作区租约繁忙：调度延迟，不计为失败，不消耗重试次数（方案 §6.2）。
        // 把任务回退到 PENDING，等待下一轮调度重新获取租约。
        if (result.deferred()) {
            return CompletionDecision.deferred(result.failureReason());
        }
        String summary = firstNonBlank(result.output(), result.failureReason());
        if (!result.succeeded()) {
            String failure = firstNonBlank(summary, "Agent 执行失败");
            return CompletionDecision.failed(summary, failure);
        }
        if (result.output() == null) {
            return CompletionDecision.failed(summary, "执行返回空结果");
        }
        recordProgressBestEffort(run, summary);
        return CompletionDecision.ok(summary);
    }

    private AgentExecutionRequest buildRequest(Run run, CodingTask task, String prompt, String agentName) {
        AgentExecutionRequest request = new AgentExecutionRequest();
        request.setRunId(run.getId());
        request.setProjectId(task.getProjectId());
        request.setRequirementId(task.getRequirementId());
        request.setLogStreamKey(task.getRequirementId());
        request.setLoopId(task.getLoopId());
        request.setCodingTaskId(task.getId());
        request.setPrompt(prompt);
        request.setTriggerSource(run.getTriggerSource());
        request.setMemoryContextId(run.getMemoryContextId());
        request.setWorkspaceMemoryId(run.getWorkspaceMemoryId());
        request.setParentRunId(run.getParentRunId());
        request.setAttemptNo(run.getAttemptNo());
        if (agentName == null || agentName.isBlank()) {
            request.setTimeoutSeconds(1_800L);
        }
        return request;
    }

    private String requirementWorkspaceKey(CodingTask task) {
        if (task.getRequirementId() != null && !task.getRequirementId().isBlank()) {
            return workspaceManager.requirementWorkspaceKey(task.getRequirementId());
        }
        return "coding-task-" + task.getId();
    }

    private record CompletionDecision(boolean success, String summary, String failureReason, boolean deferred) {
        static CompletionDecision ok(String summary) {
            return new CompletionDecision(true, summary, null, false);
        }

        static CompletionDecision failed(String summary, String failureReason) {
            return new CompletionDecision(false, summary, failureReason, false);
        }

        static CompletionDecision deferred(String reason) {
            return new CompletionDecision(false, reason, reason, true);
        }
    }

    /**
     * 把 Run 绑定到进度账本（EXE-004+EXE-005）：解析 invocationKey + 在 RequirementWorkspace 就绪时绑定 Execution。
     * workspace 不存在时通过 WorkspaceLeaseService 创建（EXE-005：bindOrResolveWorkspace）。
     */
    private CodingTaskProgressIntegrator.ProgressPreflight bindProgress(Run run, String codingTaskId,
                              String requirementId, String loopId, String prompt, String baseCommit) {
        try {
            String workspaceId = requirementWorkspaceDao.findByRequirementId(requirementId)
                    .map(RequirementWorkspace::getId).orElse(null);
            // EXE-005: workspace 不存在时自动创建（bindOrResolve 保证同一需求只有一个 workspace）
            if (workspaceId == null) {
                Requirement requirement = requirementService.findOne(requirementId);
                String projectId = requirement != null ? requirement.getProjectId() : null;
                if (projectId != null) {
                    RequirementWorkspace ws = workspaceLeaseService.bindOrResolveWorkspace(projectId, requirementId);
                    if (ws != null) {
                        workspaceId = ws.getId();
                    }
                }
            }
            CodingTaskProgressIntegrator.ProgressPreflight preflight = codingTaskProgressIntegrator.preflight(
                    codingTaskId, requirementId, TaskExecutionType.CODING_TASK, loopId, 1, prompt, workspaceId, baseCommit);
            run.setInvocationKey(preflight.invocationKey());
            if (preflight.executionId() != null) {
                run.setExecutionId(preflight.executionId());
                run.setObservedPlanVersion(1);
            }
            return preflight;
        } catch (Exception e) {
            log.warn("coding-task progress preflight failed; continue without ledger. taskId={}", codingTaskId, e);
            return new CodingTaskProgressIntegrator.ProgressPreflight(null, null, null, null);
        }
    }

    private void finishRun(Run run, CodingTask task, boolean success, String failureReason) {
        finishRun(run, task, success, failureReason, failureReason, false);
    }

    private void finishRun(Run run, CodingTask task, boolean success, String summary, String failureReason) {
        finishRun(run, task, success, summary, failureReason, false);
    }

    private void finishRun(Run run, CodingTask task, boolean success, String failureReason,
                           boolean schedulerManaged) {
        boolean settled = finishRun(run, task, success, failureReason, failureReason, schedulerManaged);
        if (settled && schedulerManaged) {
            eventPublisher.publishEvent(new CodingTaskSchedulingEvents.DevelopmentFinished(
                    task.getId(), success, failureReason));
        }
    }

    private boolean finishRun(Run run, CodingTask task, boolean success, String summary, String failureReason,
                              boolean schedulerManaged) {
        Date now = new Date();
        Run persistedRun = runDao.findOne(run.getId());
        CodingTask persistedTask = codingTaskDao.findOne(task.getId());
        if (persistedRun == null || persistedTask == null) {
            log.warn("finishRun skipped because run/task disappeared. runId={}, taskId={}",
                    run.getId(), task.getId());
            return false;
        }
        if (Boolean.TRUE.equals(persistedRun.getCancelRequested())) {
            persistedRun.setState(RunState.CANCELLED);
            persistedRun.setTerminalReason(RunTerminalReason.CANCELLED);
            persistedRun.setFinishedDate(now);
            persistedRun.setSummary(firstNonBlank(summary, persistedRun.getSummary()));
            persistedRun.setFailureReason(firstNonBlank(failureReason, persistedRun.getFailureReason()));
            runDao.save(persistedRun);
            // 计划修订已将源任务替代时，晚到的进程回调不得把 SUPERSEDED 覆盖成 CANCELLED。
            if (persistedTask.getStatus() != CodingTaskStatus.SUPERSEDED) {
                persistedTask.setStatus(CodingTaskStatus.CANCELLED);
                codingTaskDao.save(persistedTask);
            }
            return false;
        }
        if (persistedRun.getLoopId() != null && persistedTask.getLoopId() != null
                && !Objects.equals(persistedRun.getLoopId(), persistedTask.getLoopId())) {
            persistedTask.setStatus(CodingTaskStatus.STALE);
            codingTaskDao.save(persistedTask);
            return false;
        }
        if (persistedRun.getState() != RunState.RUNNING || persistedTask.getStatus() != CodingTaskStatus.RUNNING) {
            log.info("finishRun skipped because run/task already settled. runId={}, runState={}, taskId={}, taskStatus={}",
                    persistedRun.getId(), persistedRun.getState(), persistedTask.getId(), persistedTask.getStatus());
            return false;
        }

        persistedRun.setState(success ? RunState.SUCCEEDED : RunState.FAILED);
        persistedRun.setTerminalReason(success ? RunTerminalReason.SUCCEEDED : RunTerminalReason.FAILED);
        persistedRun.setFinishedDate(now);
        persistedRun.setSummary(summary);
        if (success) {
            persistedRun.setFailureReason(null);
            failureInfoSupport.clearCodingTaskFailure(persistedTask);
        } else {
            persistedRun.setFailureReason(failureReason);
            String propagatedFailure = firstNonBlank(failureReason, summary, "编码执行失败");
            failureInfoSupport.markCodingTaskFailure(persistedTask, propagatedFailure,
                    propagatedFailure, persistedRun.getTriggerSource(), now);
        }
        runDao.save(persistedRun);

        appendTerminalObservationBestEffort(persistedRun, success, summary, failureReason);

        if (schedulerManaged) {
            return true;
        }

        persistedTask.setStatus(success ? CodingTaskStatus.SUCCEEDED : CodingTaskStatus.FAILED);
        codingTaskDao.save(persistedTask);

        if (success && persistedTask.getExecutionPlanId() == null) {
            submitMemoryUpdateJob(persistedTask, persistedRun);
        }
        return false;
    }

    /**
     * CodingTask 成功后投递 MEMORY_UPDATE_AFTER_CODING_TASK job。失败只记录日志，不影响 CodingTask 成功状态。
     */
    private void submitMemoryUpdateJob(CodingTask task, Run run) {
        try {
            WorkspaceMemory current = workspaceMemoryService.findCurrent(task.getProjectId());
            String baseWorkspaceMemoryId = current == null ? null : current.getId();
            String idempotencyKey = task.getProjectId() + ":" + task.getId() + ":" + run.getId() + ":" + baseWorkspaceMemoryId;
            OperateResultWithData<MemoryJob> result = memoryJobService.submit(
                    task.getProjectId(),
                    MemoryJobType.MEMORY_UPDATE_AFTER_CODING_TASK,
                    MemoryJobTriggerSource.CODING_TASK_SUCCEEDED,
                    idempotencyKey,
                    task.getRequirementId(),
                    task.getId(),
                    run.getId(),
                    baseWorkspaceMemoryId);
            if (result.successful()) {
                log.info("coding-task: 已投递记忆回写 job taskId={}, runId={}, baseMemoryId={}",
                        task.getId(), run.getId(), baseWorkspaceMemoryId);
            } else {
                log.warn("coding-task: 投递记忆回写 job 失败 taskId={}, reason={}",
                        task.getId(), result.getMessage());
            }
        } catch (Exception e) {
            log.warn("coding-task: 投递记忆回写 job 异常 taskId={}", task.getId(), e);
        }
    }

    private String buildExecutionPrompt(CodingTask task, String userPrompt, Run run) {
        Requirement requirement = requirementService.findOne(task.getRequirementId());
        ExecutionPlan plan = task.getExecutionPlanId() == null ? null : executionPlanDao.findOne(task.getExecutionPlanId());
        RequirementDesignContext context = resolveContext(requirement);
        String designContextSection = designContextPromptAssembler.assemble(context);

        StringBuilder sb = new StringBuilder();
        sb.append("PRD：").append(requirement == null ? "" : requirement.getPrdContent()).append('\n');
        sb.append("执行计划：").append(plan == null ? "" : plan.getPlanJson()).append('\n');
        sb.append("\n").append(designContextSection).append("\n");
        sb.append("编码任务：").append(task.getTitle()).append('\n');
        sb.append("任务描述：").append(task.getDescription()).append('\n');
        if (task.getFileScope() != null) {
            sb.append("文件范围：").append(task.getFileScope()).append('\n');
        }

        if (requirementCommentService != null) {
            sb.append("历史评论：\n");
            requirementCommentService.findByRequirementId(task.getRequirementId()).forEach(comment ->
                    sb.append('[').append(comment.getAuthorType()).append('/').append(comment.getCommentType())
                            .append("] ").append(Objects.toString(comment.getContent(), "")).append('\n'));
        }

        Run previousRun = run != null && run.getParentRunId() != null
                ? runDao.findOne(run.getParentRunId()) : null;
        if (previousRun != null) {
            int recoveryAttemptNo = run == null
                    ? Objects.requireNonNullElse(previousRun.getAttemptNo(), 1) + 1
                    : Objects.requireNonNullElse(run.getAttemptNo(), 1);
            sb.append("\n恢复执行上下文：\n");
            sb.append("- 上一次 Run：").append(previousRun.getId())
                    .append("，状态：").append(previousRun.getState())
                    .append("，终止原因：").append(previousRun.getTerminalReason()).append('\n');
            if (previousRun.getFailureReason() != null) {
                sb.append("- 上一次失败原因：").append(previousRun.getFailureReason()).append('\n');
            }
            sb.append("- 这是同一计划任务的第 ")
                    .append(recoveryAttemptNo)
                    .append(" 次执行尝试。先检查现有代码、Git 差异和测试结果，保留已正确完成的部分，")
                    .append("只继续未完成或未通过验收的部分；不要无条件重做整个任务。\n");
            appendHandoffSnapshot(sb, task);
        }
        if (userPrompt != null && !userPrompt.isBlank()) {
            sb.append("用户补充提示：").append(userPrompt).append('\n');
        }
        String worktreePath = run == null ? null : run.getWorktreePath();
        if (worktreePath == null) {
            WorkspaceResolveResult workspace = workspaceManager.resolve(task.getProjectId());
            worktreePath = workspace == null ? null : workspace.getPath();
        }
        if (worktreePath != null) {
            CodingTaskChangeResult changes = codingTaskChangeCollector.collect(worktreePath, null);
            if (changes != null && changes.isSuccess()) {
                sb.append("当前工作区差异：").append(Objects.toString(changes.getDiffSummary(), "")).append('\n');
            }
        }
        sb.append("请在已解析的工作区中按上述上下文执行编码，只修改任务范围内的文件。");
        if (previousRun == null) {
            sb.append("本任务必须在工作区落地至少一个代码或文档文件变更；");
            sb.append("仅分析、说明、报告完成但不修改文件会被系统判定为开发失败。");
        } else {
            sb.append("恢复执行允许复用上一次 Run 已落地的有效变更，但必须检查并完成当前任务验收；");
            sb.append("如果现有成果已经满足任务要求，不要为了制造新差异而重复改写，请运行必要验证并明确给出证据。");
        }
        return sb.toString();
    }

    private void appendHandoffSnapshot(StringBuilder sb, CodingTask task) {
        if (task.getSupersedesTaskId() == null || taskHandoffSnapshotDao == null) {
            return;
        }
        taskHandoffSnapshotDao.findTopByCodingTaskIdOrderByRevisionSeqDesc(task.getSupersedesTaskId())
                .ifPresent(snapshot -> {
                    sb.append("- 修订交接快照（源任务 ").append(task.getSupersedesTaskId()).append("）：\n");
                    appendSnapshotValue(sb, "运行摘要", snapshot.getRunSummary());
                    appendSnapshotValue(sb, "差异统计", snapshot.getDiffStat());
                    appendSnapshotValue(sb, "差异摘要", snapshot.getDiffSummary());
                    appendSnapshotValue(sb, "进度账本", snapshot.getProgressSnapshotJson());
                });
    }

    private void appendSnapshotValue(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("  - ").append(label).append("：").append(value).append('\n');
        }
    }

    private void linkPreviousAttempt(Run run, Run previousRun) {
        if (previousRun == null) {
            run.setAttemptNo(1);
            return;
        }
        run.setParentRunId(previousRun.getId());
        run.setAttemptNo(Objects.requireNonNullElse(previousRun.getAttemptNo(), 1) + 1);
    }

    private Run findLatestRun(String codingTaskId) {
        return runDao.findByCodingTaskId(codingTaskId).stream()
                .max((left, right) -> Integer.compare(
                        Objects.requireNonNullElse(left.getRunNo(), 0),
                        Objects.requireNonNullElse(right.getRunNo(), 0)))
                .orElse(null);
    }

    private Run findPreviousRun(CodingTask task) {
        Run previousRun = findLatestRun(task.getId());
        if (previousRun == null && task.getSupersedesTaskId() != null) {
            previousRun = findLatestRun(task.getSupersedesTaskId());
        }
        return previousRun;
    }

    private void recordProgressBestEffort(Run run, String summary) {
        try {
            if (!codingTaskProgressIntegrator.recordSuccessfulCodingTaskCompletion(run, summary)) {
                log.warn("coding-task progress ledger update incomplete; task success is not blocked. runId={}", run.getId());
            }
        } catch (Exception e) {
            log.warn("coding-task progress ledger update failed; task success is not blocked. runId={}", run.getId(), e);
        }
    }

    private void appendTerminalObservationBestEffort(Run run, boolean success, String summary, String failureReason) {
        try {
            codingTaskProgressIntegrator.appendTerminalObservation(run, success, summary, failureReason);
        } catch (Exception e) {
            log.warn("coding-task terminal ledger observation failed; run settlement is not blocked. runId={}",
                    run == null ? null : run.getId(), e);
        }
    }

    private String safeProgressBrief(String executionId) {
        try {
            return Objects.requireNonNullElse(codingTaskProgressIntegrator.buildProgressBrief(executionId), "");
        } catch (Exception e) {
            log.warn("coding-task progress brief unavailable; continue with plan/workspace recovery. executionId={}",
                    executionId, e);
            return "";
        }
    }

    private RequirementDesignContext resolveContext(Requirement requirement) {
        if (requirement == null) {
            return null;
        }
        RequirementDesignContext context = requirementDesignContextService
                .findCurrentByRequirement(requirement.getId());
        if (isReady(context)) {
            return context;
        }
        return requirementDesignContextService.prepare(requirement.getId());
    }

    private boolean isReady(RequirementDesignContext context) {
        return context != null
                && context.getContextStatus() == com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus.READY;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
