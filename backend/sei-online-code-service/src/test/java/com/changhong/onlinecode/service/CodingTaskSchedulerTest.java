package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.WorkspaceSource;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.service.validation.ValidationCommandExecutor;
import com.changhong.onlinecode.service.memory.CodingTaskChangeCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private ValidationCommandExecutor validationCommandExecutor;
    private WorkspaceManager workspaceManager;
    private RunDao runDao;
    private CodingTaskScheduler scheduler;

    private final AtomicInteger savedTasks = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        codingTaskDao = mock(CodingTaskDao.class);
        requirementDao = mock(RequirementDao.class);
        executionService = mock(CodingTaskExecutionService.class);
        validationCommandExecutor = mock(ValidationCommandExecutor.class);
        workspaceManager = mock(WorkspaceManager.class);
        runDao = mock(RunDao.class);
        scheduler = new CodingTaskScheduler(codingTaskDao, requirementDao, executionService,
                validationCommandExecutor, workspaceManager, runDao, mock(CodingTaskChangeCollector.class));
        when(runDao.findByCodingTaskId(anyString())).thenReturn(List.of());

        when(codingTaskDao.save(any(CodingTask.class))).thenAnswer(invocation -> {
            savedTasks.incrementAndGet();
            return invocation.getArgument(0);
        });
        when(workspaceManager.resolve(anyString())).thenReturn(
                new WorkspaceResolveResult("/tmp/ws", true, WorkspaceSource.SCAFFOLD));
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
    void schedule_respectsLaneConcurrency() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);

        CodingTask running = task("task-a", "FE-001", "frontend", List.of(), CodingTaskStatus.RUNNING);
        running.setAssignedAgent("frontend-dev-agent");
        CodingTask pending = task("task-b", "FE-002", "frontend", List.of(), CodingTaskStatus.PENDING);
        pending.setAssignedAgent("frontend-dev-agent");
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(running, pending));

        scheduler.schedule("req-1");

        verify(executionService, never()).executePlanTask(eq("task-b"), anyString(), anyString());
        ArgumentCaptor<CodingTask> captor = ArgumentCaptor.forClass(CodingTask.class);
        verify(codingTaskDao, times(1)).save(captor.capture());
        assertEquals(CodingTaskStatus.BLOCKED, captor.getValue().getStatus());
    }

    @Test
    void schedule_blocksTaskOnFileScopeParentChildConflict() {
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
        ArgumentCaptor<CodingTask> captor = ArgumentCaptor.forClass(CodingTask.class);
        verify(codingTaskDao, times(1)).save(captor.capture());
        assertEquals(CodingTaskStatus.BLOCKED, captor.getValue().getStatus());
    }

    @Test
    void onDevelopmentRunFinished_success_entersValidatingThenSucceeds() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask task = task("task-a", "FE-001", "frontend", List.of(), CodingTaskStatus.RUNNING);
        task.setAssignedAgent("frontend-dev-agent");
        when(codingTaskDao.findOne("task-a")).thenReturn(task);
        when(validationCommandExecutor.execute(any(Path.class), eq("pnpm -C frontend build")))
                .thenReturn(new ValidationCommandExecutor.ValidationResult(0, "ok", "", Duration.ofSeconds(1)));

        scheduler.onDevelopmentRunFinished("task-a", true, null);

        assertEquals(CodingTaskStatus.SUCCEEDED, task.getStatus());
    }

    @Test
    void onDevelopmentRunFinished_validationFailure_marksValidationFailedAndReschedules() {
        Requirement req = requirement("req-1", "loop-1");
        when(requirementDao.findOne("req-1")).thenReturn(req);
        CodingTask task = task("task-a", "FE-001", "frontend", List.of(), CodingTaskStatus.RUNNING);
        task.setAssignedAgent("frontend-dev-agent");
        when(codingTaskDao.findOne("task-a")).thenReturn(task);
        when(validationCommandExecutor.execute(any(Path.class), eq("pnpm -C frontend build")))
                .thenReturn(new ValidationCommandExecutor.ValidationResult(1, "", "build failed", Duration.ofSeconds(1)));

        scheduler.onDevelopmentRunFinished("task-a", true, null);

        assertEquals(CodingTaskStatus.VALIDATION_FAILED, task.getStatus());
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
