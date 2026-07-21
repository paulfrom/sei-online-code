package com.changhong.onlinecode.service.progress;

import com.changhong.onlinecode.dao.ExecutionCheckpointDao;
import com.changhong.onlinecode.dao.ExecutionStepDao;
import com.changhong.onlinecode.dao.RequirementWorkspaceDao;
import com.changhong.onlinecode.dao.RunObservationDao;
import com.changhong.onlinecode.dao.TaskExecutionDao;
import com.changhong.onlinecode.dto.enums.ExecutionCheckpointType;
import com.changhong.onlinecode.dto.enums.ExecutionStepStatus;
import com.changhong.onlinecode.dto.enums.TaskExecutionType;
import com.changhong.onlinecode.dto.progress.ProgressOperationResult;
import com.changhong.onlinecode.dto.progress.WriteAuthorization;
import com.changhong.onlinecode.entity.ExecutionCheckpoint;
import com.changhong.onlinecode.entity.ExecutionStep;
import com.changhong.onlinecode.entity.TaskExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProgressService 核心原子协议验收（EXE-002）。
 *
 * <p>WHY：覆盖 ADR-001 关键不变量——Execution 唯一回读、并发 claim 仅一个成功、旧 token 返回 STALE_OWNER、
 * VERIFIED 不可由普通更新回退、checkpoint 写失败与 step/snapshot 一并回滚。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock
    private TaskExecutionDao taskExecutionDao;

    @Mock
    private RequirementWorkspaceDao requirementWorkspaceDao;

    @Mock
    private ExecutionStepDao executionStepDao;

    @Mock
    private ExecutionCheckpointDao executionCheckpointDao;

    @Mock
    private RunObservationDao runObservationDao;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProgressService progressService;

    // ============================ find-or-create Execution ============================

    /** 新 key：不存在则插入并返回。 */
    @Test
    void findOrCreateExecution_newKey_insertsAndReturns() {
        when(taskExecutionDao.findByExecutionKey("k1")).thenReturn(Optional.empty());
        TaskExecution saved = new TaskExecution();
        saved.setExecutionKey("k1");
        when(taskExecutionDao.saveAndFlush(any())).thenReturn(saved);

        TaskExecution result = callFindOrCreate("k1");

        assertSame(saved, result);
        verify(taskExecutionDao).saveAndFlush(any());
    }

    /** 已存在 key：直接返回，不重复插入。 */
    @Test
    void findOrCreateExecution_existingKey_returnsExistingWithoutInsert() {
        TaskExecution existing = new TaskExecution();
        existing.setExecutionKey("k2");
        when(taskExecutionDao.findByExecutionKey("k2")).thenReturn(Optional.of(existing));

        TaskExecution result = callFindOrCreate("k2");

        assertSame(existing, result);
        verify(taskExecutionDao, never()).saveAndFlush(any());
    }

    /** 并发冲突：saveAndFlush 抛唯一约束异常 → 回读胜出者，不向外抛错。 */
    @Test
    void findOrCreateExecution_concurrentConflict_readsBackWinner() {
        TaskExecution winner = new TaskExecution();
        winner.setExecutionKey("k3");
        winner.setBusinessTaskId("biz-winner");
        when(taskExecutionDao.findByExecutionKey("k3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(taskExecutionDao.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_task_exec_execution_key"));

        TaskExecution result = callFindOrCreate("k3");

        assertSame(winner, result);
    }

    private TaskExecution callFindOrCreate(String key) {
        return progressService.findOrCreateExecution(key, TaskExecutionType.CODING_TASK,
                "biz-" + key, null, "req-1", "loop-1", "hash-" + key, 1, "ws-1", "commit-1");
    }

    // ============================ claim step (CAS) ============================

    /** claim 后必须清理 JPA 一级缓存，否则回读的是更新前的 owner/token，后续 markApplied 会稳定失败。 */
    @Test
    void claimStep_bulkUpdateClearsPersistenceContextBeforeReload() {
        assertBulkUpdateClearsPersistenceContext("claimStep");
        assertBulkUpdateClearsPersistenceContext("unblockForRetry");
    }

    private void assertBulkUpdateClearsPersistenceContext(String methodName) {
        Modifying modifying = java.util.Arrays.stream(ExecutionStepDao.class.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow()
                .getAnnotation(Modifying.class);
        assertTrue(modifying.clearAutomatically(), methodName + " must clear the persistence context");
        assertTrue(modifying.flushAutomatically(), methodName + " must flush before the bulk update");
    }

    /** CAS 成功：claim 返回 OK，claimToken 已生成，snapshotVersion 递增。 */
    @Test
    void claimStep_casSuccess_returnsOkAndBumpsSnapshot() {
        ExecutionStep pending = newStep("exec-1", ExecutionStepStatus.PENDING);
        ExecutionStep claimed = newStep("exec-1", ExecutionStepStatus.IN_PROGRESS);
        claimed.setClaimToken("tok");
        when(executionStepDao.findOne("s1")).thenReturn(pending, claimed);
        when(executionStepDao.claimStep(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(taskExecutionDao.findOne("exec-1")).thenReturn(execWithWs("ws-1"));
        when(requirementWorkspaceDao.incrementSnapshotVersion("ws-1")).thenReturn(1);

        ProgressOperationResult<ExecutionStep> result =
                progressService.claimStep("s1", auth("run-1", null), new Date());

        assertTrue(result.isOk());
        verify(requirementWorkspaceDao).incrementSnapshotVersion("ws-1");
    }

    /** CAS 返回 0（版本/状态被并发改写）：返回 STALE_OWNER，不 bump snapshot。 */
    @Test
    void claimStep_casMiss_returnsStaleOwnerWithoutBump() {
        ExecutionStep pending = newStep("exec-1", ExecutionStepStatus.PENDING);
        when(executionStepDao.findOne("s1")).thenReturn(pending);
        when(executionStepDao.claimStep(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        ProgressOperationResult<ExecutionStep> result =
                progressService.claimStep("s1", auth("run-1", null), new Date());

        assertTrue(result.isStaleOwner());
        verify(requirementWorkspaceDao, never()).incrementSnapshotVersion(any());
    }

    // ============================ markVerified (no downgrade) ============================

    /** APPLIED → VERIFIED：成功。 */
    @Test
    void markVerified_fromApplied_succeeds() {
        ExecutionStep applied = newStep("exec-1", ExecutionStepStatus.APPLIED);
        when(executionStepDao.findOne("s1")).thenReturn(applied);
        when(executionStepDao.markVerified(any(), any(), any(), any(), any())).thenReturn(1);
        when(taskExecutionDao.findOne("exec-1")).thenReturn(execWithWs("ws-1"));
        when(requirementWorkspaceDao.incrementSnapshotVersion("ws-1")).thenReturn(1);

        ProgressOperationResult<ExecutionStep> result =
                progressService.markVerified("s1", auth("run-1", "tok"));

        assertTrue(result.isOk());
    }

    /** 已 VERIFIED 再次 markVerified：幂等成功，不视为回退。 */
    @Test
    void markVerified_alreadyVerified_isIdempotentNotDowngrade() {
        ExecutionStep verified = newStep("exec-1", ExecutionStepStatus.VERIFIED);
        when(executionStepDao.findOne("s1")).thenReturn(verified);
        when(executionStepDao.markVerified(any(), any(), any(), any(), any())).thenReturn(0);

        ProgressOperationResult<ExecutionStep> result =
                progressService.markVerified("s1", auth("run-1", "tok"));

        assertTrue(result.isOk());
    }

    /** 非 APPLIED 且 owner 匹配：返回 INVALID_STATE（不可绕过状态机直接置 VERIFIED）。 */
    @Test
    void markVerified_wrongStateWithMatchingOwner_returnsInvalidState() {
        ExecutionStep inProgress = newStep("exec-1", ExecutionStepStatus.IN_PROGRESS);
        inProgress.setOwnerRunId("run-1");
        inProgress.setClaimToken("tok");
        when(executionStepDao.findOne("s1")).thenReturn(inProgress);
        when(executionStepDao.markVerified(any(), any(), any(), any(), any())).thenReturn(0);

        ProgressOperationResult<ExecutionStep> result =
                progressService.markVerified("s1", auth("run-1", "tok"));

        assertTrue(result.getStatus() == com.changhong.onlinecode.dto.progress.ProgressOperationStatus.INVALID_STATE);
    }

    // ============================ checkpoint rollback ============================

    /** step latest-checkpoint 更新被拒（stale owner）：抛错触发整体回滚（journal insert + snapshot 不落地）。 */
    @Test
    void appendCheckpoint_stepUpdateRejected_rollsBack() {
        when(executionCheckpointDao.findTopByExecutionIdOrderBySequenceNoDesc("exec-1")).thenReturn(Optional.empty());
        ExecutionCheckpoint saved = new ExecutionCheckpoint();
        saved.setId("cp-1");
        when(executionCheckpointDao.saveAndFlush(any())).thenReturn(saved);
        when(executionStepDao.updateLatestCheckpoint(any(), any(), any(), any(), any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () ->
                progressService.appendCheckpoint("s1", "exec-1", auth("run-1", "tok"),
                        ExecutionCheckpointType.PROGRESS, "payload", null, null, null));
    }

    // ============================ fixtures ============================

    private WriteAuthorization auth(String runId, String claimToken) {
        WriteAuthorization authorization = new WriteAuthorization();
        authorization.setRunId(runId);
        authorization.setClaimToken(claimToken);
        authorization.setFencingToken(1L);
        return authorization;
    }

    private ExecutionStep newStep(String executionId, ExecutionStepStatus status) {
        ExecutionStep step = new ExecutionStep();
        step.setExecutionId(executionId);
        step.setStatus(status);
        step.setVersion(0L);
        return step;
    }

    private TaskExecution execWithWs(String workspaceId) {
        TaskExecution execution = new TaskExecution();
        execution.setRequirementWorkspaceId(workspaceId);
        return execution;
    }
}
