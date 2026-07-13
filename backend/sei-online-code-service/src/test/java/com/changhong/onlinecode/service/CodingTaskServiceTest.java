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
                CodingTaskStatus.SUCCEEDED,
                CodingTaskStatus.CANCELLED
        }) {
            CodingTask task = taskWithStatus(status);
            when(codingTaskDao.findOne(task.getId())).thenReturn(task);
            when(executionService.execute(eq(task.getId()), eq("rerun-prompt")))
                    .thenReturn(ResultData.success(new CodingTaskDto()));

            ResultData<CodingTaskDto> result = service.rerun(task.getId(), "rerun-prompt");

            assertTrue(result.successful(), "status=" + status + " 应该允许重跑");
            verify(executionService).execute(eq(task.getId()), eq("rerun-prompt"));
            clearInvocations(executionService);
        }
    }

    @Test
    void rerun_disallowedStatuses_rejects() {
        for (CodingTaskStatus status : new CodingTaskStatus[]{
                CodingTaskStatus.PENDING,
                CodingTaskStatus.RUNNING,
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
