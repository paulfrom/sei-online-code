package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.MemoryJobTriggerSource;
import com.changhong.onlinecode.dto.enums.MemoryJobType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.MemoryJob;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.memory.CodingTaskChangeCollector;
import com.changhong.onlinecode.service.memory.CodingTaskChangeResult;
import com.changhong.onlinecode.service.memory.WorkspaceChangeDetector;
import com.changhong.onlinecode.service.agent.AgentExecutionResult;
import com.changhong.onlinecode.service.agent.AgentExecutionRequest;
import com.changhong.onlinecode.service.agent.AgentExecutionService;
import com.changhong.onlinecode.dto.CodingTaskDto;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import com.changhong.sei.core.utils.TransactionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

class CodingTaskExecutionServiceTest {

    private CodingTaskDao codingTaskDao;
    private RunDao runDao;
    private FailureInfoSupport failureInfoSupport;
    private MemoryJobService memoryJobService;
    private WorkspaceMemoryService workspaceMemoryService;
    private AgentService agentService;
    private RequirementService requirementService;
    private ApplicationEventPublisher eventPublisher;
    private ExecutionPlanDao executionPlanDao;
    private WorkspaceManager workspaceManager;
    private AgentExecutionService agentExecutionService;
    private RunNumberService runNumberService;
    private CodingTaskChangeCollector changeCollector;
    private com.changhong.onlinecode.service.agent.CodingTaskProgressIntegrator codingTaskProgressIntegrator;
    private WorkspaceChangeDetector workspaceChangeDetector;
    private CodingTaskExecutionService service;
    @org.junit.jupiter.api.io.TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        codingTaskDao = mock(CodingTaskDao.class);
        runDao = mock(RunDao.class);
        failureInfoSupport = new FailureInfoSupport();
        memoryJobService = mock(MemoryJobService.class);
        workspaceMemoryService = mock(WorkspaceMemoryService.class);
        agentService = mock(AgentService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        executionPlanDao = mock(ExecutionPlanDao.class);
        workspaceManager = mock(WorkspaceManager.class);
        when(workspaceManager.requirementWorkspaceKey(anyString()))
                .thenAnswer(invocation -> "requirement-" + invocation.getArgument(0));
        agentExecutionService = mock(AgentExecutionService.class);
        runNumberService = mock(RunNumberService.class);
        when(runNumberService.assign(any(Run.class))).thenAnswer(invocation -> invocation.getArgument(0));
        changeCollector = mock(CodingTaskChangeCollector.class);
        workspaceChangeDetector = new WorkspaceChangeDetector();
        requirementService = mock(RequirementService.class);
        com.changhong.onlinecode.dao.RequirementWorkspaceDao requirementWorkspaceDao =
                mock(com.changhong.onlinecode.dao.RequirementWorkspaceDao.class);
        codingTaskProgressIntegrator = mock(com.changhong.onlinecode.service.agent.CodingTaskProgressIntegrator.class);
        org.mockito.Mockito.lenient().when(codingTaskProgressIntegrator.preflight(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new com.changhong.onlinecode.service.agent.CodingTaskProgressIntegrator.ProgressPreflight(
                        null, null, "inv-test", null));
        org.mockito.Mockito.lenient().when(codingTaskProgressIntegrator.recordSuccessfulCodingTaskCompletion(
                        any(Run.class), any(), any()))
                .thenReturn(true);
        com.changhong.onlinecode.service.progress.WorkspaceLeaseService workspaceLeaseService =
                mock(com.changhong.onlinecode.service.progress.WorkspaceLeaseService.class);
        service = new CodingTaskExecutionService(
                codingTaskDao,
                runDao,
                runNumberService,
                requirementService,
                executionPlanDao,
                mock(RequirementCommentService.class),
                workspaceManager,
                agentService,
                agentExecutionService,
                failureInfoSupport,
                mock(RequirementDesignContextService.class),
                mock(DesignContextPromptAssembler.class),
                memoryJobService,
                workspaceMemoryService,
                changeCollector,
                workspaceChangeDetector,
                eventPublisher,
                codingTaskProgressIntegrator,
                workspaceLeaseService,
                requirementWorkspaceDao
        );
    }

    @Test
    void finishRun_doesNotOverrideCancelledTask() throws Exception {
        Run callbackRun = new Run();
        callbackRun.setId("run1");
        CodingTask callbackTask = new CodingTask();
        callbackTask.setId("task1");

        Run persistedRun = new Run();
        persistedRun.setId("run1");
        persistedRun.setState(RunState.CANCELLED);
        persistedRun.setTriggerSource(TriggerSource.USER_ACTION);
        CodingTask persistedTask = new CodingTask();
        persistedTask.setId("task1");
        persistedTask.setStatus(CodingTaskStatus.CANCELLED);

        when(runDao.findOne("run1")).thenReturn(persistedRun);
        when(codingTaskDao.findOne("task1")).thenReturn(persistedTask);

        invokeFinishRun(callbackRun, callbackTask, true, null);

        verify(runDao, never()).save(any(Run.class));
        verify(codingTaskDao, never()).save(any(CodingTask.class));
        verify(memoryJobService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void finishRun_marksRunningTaskSucceededAndClearsFailure() throws Exception {
        Run callbackRun = new Run();
        callbackRun.setId("run2");
        CodingTask callbackTask = new CodingTask();
        callbackTask.setId("task2");

        Run persistedRun = new Run();
        persistedRun.setId("run2");
        persistedRun.setState(RunState.RUNNING);
        persistedRun.setTriggerSource(TriggerSource.AUTO);
        CodingTask persistedTask = new CodingTask();
        persistedTask.setId("task2");
        persistedTask.setStatus(CodingTaskStatus.RUNNING);
        persistedTask.setProjectId("proj-1");
        persistedTask.setRequirementId("req-1");
        persistedTask.setFailureSummary("old");
        persistedTask.setRetryCount(2);

        when(runDao.findOne("run2")).thenReturn(persistedRun);
        when(codingTaskDao.findOne("task2")).thenReturn(persistedTask);

        WorkspaceMemory current = new WorkspaceMemory();
        current.setId("wm-1");
        when(workspaceMemoryService.findCurrent("proj-1")).thenReturn(current);
        @SuppressWarnings("unchecked")
        OperateResultWithData<MemoryJob> memoryJobSubmitted = mock(OperateResultWithData.class);
        when(memoryJobSubmitted.successful()).thenReturn(true);
        when(memoryJobService.submit(eq("proj-1"), eq(MemoryJobType.MEMORY_UPDATE_AFTER_CODING_TASK),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                eq("req-1"), eq("task2"), eq("run2"), eq("wm-1")))
                .thenReturn(memoryJobSubmitted);

        invokeFinishRun(callbackRun, callbackTask, true, "cli completed");

        assertEquals(RunState.SUCCEEDED, persistedRun.getState());
        assertEquals("cli completed", persistedRun.getSummary());
        assertEquals(CodingTaskStatus.SUCCEEDED, persistedTask.getStatus());
        assertEquals(null, persistedTask.getFailureSummary());
        assertEquals(0, persistedTask.getRetryCount());
        verify(runDao).save(persistedRun);
        verify(codingTaskDao).save(persistedTask);
        verify(memoryJobService).submit(eq("proj-1"), eq(MemoryJobType.MEMORY_UPDATE_AFTER_CODING_TASK),
                eq(MemoryJobTriggerSource.CODING_TASK_SUCCEEDED), anyString(),
                eq("req-1"), eq("task2"), eq("run2"), eq("wm-1"));
    }

    @Test
    void finishRun_failedTask_doesNotSubmitMemoryUpdateJob() throws Exception {
        Run callbackRun = new Run();
        callbackRun.setId("run3");
        CodingTask callbackTask = new CodingTask();
        callbackTask.setId("task3");

        Run persistedRun = new Run();
        persistedRun.setId("run3");
        persistedRun.setState(RunState.RUNNING);
        persistedRun.setTriggerSource(TriggerSource.AUTO);
        CodingTask persistedTask = new CodingTask();
        persistedTask.setId("task3");
        persistedTask.setStatus(CodingTaskStatus.RUNNING);
        persistedTask.setProjectId("proj-1");

        when(runDao.findOne("run3")).thenReturn(persistedRun);
        when(codingTaskDao.findOne("task3")).thenReturn(persistedTask);

        invokeFinishRun(callbackRun, callbackTask, false, "build failed");

        assertEquals(RunState.FAILED, persistedRun.getState());
        assertEquals("build failed", persistedRun.getSummary());
        assertEquals("build failed", persistedRun.getFailureReason());
        assertEquals(CodingTaskStatus.FAILED, persistedTask.getStatus());
        assertEquals("build failed", persistedTask.getFailureSummary());
        assertEquals("build failed", persistedTask.getFailureDetail());
        verify(memoryJobService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void finishRun_memoryJobSubmissionFailure_doesNotAffectTaskSuccess() throws Exception {
        Run callbackRun = new Run();
        callbackRun.setId("run4");
        CodingTask callbackTask = new CodingTask();
        callbackTask.setId("task4");

        Run persistedRun = new Run();
        persistedRun.setId("run4");
        persistedRun.setState(RunState.RUNNING);
        CodingTask persistedTask = new CodingTask();
        persistedTask.setId("task4");
        persistedTask.setStatus(CodingTaskStatus.RUNNING);
        persistedTask.setProjectId("proj-1");

        when(runDao.findOne("run4")).thenReturn(persistedRun);
        when(codingTaskDao.findOne("task4")).thenReturn(persistedTask);
        when(workspaceMemoryService.findCurrent("proj-1")).thenReturn(null);
        when(memoryJobService.submit(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("db unavailable"));

        invokeFinishRun(callbackRun, callbackTask, true, null);

        assertEquals(RunState.SUCCEEDED, persistedRun.getState());
        assertEquals(CodingTaskStatus.SUCCEEDED, persistedTask.getStatus());
        verify(memoryJobService).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void finishRun_schedulerManaged_success_callsSchedulerAndDoesNotSubmitMemoryJob() throws Exception {
        Run callbackRun = new Run();
        callbackRun.setId("run5");
        CodingTask callbackTask = new CodingTask();
        callbackTask.setId("task5");

        Run persistedRun = new Run();
        persistedRun.setId("run5");
        persistedRun.setState(RunState.RUNNING);
        persistedRun.setTriggerSource(TriggerSource.AUTO);
        CodingTask persistedTask = new CodingTask();
        persistedTask.setId("task5");
        persistedTask.setStatus(CodingTaskStatus.RUNNING);
        persistedTask.setProjectId("proj-1");
        persistedTask.setRequirementId("req-1");

        when(runDao.findOne("run5")).thenReturn(persistedRun);
        when(codingTaskDao.findOne("task5")).thenReturn(persistedTask);

        invokeFinishRun(callbackRun, callbackTask, true, null, true);

        assertEquals(RunState.SUCCEEDED, persistedRun.getState());
        verify(eventPublisher).publishEvent(
                new CodingTaskSchedulingEvents.DevelopmentFinished("task5", true, null));
        verify(memoryJobService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
        verify(codingTaskDao, never()).save(any(CodingTask.class));
    }

    @Test
    void finishRun_schedulerManaged_failure_callsSchedulerWithReason() throws Exception {
        Run callbackRun = new Run();
        callbackRun.setId("run6");
        CodingTask callbackTask = new CodingTask();
        callbackTask.setId("task6");

        Run persistedRun = new Run();
        persistedRun.setId("run6");
        persistedRun.setState(RunState.RUNNING);
        CodingTask persistedTask = new CodingTask();
        persistedTask.setId("task6");
        persistedTask.setStatus(CodingTaskStatus.RUNNING);

        when(runDao.findOne("run6")).thenReturn(persistedRun);
        when(codingTaskDao.findOne("task6")).thenReturn(persistedTask);

        invokeFinishRun(callbackRun, callbackTask, false, "compile error", true);

        assertEquals(RunState.FAILED, persistedRun.getState());
        verify(eventPublisher).publishEvent(
                new CodingTaskSchedulingEvents.DevelopmentFinished("task6", false, "compile error"));
        verify(memoryJobService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void executePlanTask_agentNotFound_marksFailedAndTriggersScheduler() {
        CodingTask task = new CodingTask();
        task.setId("task7");
        task.setRequirementId("req-7");
        task.setProjectId("proj-7");
        task.setStatus(CodingTaskStatus.BLOCKED);

        when(codingTaskDao.findOne("task7")).thenReturn(task);
        when(runDao.findByCodingTaskId("task7")).thenReturn(java.util.Collections.emptyList());
        when(agentService.findByName("missing-agent")).thenReturn(null);

        ResultData<CodingTaskDto> result = service.executePlanTask("task7", "missing-agent", "prompt");

        assertFalse(result.successful());
        assertEquals("开发代理不存在: missing-agent", result.getMessage());
        assertEquals(CodingTaskStatus.FAILED, task.getStatus());
        assertEquals("开发代理未找到", task.getFailureSummary());
        verify(codingTaskDao).save(task);
        verify(eventPublisher).publishEvent(new CodingTaskSchedulingEvents.ScheduleRequested("req-7"));
    }

    @Test
    void executePlanTask_copiesPlanMemoryTraceToDevelopmentRun() {
        CodingTask task = new CodingTask();
        task.setId("task-trace");
        task.setRequirementId("req-trace");
        task.setProjectId("project-trace");
        task.setExecutionPlanId("plan-trace");
        task.setLoopId("loop-trace");
        Agent agent = new Agent();
        agent.setName("backend-dev-agent");
        agent.setCliTool("codex");
        ExecutionPlan plan = new ExecutionPlan();
        plan.setMemoryContextId("context-1");
        plan.setWorkspaceMemoryId("memory-1");
        com.changhong.onlinecode.agent.AgentWorkspace agentWorkspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(agentWorkspace.path()).thenReturn(java.nio.file.Path.of(System.getProperty("java.io.tmpdir")));
        when(agentWorkspace.pathString()).thenReturn(System.getProperty("java.io.tmpdir"));

        when(codingTaskDao.findOne("task-trace")).thenReturn(task);
        when(runDao.findByCodingTaskId("task-trace")).thenReturn(java.util.List.of());
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            if (run.getId() == null) run.setId("run-trace");
            return run;
        });
        when(agentService.findByName("backend-dev-agent")).thenReturn(agent);
        when(executionPlanDao.findOne("plan-trace")).thenReturn(plan);
        when(agentExecutionService.workspace(eq("project-trace"), anyString())).thenReturn(agentWorkspace);
        when(changeCollector.collect(any(), any())).thenReturn(
                new com.changhong.onlinecode.service.memory.CodingTaskChangeResult());
        when(agentExecutionService.executeAsync(eq("backend-dev-agent"), any()))
                .thenReturn(new CompletableFuture<>());

        service.executePlanTask("task-trace", "backend-dev-agent", "prompt");

        ArgumentCaptor<Run> captor = ArgumentCaptor.forClass(Run.class);
        verify(runDao, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Run developmentRun = captor.getAllValues().get(0);
        assertEquals("context-1", developmentRun.getMemoryContextId());
        assertEquals("memory-1", developmentRun.getWorkspaceMemoryId());
    }

    @Test
    void executePlanTask_createsDevelopmentRunWithoutPresetId() {
        CodingTask task = new CodingTask();
        task.setId("task-new-run");
        task.setRequirementId("req-new-run");
        task.setProjectId("project-new-run");
        task.setStatus(CodingTaskStatus.PENDING);
        task.setAssignedAgent("backend-dev-agent");

        Agent agent = new Agent();
        agent.setName("backend-dev-agent");
        agent.setCliTool("codex");

        com.changhong.onlinecode.agent.AgentWorkspace agentWorkspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(agentWorkspace.path()).thenReturn(tempDir);
        when(agentWorkspace.pathString()).thenReturn(tempDir.toString());

        AtomicReference<Run> savedRun = new AtomicReference<>();
        when(codingTaskDao.findOne("task-new-run")).thenReturn(task);
        when(runDao.findByCodingTaskId("task-new-run")).thenReturn(java.util.List.of());
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            assertNull(run.getId(), "new Run must let sei-core generate id during insert");
            run.setId("run-generated");
            savedRun.set(run);
            return run;
        });
        when(agentService.findByName("backend-dev-agent")).thenReturn(agent);
        when(workspaceManager.requirementWorkspaceKey("req-new-run")).thenReturn("requirement-req-new-run");
        when(agentExecutionService.workspace(eq("project-new-run"), anyString())).thenReturn(agentWorkspace);
        when(changeCollector.resolveHead(tempDir.toString())).thenReturn("base-new-run");
        when(agentExecutionService.executeAsync(eq("backend-dev-agent"), any()))
                .thenReturn(new CompletableFuture<>());

        ResultData<CodingTaskDto> result = service.executePlanTask("task-new-run", "backend-dev-agent", "prompt");

        assertTrue(result.successful());
        assertEquals("run-generated", savedRun.get().getId());
    }

    @Test
    void executePlanTask_defersAgentExecutionUntilTransactionCommit() {
        CodingTask task = new CodingTask();
        task.setId("task-after-commit");
        task.setRequirementId("req-after-commit");
        task.setProjectId("project-after-commit");
        task.setStatus(CodingTaskStatus.PENDING);
        task.setAssignedAgent("backend-dev-agent");

        Agent agent = new Agent();
        agent.setName("backend-dev-agent");
        agent.setCliTool("codex");

        com.changhong.onlinecode.agent.AgentWorkspace agentWorkspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(agentWorkspace.path()).thenReturn(tempDir);
        when(agentWorkspace.pathString()).thenReturn(tempDir.toString());

        when(codingTaskDao.findOne("task-after-commit")).thenReturn(task);
        when(runDao.findByCodingTaskId("task-after-commit")).thenReturn(java.util.List.of());
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId("run-after-commit");
            }
            return run;
        });
        when(agentService.findByName("backend-dev-agent")).thenReturn(agent);
        when(agentExecutionService.workspace(eq("project-after-commit"), anyString())).thenReturn(agentWorkspace);
        when(changeCollector.resolveHead(tempDir.toString())).thenReturn("base-after-commit");
        when(agentExecutionService.executeAsync(eq("backend-dev-agent"), any()))
                .thenReturn(new CompletableFuture<>());

        TransactionSynchronizationManager.initSynchronization();
        try {
            ResultData<CodingTaskDto> result = service.executePlanTask(
                    "task-after-commit", "backend-dev-agent", "prompt");

            assertTrue(result.successful());
            verify(agentExecutionService, never()).executeAsync(anyString(), any());
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(agentExecutionService).executeAsync(eq("backend-dev-agent"), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void executePlanTask_defersAgentExecutionWhenNewTransactionStartsInsideOuterAfterCommit() {
        CodingTask task = new CodingTask();
        task.setId("task-nested-after-commit");
        task.setRequirementId("req-nested-after-commit");
        task.setProjectId("project-nested-after-commit");
        task.setStatus(CodingTaskStatus.PENDING);
        task.setAssignedAgent("backend-dev-agent");

        Agent agent = new Agent();
        agent.setName("backend-dev-agent");
        agent.setCliTool("codex");

        com.changhong.onlinecode.agent.AgentWorkspace agentWorkspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(agentWorkspace.path()).thenReturn(tempDir);
        when(agentWorkspace.pathString()).thenReturn(tempDir.toString());
        when(codingTaskDao.findOne("task-nested-after-commit")).thenReturn(task);
        when(runDao.findByCodingTaskId("task-nested-after-commit")).thenReturn(List.of());
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId("run-nested-after-commit");
            }
            return run;
        });
        when(agentService.findByName("backend-dev-agent")).thenReturn(agent);
        when(agentExecutionService.workspace(eq("project-nested-after-commit"), anyString()))
                .thenReturn(agentWorkspace);
        when(changeCollector.resolveHead(tempDir.toString())).thenReturn("base-nested-after-commit");
        when(agentExecutionService.executeAsync(eq("backend-dev-agent"), any()))
                .thenReturn(new CompletableFuture<>());

        AtomicReference<List<TransactionSynchronization>> innerSynchronizations = new AtomicReference<>();
        TransactionSynchronizationManager.initSynchronization();
        try {
            TransactionUtil.afterCommit(() -> {
                // 模拟外层 afterCommit 中通过 REQUIRES_NEW 开启的独立调度事务。
                TransactionSynchronizationManager.initSynchronization();
                try {
                    ResultData<CodingTaskDto> result = service.executePlanTask(
                            "task-nested-after-commit", "backend-dev-agent", "prompt");
                    assertTrue(result.successful());
                    verify(agentExecutionService, never()).executeAsync(anyString(), any());
                    innerSynchronizations.set(List.copyOf(
                            TransactionSynchronizationManager.getSynchronizations()));
                } finally {
                    TransactionSynchronizationManager.clearSynchronization();
                }
            });
            List<TransactionSynchronization> outerSynchronizations = List.copyOf(
                    TransactionSynchronizationManager.getSynchronizations());
            TransactionSynchronizationManager.clearSynchronization();

            outerSynchronizations.forEach(TransactionSynchronization::afterCommit);
            verify(agentExecutionService, never()).executeAsync(anyString(), any());

            innerSynchronizations.get().forEach(TransactionSynchronization::afterCommit);
            verify(agentExecutionService).executeAsync(eq("backend-dev-agent"), any());
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    void executePlanTask_runnerCompletesWithoutWorkspaceChanges_marksDevelopmentFailed() {
        CodingTask task = new CodingTask();
        task.setId("task-empty");
        task.setRequirementId("req-empty");
        task.setProjectId("project-empty");
        task.setStatus(CodingTaskStatus.PENDING);
        task.setAssignedAgent("backend-dev-agent");

        Agent agent = new Agent();
        agent.setName("backend-dev-agent");
        agent.setCliTool("codex");

        com.changhong.onlinecode.agent.AgentWorkspace agentWorkspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(agentWorkspace.path()).thenReturn(tempDir);
        when(agentWorkspace.pathString()).thenReturn(tempDir.toString());

        AtomicReference<Run> savedRun = new AtomicReference<>();
        when(codingTaskDao.findOne("task-empty")).thenReturn(task);
        when(runDao.findByCodingTaskId("task-empty")).thenReturn(java.util.List.of());
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId("run-empty");
            }
            savedRun.set(run);
            return run;
        });
        when(runDao.findOne(anyString())).thenAnswer(invocation -> savedRun.get());
        when(agentService.findByName("backend-dev-agent")).thenReturn(agent);
        when(agentExecutionService.workspace(eq("project-empty"), anyString())).thenReturn(agentWorkspace);
        when(changeCollector.resolveHead(tempDir.toString())).thenReturn("base-1");
        CodingTaskChangeResult noChanges = new CodingTaskChangeResult();
        noChanges.setSuccess(true);
        noChanges.setChangedFiles(java.util.List.of());
        when(changeCollector.collect(tempDir.toString(), "base-1")).thenReturn(noChanges);
        when(agentExecutionService.executeAsync(eq("backend-dev-agent"), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(new AgentExecutionResult(
                        savedRun.get().getId(), "任务已完成", true, null)));

        ResultData<CodingTaskDto> result = service.executePlanTask("task-empty", "backend-dev-agent", "prompt");

        assertTrue(result.successful());
        assertEquals(RunState.FAILED, savedRun.get().getState());
        assertEquals("任务已完成", savedRun.get().getSummary());
        verify(eventPublisher).publishEvent(new CodingTaskSchedulingEvents.DevelopmentFinished(
                "task-empty", false, "开发代理未在指定工作区产生代码或文档变更"));
    }

    @Test
    void executePlanTask_cliFailureStoresOutputAndPropagatesFailureReason() {
        CodingTask task = new CodingTask();
        task.setId("task-cli-fail");
        task.setRequirementId("req-cli-fail");
        task.setProjectId("project-cli-fail");
        task.setStatus(CodingTaskStatus.PENDING);
        task.setAssignedAgent("backend-dev-agent");

        Agent agent = new Agent();
        agent.setName("backend-dev-agent");
        agent.setCliTool("claude");

        com.changhong.onlinecode.agent.AgentWorkspace agentWorkspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(agentWorkspace.path()).thenReturn(tempDir);
        when(agentWorkspace.pathString()).thenReturn(tempDir.toString());

        AtomicReference<Run> savedRun = new AtomicReference<>();
        when(codingTaskDao.findOne("task-cli-fail")).thenAnswer(invocation -> task);
        when(runDao.findByCodingTaskId("task-cli-fail")).thenReturn(java.util.List.of());
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId("run-cli-fail");
            }
            savedRun.set(run);
            return run;
        });
        when(runDao.findOne(anyString())).thenAnswer(invocation -> savedRun.get());
        when(agentService.findByName("backend-dev-agent")).thenReturn(agent);
        when(agentExecutionService.workspace(eq("project-cli-fail"), anyString())).thenReturn(agentWorkspace);
        when(changeCollector.resolveHead(tempDir.toString())).thenReturn("base-cli-fail");
        when(agentExecutionService.executeAsync(eq("backend-dev-agent"), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(new AgentExecutionResult(
                        savedRun.get().getId(), "真实 CLI 失败输出", false, "claude exited with code 1")));

        ResultData<CodingTaskDto> result = service.executePlanTask("task-cli-fail", "backend-dev-agent", "prompt");

        assertTrue(result.successful());
        assertEquals(RunState.FAILED, savedRun.get().getState());
        assertEquals("真实 CLI 失败输出", savedRun.get().getSummary());
        assertEquals("真实 CLI 失败输出", task.getFailureSummary());
        assertEquals("真实 CLI 失败输出", task.getFailureDetail());
        verify(eventPublisher).publishEvent(new CodingTaskSchedulingEvents.DevelopmentFinished(
                "task-cli-fail", false, "真实 CLI 失败输出"));
    }

    @Test
    void executePlanTask_successSummaryContainingFailedWord_marksDevelopmentSucceeded() throws Exception {
        CodingTask task = new CodingTask();
        task.setId("task-file-change");
        task.setRequirementId("req-file-change");
        task.setProjectId("project-file-change");
        task.setStatus(CodingTaskStatus.PENDING);
        task.setAssignedAgent("backend-dev-agent");

        Agent agent = new Agent();
        agent.setName("backend-dev-agent");
        agent.setCliTool("claude");

        com.changhong.onlinecode.agent.AgentWorkspace agentWorkspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(agentWorkspace.path()).thenReturn(tempDir);
        when(agentWorkspace.pathString()).thenReturn(tempDir.toString());

        AtomicReference<Run> savedRun = new AtomicReference<>();
        when(codingTaskDao.findOne("task-file-change")).thenReturn(task);
        when(runDao.findByCodingTaskId("task-file-change")).thenReturn(java.util.List.of());
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId("run-file-change");
            }
            savedRun.set(run);
            return run;
        });
        when(runDao.findOne(anyString())).thenAnswer(invocation -> savedRun.get());
        when(agentService.findByName("backend-dev-agent")).thenReturn(agent);
        when(agentExecutionService.workspace(eq("project-file-change"), anyString())).thenReturn(agentWorkspace);
        when(changeCollector.resolveHead(tempDir.toString())).thenReturn(null);
        when(agentExecutionService.executeAsync(eq("backend-dev-agent"), any()))
                .thenAnswer(invocation -> {
                    Files.writeString(tempDir.resolve("generated.txt"), "hello");
                    return CompletableFuture.completedFuture(new AgentExecutionResult(savedRun.get().getId(),
                            "Supported outcomes: ENROLLED/WAITLISTED/FAILED", true, null));
                });

