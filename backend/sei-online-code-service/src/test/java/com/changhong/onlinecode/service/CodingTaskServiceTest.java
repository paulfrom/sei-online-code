package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.CodingTaskDto;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.sei.core.dto.ResultData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CodingTaskService 单元测试。
 *
 * <p>验证 /run 与 /rerun 的状态机约束与提示词校验。</p>
 */
class CodingTaskServiceTest {

    private CodingTaskDao codingTaskDao;
    private RunDao runDao;
    private CodingTaskExecutionService executionService;
    private CodingTaskService service;

    @BeforeEach
    void setUp() {
        codingTaskDao = mock(CodingTaskDao.class);
        runDao = mock(RunDao.class);
        executionService = mock(CodingTaskExecutionService.class);
        service = new CodingTaskService(codingTaskDao, runDao, executionService);
    }

    @Test
    void run_taskNotFound_returnsFail() {
        when(codingTaskDao.findOne("missing")).thenReturn(null);

        ResultData<CodingTaskDto> result = service.run("missing", null);

        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("不存在"));
        verify(executionService, never()).execute(any(), any());
    }

    @Test
    void run_pendingStatus_delegatesToExecutionService() {
        CodingTask task = taskWithStatus(CodingTaskStatus.PENDING);
        when(codingTaskDao.findOne(task.getId())).thenReturn(task);
        when(executionService.execute(eq(task.getId()), eq("prompt")))
                .thenReturn(ResultData.success(new CodingTaskDto()));

        ResultData<CodingTaskDto> result = service.run(task.getId(), "prompt");

        assertTrue(result.successful());
        verify(executionService).execute(eq(task.getId()), eq("prompt"));
    }

    @Test
    void run_nonPendingStatus_rejects() {
        for (CodingTaskStatus status : new CodingTaskStatus[]{
                CodingTaskStatus.RUNNING,
                CodingTaskStatus.SUCCEEDED,
                CodingTaskStatus.FAILED,
                CodingTaskStatus.CANCELLED,
                CodingTaskStatus.STALE
        }) {
            CodingTask task = taskWithStatus(status);
            when(codingTaskDao.findOne(task.getId())).thenReturn(task);

            ResultData<CodingTaskDto> result = service.run(task.getId(), null);

            assertFalse(result.successful(), "status=" + status + " 应该被拒绝");
            assertTrue(result.getMessage().contains("待执行"), "status=" + status);
            verify(executionService, never()).execute(any(), any());
        }
    }

    @Test
    void rerun_taskNotFound_returnsFail() {
        when(codingTaskDao.findOne("missing")).thenReturn(null);

        ResultData<CodingTaskDto> result = service.rerun("missing", "prompt");

        assertFalse(result.successful());
        verify(executionService, never()).execute(any(), any());
    }

    @Test
    void rerun_blankPrompt_rejects() {
        ResultData<CodingTaskDto> result = service.rerun("id", "   ");

        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("rerunPrompt"));
        verify(executionService, never()).execute(any(), any());
    }

    @Test
    void rerun_allowedStatuses_delegatesToExecutionService() {
        for (CodingTaskStatus status : new CodingTaskStatus[]{
                CodingTaskStatus.FAILED,
                CodingTaskStatus.VALIDATION_FAILED
        }) {
            CodingTask task = taskWithStatus(status);
            when(codingTaskDao.findOne(task.getId())).thenReturn(task);
            when(codingTaskDao.updateStatusIfMatch(task.getId(), status, CodingTaskStatus.RUNNING))
                    .thenReturn(1);
            when(executionService.execute(eq(task.getId()), eq("rerun-prompt")))
                    .thenReturn(ResultData.success(new CodingTaskDto()));

            ResultData<CodingTaskDto> result = service.rerun(task.getId(), "rerun-prompt");

            assertTrue(result.successful(), "status=" + status + " 应该允许重跑");
            verify(codingTaskDao).updateStatusIfMatch(task.getId(), status, CodingTaskStatus.RUNNING);
            verify(executionService).execute(eq(task.getId()), eq("rerun-prompt"));
            clearInvocations(executionService);
            clearInvocations(codingTaskDao);
        }
    }

    @Test
    void rerun_claimedByCompensation_rejectsWithoutExecuting() {
        CodingTask task = taskWithStatus(CodingTaskStatus.FAILED);
        when(codingTaskDao.findOne(task.getId())).thenReturn(task);
        when(codingTaskDao.updateStatusIfMatch(task.getId(), CodingTaskStatus.FAILED, CodingTaskStatus.RUNNING))
                .thenReturn(0);

        ResultData<CodingTaskDto> result = service.rerun(task.getId(), "rerun-prompt");

        assertFalse(result.successful());
        assertTrue(result.getMessage().contains("抢占"));
        verify(executionService, never()).execute(any(), any());
        verify(executionService, never()).executePlanTask(any(), any(), any(), any());
    }

    @Test
    void rerun_executionServiceFailureRestoresFailedStatus() {
        CodingTask task = taskWithStatus(CodingTaskStatus.FAILED);
        when(codingTaskDao.findOne(task.getId())).thenReturn(task);
        when(codingTaskDao.updateStatusIfMatch(task.getId(), CodingTaskStatus.FAILED, CodingTaskStatus.RUNNING))
                .thenReturn(1);
        when(executionService.execute(task.getId(), "rerun-prompt"))
                .thenReturn(ResultData.fail("已有运行中 Run"));

        ResultData<CodingTaskDto> result = service.rerun(task.getId(), "rerun-prompt");

        assertFalse(result.successful());
        verify(codingTaskDao).updateStatusIfMatch(task.getId(), CodingTaskStatus.RUNNING, CodingTaskStatus.FAILED);
    }

    @Test
    void rerun_disallowedStatuses_rejects() {
        for (CodingTaskStatus status : new CodingTaskStatus[]{
                CodingTaskStatus.PENDING,
                CodingTaskStatus.RUNNING,
                CodingTaskStatus.VALIDATING,
                CodingTaskStatus.SUCCEEDED,
                CodingTaskStatus.CANCELLED,
                CodingTaskStatus.BLOCKED,
                CodingTaskStatus.STALE
        }) {
            CodingTask task = taskWithStatus(status);
            when(codingTaskDao.findOne(task.getId())).thenReturn(task);

            ResultData<CodingTaskDto> result = service.rerun(task.getId(), "rerun-prompt");

            assertFalse(result.successful(), "status=" + status + " 应该被拒绝");
            verify(executionService, never()).execute(any(), any());
            clearInvocations(executionService);
        }
    }

    @Test
    void convertToDto_preservesExecutionPlanTraceFields() {
        CodingTask task = taskWithStatus(CodingTaskStatus.PENDING);
        task.setExecutionPlanId("plan-1");
        task.setPlanTaskKey("FE-001");
        task.setAssignedAgent("frontend-dev-agent");
        task.setLoopId("loop-1");
        task.setArea("frontend");
        task.setDependsOn(java.util.List.of("BE-001"));
        task.setFileScope(java.util.List.of("frontend/src"));

        CodingTaskDto dto = service.convertToDto(task);

        assertEquals("plan-1", dto.getExecutionPlanId());
        assertEquals("FE-001", dto.getPlanTaskKey());
        assertEquals("frontend-dev-agent", dto.getAssignedAgent());
        assertEquals("loop-1", dto.getLoopId());
        assertEquals("frontend", dto.getArea());
        assertEquals(java.util.List.of("BE-001"), dto.getDependsOn());
    }

    @Test
    void rerun_executionPlanTaskUsesSchedulerManagedExecution() {
        CodingTask task = taskWithStatus(CodingTaskStatus.VALIDATION_FAILED);
        task.setExecutionPlanId("plan-1");
        task.setAssignedAgent("backend-dev-agent");
        when(codingTaskDao.findOne(task.getId())).thenReturn(task);
        when(codingTaskDao.updateStatusIfMatch(task.getId(), CodingTaskStatus.VALIDATION_FAILED,
                CodingTaskStatus.RUNNING)).thenReturn(1);
        when(executionService.executePlanTask(task.getId(), "backend-dev-agent", "修复验证",
                com.changhong.onlinecode.dto.enums.TriggerSource.USER_ACTION))
                .thenReturn(ResultData.success(new CodingTaskDto()));

        ResultData<CodingTaskDto> result = service.rerun(task.getId(), "修复验证");

        assertTrue(result.successful());
        verify(executionService).executePlanTask(task.getId(), "backend-dev-agent", "修复验证",
                com.changhong.onlinecode.dto.enums.TriggerSource.USER_ACTION);
        verify(executionService, never()).execute(any(), any());
    }

    private CodingTask taskWithStatus(CodingTaskStatus status) {
        CodingTask task = new CodingTask();
        task.setId("task-" + status.name());
        task.setProjectId("proj-1");
        task.setRequirementId("req-1");
        task.setStatus(status);
        task.setTitle("测试任务");
        return task;
    }
}
