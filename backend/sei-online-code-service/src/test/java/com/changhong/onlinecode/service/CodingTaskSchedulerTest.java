package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.service.revision.apply.EffectiveTaskGraph;
import com.changhong.onlinecode.service.revision.apply.EffectiveTaskGraphResolver;
import com.changhong.onlinecode.service.validation.ValidationLoopService;
import com.changhong.onlinecode.service.memory.CodingTaskChangeCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CodingTaskScheduler 单元测试。
 */
class CodingTaskSchedulerTest {

    private CodingTaskDao codingTaskDao;
    private RequirementDao requirementDao;
    private CodingTaskExecutionService executionService;
    private RunDao runDao;
    private ApplicationEventPublisher eventPublisher;
    private RequirementCommentService requirementCommentService;
    private ValidationLoopService validationLoopService;
    private EffectiveTaskGraphResolver effectiveTaskGraphResolver;
    private com.changhong.onlinecode.service.validation.ValidationTaskExecutionService validationTaskExecutionService;
    private com.changhong.onlinecode.service.review.TaskDeliveryReviewService taskDeliveryReviewService;
    private CodingTaskScheduler scheduler;

    private final AtomicInteger savedTasks = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        codingTaskDao = mock(CodingTaskDao.class);
        requirementDao = mock(RequirementDao.class);
        executionService = mock(CodingTaskExecutionService.class);
        runDao = mock(RunDao.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        requirementCommentService = mock(RequirementCommentService.class);
        validationLoopService = mock(ValidationLoopService.class);
        effectiveTaskGraphResolver = mock(EffectiveTaskGraphResolver.class);
        validationTaskExecutionService = mock(com.changhong.onlinecode.service.validation.ValidationTaskExecutionService.class);
        taskDeliveryReviewService = mock(com.changhong.onlinecode.service.review.TaskDeliveryReviewService.class);
        // 默认：无未决审阅（不触发门禁），依赖任务视为已 APPROVE（保留原调度语义）。
        // 针对新门禁/依赖语义的专门测试可在用例内覆盖这些 stub。
        when(taskDeliveryReviewService.hasOpenReview(anyString(), anyString())).thenReturn(false);
        when(taskDeliveryReviewService.isApproved(anyString())).thenReturn(true);
        scheduler = new CodingTaskScheduler(codingTaskDao, requirementDao, executionService,
                runDao, mock(CodingTaskChangeCollector.class),
                eventPublisher, requirementCommentService, validationLoopService,
                validationTaskExecutionService,
                effectiveTaskGraphResolver,
                taskDeliveryReviewService);
        when(runDao.findByCodingTaskId(anyString())).thenReturn(List.of());

        when(codingTaskDao.save(any(CodingTask.class))).thenAnswer(invocation -> {
            savedTasks.incrementAndGet();
            return invocation.getArgument(0);
        });
        when(effectiveTaskGraphResolver.resolveLatest(anyString(), anyString())).thenAnswer(invocation -> {
            String requirementId = invocation.getArgument(0);
            String loopId = invocation.getArgument(1);
            List<CodingTask> tasks = codingTaskDao.findByRequirementId(requirementId).stream()
                    .filter(task -> loopId.equals(task.getLoopId()))
                    .toList();
            Map<String, CodingTask> byKey = new LinkedHashMap<>();
            tasks.forEach(task -> byKey.put(task.getPlanTaskKey(), task));
            return new EffectiveTaskGraph(0L, tasks, byKey);
        });
    }

    @Test
    void schedule_startsNewTransactionWhenInvokedFromAfterCommitCallback() throws Exception {
        Method entryPoint = CodingTaskScheduler.class.getMethod("schedule", String.class);
        Transactional transactional = entryPoint.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    @Test
    void schedule_publishesCompletionEventWithoutDependingOnAutomationService() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of());

        scheduler.schedule("req-1");

