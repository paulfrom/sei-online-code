package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TaskExecutionType;
import com.changhong.onlinecode.dto.progress.ExecutionProgressSnapshot;
import com.changhong.onlinecode.dto.progress.StepSummary;
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
import static org.mockito.Mockito.when;

/**
 * CodingTaskProgressIntegrator 单测（EXE-004 batch 1）。
 *
 * <p>WHY：executionKey 稳定 + 已完成 Execution 跳过 + invocation 幂等复用，是 at-least-once 续作的前置。</p>
 */
@ExtendWith(MockitoExtension.class)
class CodingTaskProgressIntegratorTest {

    @Mock
    private ProgressService progressService;

    @Mock
    private RunDao runDao;

    @Mock
    private com.changhong.onlinecode.dao.ExecutionCheckpointDao executionCheckpointDao;

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

    /** 全部必填步骤 VERIFIED → shouldSkip=true（已完成 Execution 不再启动模型）。 */
    @Test
    void preflight_allVerified_shouldSkip() {
        stubExecution("exec-1");
        when(progressService.generateSnapshot("exec-1")).thenReturn(snapshot(2, 2, null));
        when(runDao.findByCodingTaskId("ct-1")).thenReturn(Collections.emptyList());

        CodingTaskProgressIntegrator.ProgressPreflight result = integrator.preflight(
                "ct-1", "req-1", TaskExecutionType.CODING_TASK, "loop-1", 1, "prompt", "ws-1", "commit-1");

        assertTrue(result.shouldSkip());
        assertEquals("exec-1", result.executionId());
        assertNull(result.reusedRunId());
        assertNotNull(result.invocationKey());
    }

    /** 仅部分 VERIFIED → shouldSkip=false；新 attempt 使用新 invocationKey，避免保存新 Run 时撞唯一键。 */
    @Test
    void preflight_partialProgress_doesNotSkipAndUsesFreshInvocationKey() {
        stubExecution("exec-2");
        when(progressService.generateSnapshot("exec-2")).thenReturn(snapshot(2, 1, "step-x"));
        Run active = new Run();
        active.setId("run-active");
        active.setState(RunState.RUNNING);
        active.setInvocationKey("inv-existing");
        when(runDao.findByCodingTaskId("ct-2")).thenReturn(List.of(active));

        CodingTaskProgressIntegrator.ProgressPreflight result = integrator.preflight(
                "ct-2", "req-1", TaskExecutionType.CODING_TASK, "loop-1", 1, "prompt", "ws-1", "commit-1");

        assertFalse(result.shouldSkip());
        assertNotEquals("inv-existing", result.invocationKey(), "新 attempt 不能复用 active Run 的 invocationKey");
        assertEquals("run-active", result.reusedRunId());
    }

    /** 无活跃 Run → 新 invocationKey（最小可确定唯一 id = UUID）。 */
    @Test
    void preflight_noActiveRun_generatesNewInvocationKey() {
        stubExecution("exec-3");
        when(progressService.generateSnapshot("exec-3")).thenReturn(snapshot(0, 0, null));
        when(runDao.findByCodingTaskId("ct-3")).thenReturn(Collections.emptyList());

        CodingTaskProgressIntegrator.ProgressPreflight result = integrator.preflight(
                "ct-3", "req-1", TaskExecutionType.CODING_TASK, "loop-1", 1, "prompt", "ws-1", "commit-1");

        assertFalse(result.shouldSkip(), "空 Execution（无步骤）不跳过");
        assertNotNull(result.invocationKey());
        assertNull(result.reusedRunId());
    }

    private void stubExecution(String executionId) {
        TaskExecution execution = new TaskExecution();
        execution.setId(executionId);
        execution.setExecutionKey("k-" + executionId);
        when(progressService.findOrCreateExecution(any(), eq(TaskExecutionType.CODING_TASK), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(execution);
    }

    private ExecutionProgressSnapshot snapshot(int required, int verified, String currentStepKey) {
        ExecutionProgressSnapshot snapshot = new ExecutionProgressSnapshot();
        StepSummary summary = new StepSummary();
        summary.setRequired(required);
        summary.setVerified(verified);
        snapshot.setStepSummary(summary);
        if (currentStepKey != null) {
            com.changhong.onlinecode.dto.progress.CurrentStepView current = new com.changhong.onlinecode.dto.progress.CurrentStepView();
            current.setStepKey(currentStepKey);
            snapshot.setCurrentStep(current);
        }
        return snapshot;
    }
}
