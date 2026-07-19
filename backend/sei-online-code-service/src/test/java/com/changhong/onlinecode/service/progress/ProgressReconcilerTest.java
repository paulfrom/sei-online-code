package com.changhong.onlinecode.service.progress;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionEffectDao;
import com.changhong.onlinecode.dao.ExecutionStepDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.TaskExecutionDao;
import com.changhong.onlinecode.dto.enums.ExecutionStepStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TaskExecutionStatus;
import com.changhong.onlinecode.dto.progress.ProgressOperationResult;
import com.changhong.onlinecode.dto.progress.WriteAuthorization;
import com.changhong.onlinecode.entity.ExecutionStep;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.TaskExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressReconcilerTest {

    @Mock private RunDao runDao;
    @Mock private TaskExecutionDao taskExecutionDao;
    @Mock private ExecutionStepDao executionStepDao;
    @Mock private ExecutionEffectDao executionEffectDao;
    @Mock private CodingTaskDao codingTaskDao;
    @Mock private ProgressService progressService;
    @Mock private WorkspaceLeaseService workspaceLeaseService;

    @InjectMocks
    private ProgressReconciler reconciler;

    private static final String RUN_ID = "run-1";
    private static final String EXECUTION_ID = "exec-1";

    // ======================== reconcileTimedOutRun ========================

    @Test
    void reconcileTimedOutRun_runningToUnknown_callsReconciler() {
        Run run = new Run();
        run.setId(RUN_ID);
        run.setState(RunState.RUNNING);
        run.setExecutionId(EXECUTION_ID);
        run.setObservedPlanVersion(1);
        when(runDao.findOne(RUN_ID)).thenReturn(run);
        when(executionStepDao.findByExecutionIdAndPlanVersion(EXECUTION_ID, 1))
                .thenReturn(List.of());

        ProgressReconciler.ReconciliationResult result = reconciler.reconcileTimedOutRun(RUN_ID);

        assertNotNull(result);
        assertEquals(RUN_ID, result.runId());
        verify(runDao).save(run);
    }

    @Test
    void reconcileTimedOutRun_inProgressSteps_markedUnknown() {
        Run run = new Run();
        run.setId(RUN_ID);
        run.setState(RunState.RUNNING);
        run.setExecutionId(EXECUTION_ID);
        run.setObservedPlanVersion(1);
        when(runDao.findOne(RUN_ID)).thenReturn(run);
        ExecutionStep step = new ExecutionStep();
        step.setId("step-1");
        step.setStepKey("impl-1");
        step.setStatus(ExecutionStepStatus.IN_PROGRESS);
        step.setOwnerRunId(RUN_ID);
        step.setClaimToken("claim-1");
        step.setWorkspaceFencingToken(1L);
        when(executionStepDao.findByExecutionIdAndPlanVersion(EXECUTION_ID, 1))
                .thenReturn(List.of(step));
        when(progressService.markUnknown(eq("step-1"), any(WriteAuthorization.class)))
                .thenReturn(ProgressOperationResult.ok(step));

        ProgressReconciler.ReconciliationResult result = reconciler.reconcileTimedOutRun(RUN_ID);

        assertNotNull(result);
        assertEquals(1, result.stepsMarkedUnknown());
        verify(progressService).markUnknown(eq("step-1"), any(WriteAuthorization.class));
    }

    @Test
    void reconcileTimedOutRun_runNotFound_returnsNotFound() {
        when(runDao.findOne(RUN_ID)).thenReturn(null);

        ProgressReconciler.ReconciliationResult result = reconciler.reconcileTimedOutRun(RUN_ID);

        assertNotNull(result);
        assertNull(result.executionId());
        assertEquals(0, result.stepsMarkedUnknown());
    }

    @Test
    void reconcileTimedOutRun_doesNotFailCodingTask() {
        Run run = new Run();
        run.setId(RUN_ID);
        run.setState(RunState.RUNNING);
        run.setExecutionId(EXECUTION_ID);
        run.setCodingTaskId("coding-task-1");
        run.setObservedPlanVersion(1);
        when(runDao.findOne(RUN_ID)).thenReturn(run);
        when(executionStepDao.findByExecutionIdAndPlanVersion(EXECUTION_ID, 1))
                .thenReturn(List.of());

        reconciler.reconcileTimedOutRun(RUN_ID);

        verifyNoInteractions(codingTaskDao);
    }

    // ======================== reconcileExecution ========================

    @Test
    void reconcileExecution_allVerified_marksSucceeded() {
        TaskExecution execution = new TaskExecution();
        execution.setId(EXECUTION_ID);
        execution.setPlanVersion(1);
        execution.setStatus(TaskExecutionStatus.PENDING);
        when(taskExecutionDao.findOne(EXECUTION_ID)).thenReturn(execution);
        ExecutionStep step = new ExecutionStep();
        step.setStepKey("step-1");
        step.setStatus(ExecutionStepStatus.VERIFIED);
        step.setRequiredStep(true);
        when(executionStepDao.findByExecutionIdAndPlanVersion(EXECUTION_ID, 1))
                .thenReturn(List.of(step));
        when(executionEffectDao.countByExecutionIdAndStatusIn(eq(EXECUTION_ID), any()))
                .thenReturn(0L);

        ProgressReconciler.ExecutionReconciliation result = reconciler.reconcileExecution(EXECUTION_ID);

        assertNotNull(result);
        assertTrue(result.allVerified());
        assertEquals(1, result.totalRequired());
        assertEquals(1, result.verified());
    }

    @Test
    void reconcileExecution_unconfirmedEffect_doesNotMarkSucceeded() {
        TaskExecution execution = new TaskExecution();
        execution.setId(EXECUTION_ID);
        execution.setPlanVersion(1);
        execution.setStatus(TaskExecutionStatus.PENDING);
        when(taskExecutionDao.findOne(EXECUTION_ID)).thenReturn(execution);
        ExecutionStep step = new ExecutionStep();
        step.setStepKey("step-1");
        step.setStatus(ExecutionStepStatus.VERIFIED);
        step.setRequiredStep(true);
        when(executionStepDao.findByExecutionIdAndPlanVersion(EXECUTION_ID, 1))
                .thenReturn(List.of(step));
        when(executionEffectDao.countByExecutionIdAndStatusIn(eq(EXECUTION_ID), any()))
                .thenReturn(1L);

        ProgressReconciler.ExecutionReconciliation result = reconciler.reconcileExecution(EXECUTION_ID);

        assertNotNull(result);
        assertFalse(result.allVerified());
        assertEquals("reconcile:effects", result.nextAction());
        assertEquals(TaskExecutionStatus.PENDING, execution.getStatus());
        verify(taskExecutionDao, never()).save(execution);
    }

    @Test
    void reconcileExecution_blockedAutoRetryDue_unblocksAndReturnsClaimAction() {
        TaskExecution execution = new TaskExecution();
        execution.setId(EXECUTION_ID);
        execution.setPlanVersion(1);
        execution.setStatus(TaskExecutionStatus.ACTIVE);
        when(taskExecutionDao.findOne(EXECUTION_ID)).thenReturn(execution);
        ExecutionStep blocked = new ExecutionStep();
        blocked.setId("step-blocked");
        blocked.setStepKey("implement:x");
        blocked.setStatus(ExecutionStepStatus.BLOCKED);
        blocked.setRequiredStep(true);
        blocked.setEvidenceData("{\"schemaVersion\":1,\"blockedReason\":\"TRANSIENT_TOOL_FAILURE\","
                + "\"recoveryPolicy\":\"AUTO_RETRY\",\"retryAfter\":\"2000-01-01T00:00:00Z\"}");
        when(executionStepDao.findByExecutionIdAndPlanVersion(EXECUTION_ID, 1))
                .thenReturn(List.of(blocked));
        when(progressService.unblockForRetry(eq("step-blocked"), any()))
                .thenReturn(ProgressOperationResult.ok(blocked));
        when(executionEffectDao.countByExecutionIdAndStatusIn(eq(EXECUTION_ID), any()))
                .thenReturn(0L);

        ProgressReconciler.ExecutionReconciliation result = reconciler.reconcileExecution(EXECUTION_ID);

        assertEquals(1, result.recoveredBlocked());
        assertEquals(0, result.blocked());
        assertFalse(result.needsHuman());
        assertEquals("claim:implement:x", result.nextAction());
        verify(progressService).unblockForRetry(eq("step-blocked"), any());
    }

    @Test
    void reconcileExecution_blockedManualReview_keepsBlockedAndNeedsHuman() {
        TaskExecution execution = new TaskExecution();
        execution.setId(EXECUTION_ID);
        execution.setPlanVersion(1);
        execution.setStatus(TaskExecutionStatus.ACTIVE);
        when(taskExecutionDao.findOne(EXECUTION_ID)).thenReturn(execution);
        ExecutionStep blocked = new ExecutionStep();
        blocked.setId("step-manual");
        blocked.setStepKey("deliver:x");
        blocked.setStatus(ExecutionStepStatus.BLOCKED);
        blocked.setRequiredStep(true);
        blocked.setEvidenceData("{\"schemaVersion\":1,\"blockedReason\":\"UNKNOWN_EFFECT\","
                + "\"recoveryPolicy\":\"MANUAL_REVIEW\"}");
        when(executionStepDao.findByExecutionIdAndPlanVersion(EXECUTION_ID, 1))
                .thenReturn(List.of(blocked));
        when(executionEffectDao.countByExecutionIdAndStatusIn(eq(EXECUTION_ID), any()))
                .thenReturn(0L);

        ProgressReconciler.ExecutionReconciliation result = reconciler.reconcileExecution(EXECUTION_ID);

        assertEquals(0, result.recoveredBlocked());
        assertEquals(1, result.blocked());
        assertTrue(result.needsHuman());
        assertEquals("manual:deliver:x", result.nextAction());
        verify(progressService, never()).unblockForRetry(any(), any());
    }

    @Test
    void reconcileExecution_notFound_returnsNotFound() {
        when(taskExecutionDao.findOne(EXECUTION_ID)).thenReturn(null);

        ProgressReconciler.ExecutionReconciliation result = reconciler.reconcileExecution(EXECUTION_ID);

        assertNotNull(result);
        assertEquals(0, result.totalRequired());
        assertFalse(result.allVerified());
    }

    // ======================== settlement ========================

    @Test
    void computeSettlementKey_sameInput_sameOutput() {
        String key1 = reconciler.computeSettlementKey("req-1", EXECUTION_ID, 1);
        String key2 = reconciler.computeSettlementKey("req-1", EXECUTION_ID, 1);

        assertEquals(key1, key2);
    }

    @Test
    void computeSettlementKey_differentInput_differentOutput() {
        String key1 = reconciler.computeSettlementKey("req-1", EXECUTION_ID, 1);
        String key2 = reconciler.computeSettlementKey("req-2", EXECUTION_ID, 1);

        assertNotEquals(key1, key2);
    }
}