        verify(eventPublisher).publishEvent(new CodingTaskScheduler.SchedulingPassCompletedEvent("req-1"));
    }

    @Test
    void schedule_startsOnlyWhenDependenciesSatisfied() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);

        CodingTask a = task("task-a", "FE-001", "frontend", List.of(), CodingTaskStatus.SUCCEEDED);
        CodingTask b = task("task-b", "FE-002", "frontend", List.of("FE-001"), CodingTaskStatus.PENDING);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(a, b));

        scheduler.schedule("req-1");

        verify(executionService).executePlanTask(eq("task-b"), anyString(), anyString());
    }

    @Test
    void schedule_blocksTaskWhenDependencyFailed() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);

        CodingTask a = task("task-a", "FE-001", "frontend", List.of(), CodingTaskStatus.FAILED);
        CodingTask b = task("task-b", "FE-002", "frontend", List.of("FE-001"), CodingTaskStatus.PENDING);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(a, b));

        scheduler.schedule("req-1");

        verify(executionService, never()).executePlanTask(eq("task-b"), anyString(), anyString());
        ArgumentCaptor<CodingTask> captor = ArgumentCaptor.forClass(CodingTask.class);
        verify(codingTaskDao, times(1)).save(captor.capture());
        assertEquals(CodingTaskStatus.BLOCKED, captor.getValue().getStatus());
    }

    @Test
    void schedule_laneContentionLeavesTaskPending() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);

        CodingTask running = task("task-a", "FE-001", "frontend", List.of(), CodingTaskStatus.RUNNING);
        running.setAssignedAgent("frontend-dev-agent");
        CodingTask pending = task("task-b", "FE-002", "frontend", List.of(), CodingTaskStatus.PENDING);
        pending.setAssignedAgent("frontend-dev-agent");
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(running, pending));

        scheduler.schedule("req-1");

        verify(executionService, never()).executePlanTask(eq("task-b"), anyString(), anyString());
        verify(codingTaskDao, never()).save(pending);
        assertEquals(CodingTaskStatus.PENDING, pending.getStatus());
    }

    @Test
    void schedule_fileScopeContentionLeavesTaskPending() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);

        CodingTask running = task("task-a", "BE-001", "backend", List.of(), CodingTaskStatus.RUNNING);
        running.setFileScope(List.of("backend/src/main/java/com/changhong/onlinecode/service"));
        running.setAssignedAgent("backend-dev-agent");
        CodingTask pending = task("task-b", "BE-002", "backend", List.of(), CodingTaskStatus.PENDING);
        pending.setFileScope(List.of("backend/src/main/java/com/changhong/onlinecode/service/UserService.java"));
        pending.setAssignedAgent("backend-dev-agent");
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(running, pending));

        scheduler.schedule("req-1");

        verify(executionService, never()).executePlanTask(eq("task-b"), anyString(), anyString());
        verify(codingTaskDao, never()).save(pending);
        assertEquals(CodingTaskStatus.PENDING, pending.getStatus());
    }

    @Test
    void onDevelopmentRunFinished_success_succeedsWithoutImplicitValidation() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask task = task("task-a", "FE-001", "frontend", List.of(), CodingTaskStatus.RUNNING);
        task.setAssignedAgent("frontend-dev-agent");
        when(codingTaskDao.findOne("task-a")).thenReturn(task);

        scheduler.onDevelopmentRunFinished("task-a", true, null);

        assertEquals(CodingTaskStatus.SUCCEEDED, task.getStatus());
        verify(validationLoopService, never()).validateTask(any());
    }

    @Test
    void schedule_validationTaskClaimsAndTriggersAsyncExecution() {
        // 新行为（方案 §7）：验证任务由 scheduler claim（置 VALIDATING）后异步执行 test-agent，
        // 不在 schedule() 事务内同步等待。claim 委托给 ValidationTaskExecutionService。
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask task = task("task-a", "VAL-001", "full-stack", List.of(), CodingTaskStatus.PENDING);
        task.setAssignedAgent("test-agent");
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));
        when(validationTaskExecutionService.claim(task)).thenReturn(true);

        scheduler.schedule("req-1");

        verify(validationTaskExecutionService).claim(task);
        verify(validationTaskExecutionService).executeAsync("task-a");
        verify(executionService, never()).executePlanTask(anyString(), anyString(), anyString());
    }

    @Test
    void schedule_marksStaleWhenLoopIdMismatch() {
        Requirement req = requirement("req-1", "loop-2");
        when(requirementDao.findOne("req-1")).thenReturn(req);

        CodingTask staleTask = task("task-a", "FE-001", "frontend", List.of(), CodingTaskStatus.PENDING);
        staleTask.setLoopId("loop-1");
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(staleTask));

        scheduler.schedule("req-1");

        assertEquals(CodingTaskStatus.STALE, staleTask.getStatus());
        verify(executionService, never()).executePlanTask(anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @EnumSource(value = RequirementRevisionState.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    void schedule_revisionStatePausesNewTasks(RequirementRevisionState revisionState) {
        Requirement req = requirement("req-1", "loop-1");
        req.setRevisionState(revisionState);
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask pending = task("task-a", "BE-001", "backend", List.of(), CodingTaskStatus.PENDING);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(pending));

        scheduler.schedule("req-1");

        verify(executionService, never()).executePlanTask(anyString(), anyString(), anyString());
        verify(effectiveTaskGraphResolver, never()).resolveLatest(anyString(), anyString());
        verify(eventPublisher, never()).publishEvent(any());
        assertEquals(CodingTaskStatus.PENDING, pending.getStatus());
    }

    @Test
    void onDevelopmentRunFinished_keepTaskSettlesButRevisionDoesNotLaunchDownstream() {
        Requirement req = requirement("req-1", "loop-1");
        req.setRevisionState(RequirementRevisionState.PLANNING);
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask running = task("task-a", "BE-001", "backend", List.of(), CodingTaskStatus.RUNNING);
        CodingTask downstream = task(
                "task-b", "BE-002", "backend", List.of("BE-001"), CodingTaskStatus.PENDING);
        when(codingTaskDao.findOne("task-a")).thenReturn(running);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(running, downstream));

        scheduler.onDevelopmentRunFinished("task-a", true, null);

        assertEquals(CodingTaskStatus.SUCCEEDED, running.getStatus());
        assertEquals(CodingTaskStatus.PENDING, downstream.getStatus());
        verify(executionService, never()).executePlanTask(eq("task-b"), anyString(), anyString());
    }

    @Test
    void schedule_usesKeepSourceFromEffectiveGraphWhenSameLoopContainsDuplicateHistoricalKey() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask keepSource = task(
                "keep-source", "BE-001", "backend", List.of(), CodingTaskStatus.SUCCEEDED);
        keepSource.setRevisionSeq(1L);
        CodingTask excludedHistoricalDuplicate = task(
                "excluded-duplicate", "BE-001", "backend", List.of(), CodingTaskStatus.FAILED);
        excludedHistoricalDuplicate.setRevisionSeq(2L);
        CodingTask currentTask = task(
                "current-task", "BE-002", "backend", List.of("BE-001"), CodingTaskStatus.PENDING);
        currentTask.setRevisionSeq(2L);
        when(codingTaskDao.findByRequirementId("req-1"))
                .thenReturn(List.of(keepSource, excludedHistoricalDuplicate, currentTask));
        when(effectiveTaskGraphResolver.resolveLatest("req-1", "loop-1"))
                .thenReturn(graph(2L, keepSource, currentTask));

        scheduler.schedule("req-1");

        verify(executionService).executePlanTask(eq("current-task"), anyString(), anyString());
        assertEquals(CodingTaskStatus.SUCCEEDED, keepSource.getStatus());
        assertEquals(CodingTaskStatus.FAILED, excludedHistoricalDuplicate.getStatus());
    }

    @Test
    void schedule_supersededTaskIsTerminalAndDoesNotFailDependentTask() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask superseded = task(
                "old-task", "BE-001", "backend", List.of(), CodingTaskStatus.SUPERSEDED);
        CodingTask dependent = task(
                "dependent", "BE-002", "backend", List.of("BE-001"), CodingTaskStatus.PENDING);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(superseded, dependent));
        when(effectiveTaskGraphResolver.resolveLatest("req-1", "loop-1"))
                .thenReturn(graph(2L, superseded, dependent));

        scheduler.schedule("req-1");

        verify(executionService, never()).executePlanTask(eq("old-task"), anyString(), anyString());
        verify(executionService, never()).executePlanTask(eq("dependent"), anyString(), anyString());
        verify(codingTaskDao, never()).save(dependent);
        assertEquals(CodingTaskStatus.PENDING, dependent.getStatus());
    }

    @Test
    void onDevelopmentRunFinished_doesNotOverwriteSupersededTask() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask superseded = task(
                "old-task", "BE-001", "backend", List.of(), CodingTaskStatus.SUPERSEDED);
        when(codingTaskDao.findOne("old-task")).thenReturn(superseded);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(superseded));

        scheduler.onDevelopmentRunFinished("old-task", false, "late failure");

        assertEquals(CodingTaskStatus.SUPERSEDED, superseded.getStatus());
        verify(codingTaskDao, never()).save(superseded);
    }

    @Test
    void schedule_ignoresSameTaskKeyFromStaleLoopWhenResolvingDependencies() {
        Requirement req = requirement("req-1", "loop-2");
        when(requirementDao.findOne("req-1")).thenReturn(req);

        CodingTask staleDependency = task(
                "old-dependency", "BE-002", "backend", List.of(), CodingTaskStatus.STALE);
        staleDependency.setLoopId("loop-1");
        CodingTask currentDependency = task(
                "current-dependency", "BE-002", "backend", List.of(), CodingTaskStatus.SUCCEEDED);
        currentDependency.setLoopId("loop-2");
        CodingTask blockedTask = task(
                "current-task", "BE-004", "backend", List.of("BE-002"), CodingTaskStatus.BLOCKED);
        blockedTask.setLoopId("loop-2");
        when(codingTaskDao.findByRequirementId("req-1"))
                .thenReturn(List.of(staleDependency, currentDependency, blockedTask));

        scheduler.schedule("req-1");

        assertEquals(CodingTaskStatus.PENDING, blockedTask.getStatus());
        verify(executionService).executePlanTask(eq("current-task"), anyString(), anyString());
    }

    @Test
    void schedule_validationTaskLoopChangesHandledByExecutorNotScheduler() {
        // 新行为（方案 §7）：loop 在验证期间变更的处理已移至 ValidationTaskExecutionService，
        // scheduler 只负责 claim + 触发异步执行，不再在 schedule() 内同步检测 loop 变更。
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask task = task("task-a", "VAL-001", "full-stack", List.of(), CodingTaskStatus.PENDING);
        task.setAssignedAgent("test-agent");
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));
        when(validationTaskExecutionService.claim(task)).thenReturn(true);

        scheduler.schedule("req-1");

        verify(validationTaskExecutionService).claim(task);
        verify(validationTaskExecutionService).executeAsync("task-a");
        // scheduler 不再同步调用 test-agent
        verify(validationLoopService, never()).validateTask(any());
    }

    @Test
    void schedule_pausedWhenOpenDeliveryReviewExists() {
        // 方案 §6.1 门禁：存在未决（PENDING/REVIEWING）交付审阅时，不启动后续任务。
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask task = task("task-a", "FE-001", "frontend", List.of(), CodingTaskStatus.PENDING);
        task.setAssignedAgent("frontend-dev-agent");
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));
        when(taskDeliveryReviewService.hasOpenReview("req-1", "loop-1")).thenReturn(true);

        scheduler.schedule("req-1");

        verify(executionService, never()).executePlanTask(anyString(), anyString(), anyString());
        // 仍发布调度完成事件，便于编排边界在审阅结算后进入计划验收。
        verify(eventPublisher).publishEvent(new CodingTaskScheduler.SchedulingPassCompletedEvent("req-1"));
    }

    @Test
    void schedule_downstreamNotStartedUntilDependencyApproved() {
        // 方案 §6.3：依赖任务 status==SUCCEEDED 且最新 review==APPROVE 才算依赖满足。
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask upstream = task("task-a", "BE-001", "backend", List.of(), CodingTaskStatus.SUCCEEDED);
        upstream.setAssignedAgent("backend-dev-agent");
        CodingTask downstream = task("task-b", "FE-001", "frontend", List.of("BE-001"), CodingTaskStatus.PENDING);
        downstream.setAssignedAgent("frontend-dev-agent");
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(upstream, downstream));
        // 上游已 SUCCEEDED 但尚未 APPROVE
        when(taskDeliveryReviewService.isApproved("task-a")).thenReturn(false);

        scheduler.schedule("req-1");

        verify(executionService, never()).executePlanTask(eq("task-b"), anyString(), anyString());
    }

    @Test
    void schedule_downstreamStartedOnceDependencyApproved() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask upstream = task("task-a", "BE-001", "backend", List.of(), CodingTaskStatus.SUCCEEDED);
        upstream.setAssignedAgent("backend-dev-agent");
        CodingTask downstream = task("task-b", "FE-001", "frontend", List.of("BE-001"), CodingTaskStatus.PENDING);
        downstream.setAssignedAgent("frontend-dev-agent");
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(upstream, downstream));
        when(taskDeliveryReviewService.isApproved("task-a")).thenReturn(true);

        scheduler.schedule("req-1");

        verify(executionService).executePlanTask(eq("task-b"), anyString(), anyString());
    }

    @Test
    void schedule_allowsCrossAreaParallelWhenNoConflict() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);

        CodingTask fe = task("task-fe", "FE-001", "frontend", List.of(), CodingTaskStatus.PENDING);
        fe.setAssignedAgent("frontend-dev-agent");
        fe.setFileScope(List.of("frontend/src/pages/A.tsx"));
        CodingTask be = task("task-be", "BE-001", "backend", List.of(), CodingTaskStatus.PENDING);
        be.setAssignedAgent("backend-dev-agent");
        be.setFileScope(List.of("backend/src/main/java/A.java"));
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(fe, be));

        scheduler.schedule("req-1");

        verify(executionService).executePlanTask(eq("task-fe"), anyString(), anyString());
        verify(executionService).executePlanTask(eq("task-be"), anyString(), anyString());
    }

    private Requirement requirement(String id, String loopId) {
        Requirement req = new Requirement();
        req.setId(id);
        req.setProjectId("proj-1");
        req.setActiveLoopId(loopId);
        return req;
    }

    private CodingTask task(String id, String planTaskKey, String area, List<String> dependsOn,
                            CodingTaskStatus status) {
        CodingTask task = new CodingTask();
        task.setId(id);
        task.setProjectId("proj-1");
        task.setRequirementId("req-1");
        task.setPlanTaskKey(planTaskKey);
        task.setArea(area);
        task.setDependsOn(dependsOn);
        task.setStatus(status);
        task.setLoopId("loop-1");
        task.setTitle(planTaskKey);
        task.setDescription("desc");
        task.setAssignedAgent("frontend".equals(area) ? "frontend-dev-agent" : "backend-dev-agent");
        return task;
    }

    private EffectiveTaskGraph graph(long revisionSeq, CodingTask... tasks) {
        Map<String, CodingTask> byKey = new LinkedHashMap<>();
        for (CodingTask task : tasks) {
            byKey.put(task.getPlanTaskKey(), task);
        }
        return new EffectiveTaskGraph(revisionSeq, List.of(tasks), byKey);
    }
}
