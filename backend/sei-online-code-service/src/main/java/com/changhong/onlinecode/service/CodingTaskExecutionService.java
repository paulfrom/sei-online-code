package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.AgentWorkspace;
import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.CodingTaskDto;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
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
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.memory.CodingTaskChangeCollector;
import com.changhong.onlinecode.service.memory.CodingTaskChangeResult;
import com.changhong.onlinecode.service.memory.WorkspaceChangeDetector;
import com.changhong.onlinecode.service.agent.AgentExecutionRequest;
import com.changhong.onlinecode.service.agent.AgentExecutionResult;
import com.changhong.onlinecode.service.agent.AgentExecutionService;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import com.changhong.sei.core.utils.TransactionUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
    private final WorkspaceChangeDetector workspaceChangeDetector;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Requests logical cancellation and best-effort process termination.
     */
    public void cancelRun(String runId) {
        Run run = runDao.findOne(runId);
        if (run == null) {
            return;
        }
        run.setCancelRequested(Boolean.TRUE);
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
        if (hasActiveRun(id)) {
            return ResultData.fail("任务已有正在执行的 Run");
        }

        task.setStatus(CodingTaskStatus.RUNNING);
        codingTaskDao.save(task);

        Agent agent = agentService.findByName(task.getAssignedAgent());
        Run run = new Run();
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
        runNumberService.assign(run);
        run = runDao.save(run);

        String fullPrompt = buildExecutionPrompt(task, prompt);

        WorkspaceChangeDetector.Snapshot baseline = workspaceChangeDetector.snapshot(workspace.pathString());

        final Run trackedRun = run;
        AgentExecutionRequest request = buildRequest(run, task, fullPrompt, task.getAssignedAgent());
        startAgentAfterCommit(task.getAssignedAgent(), request, trackedRun, task, baseline, false);

        CodingTaskDto dto = new CodingTaskDto();
        dto.setId(task.getId());
        dto.setStatus(task.getStatus());
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
        if (hasActiveRun(codingTaskId)) {
            return ResultData.fail("任务已有正在执行的 Run");
        }

        Agent agent = agentService.findByName(agentName);
        if (agent == null) {
            log.error("coding-task plan agent not found taskId={}, agentName={}", codingTaskId, agentName);
            task.setStatus(CodingTaskStatus.FAILED);
            task.setFailureSummary("开发代理未找到");
            task.setFailureDetail("agentName=" + agentName);
            task.setLastFailedAt(new Date());
            codingTaskDao.save(task);
            eventPublisher.publishEvent(new CodingTaskSchedulingEvents.ScheduleRequested(task.getRequirementId()));
            return ResultData.fail("开发代理不存在: " + agentName);
        }

        task.setStatus(CodingTaskStatus.RUNNING);
        codingTaskDao.save(task);

        Run run = new Run();
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
        runNumberService.assign(run);
        run = runDao.save(run);

        WorkspaceChangeDetector.Snapshot baseline = workspaceChangeDetector.snapshot(workspace.pathString());

        final Run trackedRun = run;
        AgentExecutionRequest request = buildRequest(run, task, buildExecutionPrompt(task, prompt), agentName);
        startAgentAfterCommit(agentName, request, trackedRun, task, baseline, true);

        CodingTaskDto dto = new CodingTaskDto();
        dto.setId(task.getId());
        dto.setStatus(task.getStatus());
        return ResultData.success(dto);
    }

    private void startAgentAfterCommit(String agentName, AgentExecutionRequest request, Run run, CodingTask task,
                                       WorkspaceChangeDetector.Snapshot baseline, boolean schedulerManaged) {
        TransactionUtil.afterCommit(() -> {
            CompletableFuture<AgentExecutionResult> future = agentExecutionService.executeAsync(agentName, request);
            future.thenAccept(result -> {
                CompletionDecision decision = decideCompletion(run, result, baseline);
                finishRun(run, task, decision.success(), decision.summary(), decision.failureReason(), schedulerManaged);
            }).exceptionally(e -> {
                log.error("coding-task execute failed taskId={}", task.getId(), e);
                finishRun(run, task, false, rootMessage(e), rootMessage(e), schedulerManaged);
                return null;
            });
        });
    }

    private CompletionDecision decideCompletion(Run run, AgentExecutionResult result,
                                                WorkspaceChangeDetector.Snapshot baseline) {
        if (result == null) {
            return CompletionDecision.failed("执行返回空结果", "执行返回空结果");
        }
        String summary = firstNonBlank(result.output(), result.failureReason());
        if (!result.succeeded()) {
            String failure = firstNonBlank(summary, "Agent 执行失败");
            return CompletionDecision.failed(summary, failure);
        }
        if (result.output() == null) {
            return CompletionDecision.failed(summary, "执行返回空结果");
        }
        if (result.output().contains("FAILED")) {
            return CompletionDecision.failed(summary, summary);
        }
        List<String> changedFiles = workspaceChangeDetector.changedFiles(baseline, run.getWorktreePath());
        if (changedFiles.isEmpty()) {
            return CompletionDecision.failed(summary, "开发代理未在指定工作区产生代码或文档变更");
        }
        if (log.isDebugEnabled()) {
            log.debug("coding-task: detected workspace changes runId={}, files={}",
                    run.getId(), changedFiles);
            return CompletionDecision.ok(summary);
        }
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

    private record CompletionDecision(boolean success, String summary, String failureReason) {
        static CompletionDecision ok(String summary) {
            return new CompletionDecision(true, summary, null);
        }

        static CompletionDecision failed(String summary, String failureReason) {
            return new CompletionDecision(false, summary, failureReason);
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
        finishRun(run, task, success, failureReason, failureReason, schedulerManaged);
    }

    private void finishRun(Run run, CodingTask task, boolean success, String summary, String failureReason,
                           boolean schedulerManaged) {
        Date now = new Date();
        Run persistedRun = runDao.findOne(run.getId());
        CodingTask persistedTask = codingTaskDao.findOne(task.getId());
        if (persistedRun == null || persistedTask == null) {
            log.warn("finishRun skipped because run/task disappeared. runId={}, taskId={}",
                    run.getId(), task.getId());
            return;
        }
        if (Boolean.TRUE.equals(persistedRun.getCancelRequested())) {
            persistedRun.setState(RunState.CANCELLED);
            persistedRun.setTerminalReason(RunTerminalReason.CANCELLED);
            persistedRun.setFinishedDate(now);
            runDao.save(persistedRun);
            persistedTask.setStatus(CodingTaskStatus.CANCELLED);
            codingTaskDao.save(persistedTask);
            return;
        }
        if (persistedRun.getLoopId() != null && persistedTask.getLoopId() != null
                && !Objects.equals(persistedRun.getLoopId(), persistedTask.getLoopId())) {
            persistedTask.setStatus(CodingTaskStatus.STALE);
            codingTaskDao.save(persistedTask);
            return;
        }
        if (persistedRun.getState() != RunState.RUNNING || persistedTask.getStatus() != CodingTaskStatus.RUNNING) {
            log.info("finishRun skipped because run/task already settled. runId={}, runState={}, taskId={}, taskStatus={}",
                    persistedRun.getId(), persistedRun.getState(), persistedTask.getId(), persistedTask.getStatus());
            return;
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

        if (schedulerManaged) {
            eventPublisher.publishEvent(new CodingTaskSchedulingEvents.DevelopmentFinished(
                    persistedTask.getId(), success, failureReason));
            return;
        }

        persistedTask.setStatus(success ? CodingTaskStatus.SUCCEEDED : CodingTaskStatus.FAILED);
        codingTaskDao.save(persistedTask);

        if (success && persistedTask.getExecutionPlanId() == null) {
            submitMemoryUpdateJob(persistedTask, persistedRun);
        }
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

    private String buildExecutionPrompt(CodingTask task, String userPrompt) {
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

        Run lastFailed = findLastFailedRun(task.getId());
        if (lastFailed != null && lastFailed.getFailureReason() != null) {
            sb.append("上一次失败原因：").append(lastFailed.getFailureReason()).append('\n');
        }
        if (userPrompt != null && !userPrompt.isBlank()) {
            sb.append("用户补充提示：").append(userPrompt).append('\n');
        }
        WorkspaceResolveResult workspace = workspaceManager.resolve(task.getProjectId());
        if (workspace != null) {
            com.changhong.onlinecode.service.memory.CodingTaskChangeResult changes =
                    codingTaskChangeCollector.collect(workspace.getPath(), null);
            if (changes.isSuccess()) {
                sb.append("当前工作区差异：").append(Objects.toString(changes.getDiffSummary(), "")).append('\n');
            }
        }
        sb.append("请在已解析的工作区中按上述上下文执行编码，只修改任务范围内的文件。");
        sb.append("本任务必须在工作区落地至少一个代码或文档文件变更；");
        sb.append("仅分析、说明、报告完成但不修改文件会被系统判定为开发失败。");
        return sb.toString();
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

    private Run findLastFailedRun(String codingTaskId) {
        List<Run> runs = runDao.findByCodingTaskId(codingTaskId);
        return runs.stream()
                .filter(r -> r.getState() == RunState.FAILED)
                .reduce((a, b) -> Objects.requireNonNullElse(a.getRunNo(), 0)
                        > Objects.requireNonNullElse(b.getRunNo(), 0) ? a : b)
                .orElse(null);
    }

    private boolean hasActiveRun(String codingTaskId) {
        List<Run> runs = runDao.findByCodingTaskId(codingTaskId);
        return runs.stream().anyMatch(r -> r.getState() == RunState.RUNNING);
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
