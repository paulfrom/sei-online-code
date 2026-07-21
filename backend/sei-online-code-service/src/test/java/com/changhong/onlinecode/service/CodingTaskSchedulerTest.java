package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.service.validation.ValidationLoopService;
import com.changhong.onlinecode.service.memory.CodingTaskChangeCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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
        scheduler = new CodingTaskScheduler(codingTaskDao, requirementDao, executionService,
                runDao, mock(CodingTaskChangeCollector.class),
                eventPublisher, requirementCommentService, validationLoopService);
        when(runDao.findByCodingTaskId(anyString())).thenReturn(List.of());

        when(codingTaskDao.save(any(CodingTask.class))).thenAnswer(invocation -> {
            savedTasks.incrementAndGet();
            return invocation.getArgument(0);
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
    void schedule_validationTaskRunsTestAgentAndMarksFailure() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask task = task("task-a", "VAL-001", "full-stack", List.of(), CodingTaskStatus.PENDING);
        task.setAssignedAgent("test-agent");
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));
        when(validationLoopService.validateTask(task))
                .thenReturn(new ValidationLoopService.ValidationOutcome(false, List.of()));

        scheduler.schedule("req-1");

        assertEquals(CodingTaskStatus.VALIDATION_FAILED, task.getStatus());
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
    void schedule_validationTaskLoopChangesDuringValidation_marksTaskStale() {
        List<CodingTaskStatus> savedStatuses = new ArrayList<>();
        when(codingTaskDao.save(any(CodingTask.class))).thenAnswer(invocation -> {
            CodingTask saved = invocation.getArgument(0);
            savedStatuses.add(saved.getStatus());
            return saved;
        });
        Requirement original = requirement("req-1", "loop-1");
        Requirement current = requirement("req-1", "loop-2");
        when(requirementDao.findOne("req-1")).thenReturn(original, current, current);
        CodingTask task = task("task-a", "VAL-001", "full-stack", List.of(), CodingTaskStatus.PENDING);
        task.setAssignedAgent("test-agent");
        task.setExecutionPlanId("plan-1");
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));
        when(validationLoopService.validateTask(task))
                .thenReturn(new ValidationLoopService.ValidationOutcome(true, List.of()));

        scheduler.schedule("req-1");

        assertEquals(CodingTaskStatus.STALE, task.getStatus());
        assertTrue(!savedStatuses.contains(CodingTaskStatus.SUCCEEDED));
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
}
