package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Run;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodingTaskExecutionServiceTest {

    private CodingTaskDao codingTaskDao;
    private RunDao runDao;
    private FailureInfoSupport failureInfoSupport;
    private CodingTaskExecutionService service;

    @BeforeEach
    void setUp() {
        codingTaskDao = mock(CodingTaskDao.class);
        runDao = mock(RunDao.class);
        failureInfoSupport = new FailureInfoSupport();
        service = new CodingTaskExecutionService(
                codingTaskDao,
                runDao,
                mock(RequirementService.class),
                mock(OverviewDesignService.class),
                mock(DetailedDesignService.class),
                mock(WorkspaceManager.class),
                mock(AgentService.class),
                mock(CliRunnerRegistry.class),
                failureInfoSupport
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
        persistedTask.setFailureSummary("old");
        persistedTask.setRetryCount(2);

        when(runDao.findOne("run2")).thenReturn(persistedRun);
        when(codingTaskDao.findOne("task2")).thenReturn(persistedTask);

        invokeFinishRun(callbackRun, callbackTask, true, null);

        assertEquals(RunState.SUCCEEDED, persistedRun.getState());
        assertEquals(CodingTaskStatus.SUCCEEDED, persistedTask.getStatus());
        assertEquals(null, persistedTask.getFailureSummary());
        assertEquals(0, persistedTask.getRetryCount());
        verify(runDao).save(persistedRun);
        verify(codingTaskDao).save(persistedTask);
    }

    private void invokeFinishRun(Run run, CodingTask task, boolean success, String reason) throws Exception {
        Method method = CodingTaskExecutionService.class.getDeclaredMethod(
                "finishRun", Run.class, CodingTask.class, boolean.class, String.class);
        method.setAccessible(true);
        method.invoke(service, run, task, success, reason);
    }
}
