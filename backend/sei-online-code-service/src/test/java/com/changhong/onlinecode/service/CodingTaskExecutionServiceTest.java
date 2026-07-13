package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.MemoryJobTriggerSource;
import com.changhong.onlinecode.dto.enums.MemoryJobType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.MemoryJob;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.memory.CodingTaskChangeCollector;
import com.changhong.onlinecode.dto.CodingTaskDto;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodingTaskExecutionServiceTest {

    private CodingTaskDao codingTaskDao;
    private RunDao runDao;
    private FailureInfoSupport failureInfoSupport;
    private MemoryJobService memoryJobService;
    private WorkspaceMemoryService workspaceMemoryService;
    private AgentService agentService;
    private CodingTaskScheduler codingTaskScheduler;
    private CodingTaskExecutionService service;

    @BeforeEach
    void setUp() {
        codingTaskDao = mock(CodingTaskDao.class);
        runDao = mock(RunDao.class);
        failureInfoSupport = new FailureInfoSupport();
        memoryJobService = mock(MemoryJobService.class);
        workspaceMemoryService = mock(WorkspaceMemoryService.class);
        agentService = mock(AgentService.class);
        codingTaskScheduler = mock(CodingTaskScheduler.class);
        service = new CodingTaskExecutionService(
                codingTaskDao,
                runDao,
                mock(RequirementService.class),
                mock(OverviewDesignService.class),
                mock(DetailedDesignService.class),
                mock(WorkspaceManager.class),
                agentService,
                mock(CliRunnerRegistry.class),
                failureInfoSupport,
                mock(RequirementDesignContextService.class),
                mock(DesignContextPromptAssembler.class),
                memoryJobService,
                workspaceMemoryService,
                mock(CodingTaskChangeCollector.class)
        );
        service.setCodingTaskScheduler(codingTaskScheduler);
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

        invokeFinishRun(callbackRun, callbackTask, true, null);

        assertEquals(RunState.SUCCEEDED, persistedRun.getState());
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
        assertEquals(CodingTaskStatus.FAILED, persistedTask.getStatus());
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
        verify(codingTaskScheduler).onDevelopmentRunFinished("task5", true, null);
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
        verify(codingTaskScheduler).onDevelopmentRunFinished("task6", false, "compile error");
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
        verify(codingTaskScheduler).schedule("req-7");
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
