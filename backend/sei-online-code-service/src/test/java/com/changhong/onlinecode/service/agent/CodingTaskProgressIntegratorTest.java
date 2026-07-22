package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TaskExecutionType;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.TaskExecution;
import com.changhong.onlinecode.service.progress.ProgressService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * CodingTaskProgressIntegrator 单测。
 *
 * <p>账本只负责 Execution 绑定与观测记录，不能声明平行步骤或跳过 Agent 执行。</p>
 */
@ExtendWith(MockitoExtension.class)
class CodingTaskProgressIntegratorTest {

    @Mock
    private ProgressService progressService;

    @Mock
    private RunDao runDao;

    @InjectMocks
    private CodingTaskProgressIntegrator integrator;

    /** executionKey 对相同输入稳定，对任一输入变化敏感。 */
    @Test
    void computeExecutionKey_isStableAndSensitive() {
        String a = CodingTaskProgressIntegrator.computeExecutionKey(
                TaskExecutionType.CODING_TASK, "biz-1", "loop-1", 1, "hash-1");
        String b = CodingTaskProgressIntegrator.computeExecutionKey(
                TaskExecutionType.CODING_TASK, "biz-1", "loop-1", 1, "hash-1");
        String c = CodingTaskProgressIntegrator.computeExecutionKey(
                TaskExecutionType.CODING_TASK, "biz-1", "loop-1", 1, "hash-2");
        assertEquals(a, b, "same inputs -> same key");
        assertFalse(a.equals(c), "different input hash -> different key");
    }

    /** preflight 只绑定 Execution，不为 CodingTask 声明平行步骤。 */
    @Test
    void preflight_bindsExecutionWithoutDeclaringParallelSteps() {
        stubExecution("exec-1");
        when(runDao.findByCodingTaskId("ct-1")).thenReturn(Collections.emptyList());

        CodingTaskProgressIntegrator.ProgressPreflight result = integrator.preflight(
                "ct-1", "req-1", TaskExecutionType.CODING_TASK, "loop-1", 1, "prompt", "ws-1", "commit-1");

        assertEquals("exec-1", result.executionId());
        assertNull(result.reusedRunId());
        assertNotNull(result.invocationKey());
        verify(progressService, never()).declareStep(any(), any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    /** 新 attempt 使用新 invocationKey，避免保存新 Run 时撞唯一键。 */
    @Test
    void preflight_partialProgress_doesNotSkipAndUsesFreshInvocationKey() {
        stubExecution("exec-2");
        Run active = new Run();
        active.setId("run-active");
        active.setState(RunState.RUNNING);
        active.setInvocationKey("inv-existing");
        when(runDao.findByCodingTaskId("ct-2")).thenReturn(List.of(active));

        CodingTaskProgressIntegrator.ProgressPreflight result = integrator.preflight(
                "ct-2", "req-1", TaskExecutionType.CODING_TASK, "loop-1", 1, "prompt", "ws-1", "commit-1");

        assertNotEquals("inv-existing", result.invocationKey(), "新 attempt 不能复用 active Run 的 invocationKey");
        assertEquals("run-active", result.reusedRunId());
    }

    /** 无活跃 Run → 新 invocationKey（最小可确定唯一 id = UUID）。 */
    @Test
    void preflight_noActiveRun_generatesNewInvocationKey() {
        stubExecution("exec-3");
        when(runDao.findByCodingTaskId("ct-3")).thenReturn(Collections.emptyList());

        CodingTaskProgressIntegrator.ProgressPreflight result = integrator.preflight(
                "ct-3", "req-1", TaskExecutionType.CODING_TASK, "loop-1", 1, "prompt", "ws-1", "commit-1");

        assertNotNull(result.invocationKey());
        assertNull(result.reusedRunId());
    }

    /** 无首个 Git commit 时用全零 SHA 持久化，不能把 null 写入账本必填列。 */
    @Test
    void preflight_nullBaseCommit_usesUnbornHeadPlaceholder() {
        stubExecution("exec-unborn");
        when(runDao.findByCodingTaskId("ct-unborn")).thenReturn(Collections.emptyList());

        integrator.preflight("ct-unborn", "req-1", TaskExecutionType.CODING_TASK,
                "loop-1", 1, "prompt", "ws-1", null);

        verify(progressService).findOrCreateExecution(any(), eq(TaskExecutionType.CODING_TASK),
                eq("ct-unborn"), eq(null), eq("req-1"), eq("loop-1"), any(), eq(1), eq("ws-1"),
                eq("0000000000000000000000000000000000000000"));
    }

    /** 未绑定 Execution 时无需写账本，不能影响任务成功。 */
    @Test
    void recordSuccessfulCodingTaskCompletion_withoutExecution_isNoOpSuccess() {
        Run run = new Run();
        run.setId("run-no-execution");

        boolean result = integrator.recordSuccessfulCodingTaskCompletion(run, "done");

        assertTrue(result);
    }

    private void stubExecution(String executionId) {
        TaskExecution execution = new TaskExecution();
        execution.setId(executionId);
        execution.setExecutionKey("k-" + executionId);
        when(progressService.findOrCreateExecution(any(), eq(TaskExecutionType.CODING_TASK), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(execution);
    }

}