        ResultData<CodingTaskDto> result = service.executePlanTask("task-file-change", "backend-dev-agent", "prompt");

        assertTrue(result.successful());
        assertEquals(RunState.SUCCEEDED, savedRun.get().getState());
        assertEquals("Supported outcomes: ENROLLED/WAITLISTED/FAILED", savedRun.get().getSummary());
        verify(eventPublisher).publishEvent(new CodingTaskSchedulingEvents.DevelopmentFinished(
                "task-file-change", true, null));
    }

    @Test
    void executePlanTask_ledgerRecordFailure_doesNotBlockDevelopmentSuccess() throws Exception {
        CodingTask task = new CodingTask();
        task.setId("task-ledger-fail");
        task.setRequirementId("req-ledger-fail");
        task.setProjectId("project-ledger-fail");
        task.setStatus(CodingTaskStatus.PENDING);
        task.setAssignedAgent("backend-dev-agent");

        Agent agent = new Agent();
        agent.setName("backend-dev-agent");
        agent.setCliTool("claude");

        com.changhong.onlinecode.agent.AgentWorkspace agentWorkspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(agentWorkspace.path()).thenReturn(tempDir);
        when(agentWorkspace.pathString()).thenReturn(tempDir.toString());

        AtomicReference<Run> savedRun = new AtomicReference<>();
        when(codingTaskDao.findOne("task-ledger-fail")).thenReturn(task);
        when(runDao.findByCodingTaskId("task-ledger-fail")).thenReturn(java.util.List.of());
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId("run-ledger-fail");
            }
            savedRun.set(run);
            return run;
        });
        when(runDao.findOne(anyString())).thenAnswer(invocation -> savedRun.get());
        when(agentService.findByName("backend-dev-agent")).thenReturn(agent);
        when(agentExecutionService.workspace(eq("project-ledger-fail"), anyString())).thenReturn(agentWorkspace);
        when(changeCollector.resolveHead(tempDir.toString())).thenReturn(null);
        when(codingTaskProgressIntegrator.preflight(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("ledger unavailable"));
        when(codingTaskProgressIntegrator.recordSuccessfulCodingTaskCompletion(any(Run.class), any(), any()))
                .thenReturn(false);
        org.mockito.Mockito.doThrow(new IllegalStateException("observation unavailable"))
                .when(codingTaskProgressIntegrator).appendTerminalObservation(
                        any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
        when(agentExecutionService.executeAsync(eq("backend-dev-agent"), any()))
                .thenAnswer(invocation -> {
                    Files.writeString(tempDir.resolve("generated-ledger-fail.txt"), "hello");
                    return CompletableFuture.completedFuture(new AgentExecutionResult(savedRun.get().getId(),
                            "任务已完成", true, null));
                });

        ResultData<CodingTaskDto> result = service.executePlanTask(
                "task-ledger-fail", "backend-dev-agent", "prompt");

        assertTrue(result.successful());
        assertEquals(RunState.SUCCEEDED, savedRun.get().getState());
        assertEquals("任务已完成", savedRun.get().getSummary());
        assertNull(savedRun.get().getFailureReason());
        verify(eventPublisher).publishEvent(new CodingTaskSchedulingEvents.DevelopmentFinished(
                "task-ledger-fail", true, null));
    }

    @Test
    void executePlanTask_retryLinksPreviousRunAndReusesExistingWorkspaceChanges() {
        CodingTask task = new CodingTask();
        task.setId("task-recovery");
        task.setRequirementId("req-recovery");
        task.setProjectId("project-recovery");
        task.setStatus(CodingTaskStatus.FAILED);
        task.setAssignedAgent("backend-dev-agent");
        task.setFileScope(java.util.List.of("backend"));

        Run previousRun = new Run();
        previousRun.setId("run-recovery-1");
        previousRun.setRunNo(1);
        previousRun.setAttemptNo(1);
        previousRun.setState(RunState.FAILED);
        previousRun.setFailureReason("执行超时");
        previousRun.setBaseCommit("base-recovery");

        Agent agent = new Agent();
        agent.setName("backend-dev-agent");
        agent.setCliTool("codex");

        com.changhong.onlinecode.agent.AgentWorkspace agentWorkspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(agentWorkspace.path()).thenReturn(tempDir);
        when(agentWorkspace.pathString()).thenReturn(tempDir.toString());

        AtomicReference<Run> currentRun = new AtomicReference<>();
        when(codingTaskDao.findOne("task-recovery")).thenReturn(task);
        when(runDao.findByCodingTaskId("task-recovery")).thenReturn(java.util.List.of(previousRun));
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId("run-recovery-2");
            }
            currentRun.set(saved);
            return saved;
        });
        when(runDao.findOne("run-recovery-1")).thenReturn(previousRun);
        when(runDao.findOne("run-recovery-2")).thenAnswer(invocation -> currentRun.get());
        when(agentService.findByName("backend-dev-agent")).thenReturn(agent);
        when(agentExecutionService.workspace(eq("project-recovery"), anyString())).thenReturn(agentWorkspace);
        when(changeCollector.resolveHead(tempDir.toString())).thenReturn("base-recovery");
        CodingTaskChangeResult existingChanges = new CodingTaskChangeResult();
        existingChanges.setSuccess(true);
        existingChanges.setChangedFiles(java.util.List.of("backend/Recovered.java"));
        existingChanges.setDiffSummary("backend/Recovered.java already changed");
        when(changeCollector.collect(eq(tempDir.toString()), any())).thenReturn(existingChanges);
        when(agentExecutionService.executeAsync(eq("backend-dev-agent"), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(new AgentExecutionResult(
                        currentRun.get().getId(), "现有实现验证通过", true, null)));

        ResultData<CodingTaskDto> result = service.executePlanTask(
                "task-recovery", "backend-dev-agent", "继续未完成部分", TriggerSource.USER_ACTION);

        assertTrue(result.successful());
        assertEquals("run-recovery-1", currentRun.get().getParentRunId());
        assertEquals(2, currentRun.get().getAttemptNo());
        assertEquals(RunState.SUCCEEDED, currentRun.get().getState());
        ArgumentCaptor<AgentExecutionRequest> requestCaptor = ArgumentCaptor.forClass(AgentExecutionRequest.class);
        verify(agentExecutionService).executeAsync(eq("backend-dev-agent"), requestCaptor.capture());
        assertEquals("run-recovery-1", requestCaptor.getValue().getParentRunId());
        assertEquals(2, requestCaptor.getValue().getAttemptNo());
        assertTrue(requestCaptor.getValue().getPrompt().contains("保留已正确完成的部分"));
        verify(eventPublisher).publishEvent(new CodingTaskSchedulingEvents.DevelopmentFinished(
                "task-recovery", true, null));
    }

    @Test
    void buildExecutionPrompt_requiresWorkspaceFileChange() throws Exception {
        CodingTask task = new CodingTask();
        task.setId("task-prompt");
        task.setRequirementId("req-prompt");
        task.setProjectId("project-prompt");
        task.setTitle("title");
        task.setDescription("desc");
        Requirement requirement = new Requirement();
        requirement.setId("req-prompt");
        requirement.setPrdContent("prd");
        when(requirementService.findOne("req-prompt")).thenReturn(requirement);

        Method method = CodingTaskExecutionService.class.getDeclaredMethod(
                "buildExecutionPrompt", CodingTask.class, String.class, Run.class);
        method.setAccessible(true);
        String prompt = (String) method.invoke(service, task, null, new Run());

        assertTrue(prompt.contains("必须在工作区落地至少一个代码或文档文件变更"));
        assertTrue(prompt.contains("不修改文件会被系统判定为开发失败"));
    }

    private void invokeFinishRun(Run run, CodingTask task, boolean success, String reason) throws Exception {
        invokeFinishRun(run, task, success, reason, false);
    }

    private void invokeFinishRun(Run run, CodingTask task, boolean success, String reason,
                                 boolean schedulerManaged) throws Exception {
        Method method = CodingTaskExecutionService.class.getDeclaredMethod(
                "finishRun", Run.class, CodingTask.class, boolean.class, String.class, boolean.class);
        method.setAccessible(true);
        method.invoke(service, run, task, success, reason, schedulerManaged);
    }
}
