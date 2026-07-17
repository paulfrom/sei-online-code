package com.changhong.onlinecode.service.progress;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementWorkspaceDao;
import com.changhong.onlinecode.dao.TaskExecutionDao;
import com.changhong.onlinecode.dto.enums.RequirementWorkspaceState;
import com.changhong.onlinecode.dto.progress.ProgressOperationResult;
import com.changhong.onlinecode.dto.progress.ProgressOperationStatus;
import com.changhong.onlinecode.dto.progress.WriteAuthorization;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementWorkspace;
import com.changhong.onlinecode.entity.TaskExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkspaceLeaseService 单元测试（EXE-005）。
 *
 * <p>WHY：覆盖 ADR-001 关键不变量——同一需求只有一个 workspace、lease/fencing 保护 Git 写操作、
 * HEAD/parent/token 不匹配进入对账而非覆盖、GC 安全判断。</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceLeaseServiceTest {

    @Mock
    private RequirementWorkspaceDao workspaceDao;

    @Mock
    private WorkspaceManager workspaceManager;

    @Mock
    private ProgressService progressService;

    @Mock
    private RequirementDao requirementDao;

    @Mock
    private TaskExecutionDao taskExecutionDao;

    @InjectMocks
    private WorkspaceLeaseService service;

    private static final String PROJECT_ID = "p1";
    private static final String REQUIREMENT_ID = "r1";
    private static final String WORKSPACE_ID = "ws1";
    private static final String RUN_ID = "run1";
    private static final String EXECUTION_ID = "exec1";
    private static final String HEAD_SHA = "abc123";
    private static final String NEW_HEAD_SHA = "def456";

    // ======================== bindOrResolveWorkspace ========================

    @Test
    void bindOrResolveWorkspace_existingRecord_returnsReused() {
        RequirementWorkspace existing = workspace("ws1");
        when(workspaceDao.findByProjectIdAndRequirementId(PROJECT_ID, REQUIREMENT_ID))
                .thenReturn(Optional.of(existing));
        // ensurePhysicalWorkspace mocks: use any() to avoid Path equality mismatches
        when(workspaceManager.requirementWorkspaceKey(REQUIREMENT_ID)).thenReturn("requirement-test");
        when(workspaceManager.resolveIsolatedWorkspace(eq(PROJECT_ID), anyString()))
                .thenReturn(Path.of("/tmp/test/ws1"));
        when(workspaceManager.getCurrentBranch(any(Path.class))).thenReturn("feature/test");

        RequirementWorkspace result = service.bindOrResolveWorkspace(PROJECT_ID, REQUIREMENT_ID);

        assertSame(existing, result);
        verify(workspaceDao, never()).saveAndFlush(any());
    }

    @Test
    void bindOrResolveWorkspace_newRecord_createsAndReturns() {
        when(workspaceDao.findByProjectIdAndRequirementId(PROJECT_ID, REQUIREMENT_ID))
                .thenReturn(Optional.empty());
        Requirement req = new Requirement();
        req.setRequirementNo("REQ-001");
        when(requirementDao.findOne(REQUIREMENT_ID)).thenReturn(req);
        Path mockPath = Path.of("/tmp/ws");
        when(workspaceManager.requirementWorkspaceKey(REQUIREMENT_ID)).thenReturn("requirement-REQ-001");
        when(workspaceManager.resolveRequirementWorkspace(PROJECT_ID, REQUIREMENT_ID)).thenReturn(mockPath);
        when(workspaceManager.getCurrentHead(mockPath)).thenReturn(HEAD_SHA);
        RequirementWorkspace saved = workspace("ws1");
        when(workspaceDao.saveAndFlush(any())).thenReturn(saved);

        RequirementWorkspace result = service.bindOrResolveWorkspace(PROJECT_ID, REQUIREMENT_ID);

        assertSame(saved, result);
        verify(workspaceDao).saveAndFlush(any());
    }

    @Test
    void bindOrResolveWorkspace_nullInputs_returnsNull() {
        assertNull(service.bindOrResolveWorkspace(null, REQUIREMENT_ID));
        assertNull(service.bindOrResolveWorkspace(PROJECT_ID, null));
        assertNull(service.bindOrResolveWorkspace("", REQUIREMENT_ID));
    }

    // ======================== acquireOwnership ========================

    @Test
    void acquireOwnership_success_acquiresLeaseAndFileLock() {
        RequirementWorkspace ws = workspace(WORKSPACE_ID);
        ws.setFencingToken(5L);
        ProgressOperationResult<RequirementWorkspace> leaseResult = ProgressOperationResult.ok(ws);
        when(progressService.acquireLease(eq(WORKSPACE_ID), eq(RUN_ID), eq(EXECUTION_ID), any()))
                .thenReturn(leaseResult);
        var mockLock = new java.util.concurrent.locks.ReentrantLock();
        when(workspaceManager.getFileLock(any())).thenReturn(mockLock);

        ProgressOperationResult<RequirementWorkspace> result = service.acquireOwnership(
                WORKSPACE_ID, RUN_ID, EXECUTION_ID, Duration.ofMinutes(5));

        assertTrue(result.isOk());
        assertEquals(5L, result.getData().getFencingToken());
    }

    @Test
    void acquireOwnership_staleOwner_returnsStaleOwner() {
        ProgressOperationResult<RequirementWorkspace> leaseResult =
                ProgressOperationResult.staleOwner("lease held by another");
        when(progressService.acquireLease(eq(WORKSPACE_ID), eq(RUN_ID), eq(EXECUTION_ID), any()))
                .thenReturn(leaseResult);

        ProgressOperationResult<RequirementWorkspace> result = service.acquireOwnership(
                WORKSPACE_ID, RUN_ID, EXECUTION_ID, Duration.ofMinutes(5));

        assertTrue(result.isStaleOwner());
    }

    // ======================== commitCheckpoint ========================

    @Test
    void commitCheckpoint_success_advancesHead() {
        RequirementWorkspace ws = workspace(WORKSPACE_ID);
        ws.setCurrentHead(HEAD_SHA);
        ws.setOwnerRunId(RUN_ID);
        ws.setFencingToken(1L);
        ws.setOwnerExecutionId(EXECUTION_ID);
        when(workspaceDao.findOne(WORKSPACE_ID)).thenReturn(ws);
        when(workspaceManager.getCurrentHead(any(Path.class))).thenReturn(HEAD_SHA);
        when(workspaceManager.commitAll(any(Path.class), anyString())).thenReturn(NEW_HEAD_SHA);
        when(workspaceDao.advanceCurrentHead(eq(WORKSPACE_ID), eq(HEAD_SHA), eq(NEW_HEAD_SHA),
                eq(RUN_ID), eq(1L))).thenReturn(1);
        TaskExecution exec = new TaskExecution();
        when(taskExecutionDao.findOne(EXECUTION_ID)).thenReturn(exec);

        WriteAuthorization auth = auth(RUN_ID, 1L);
        ProgressOperationResult<String> result = service.commitCheckpoint(
                WORKSPACE_ID, auth, "step1", HEAD_SHA);

        assertTrue(result.isOk());
        assertEquals(NEW_HEAD_SHA, result.getData());
    }

    @Test
    void commitCheckpoint_headMismatch_returnsConflict() {
        RequirementWorkspace ws = workspace(WORKSPACE_ID);
        ws.setCurrentHead("different-head");  // DB HEAD differs
        ws.setOwnerRunId(RUN_ID);
        ws.setFencingToken(1L);
        when(workspaceDao.findOne(WORKSPACE_ID)).thenReturn(ws);

        WriteAuthorization auth = auth(RUN_ID, 1L);
        ProgressOperationResult<String> result = service.commitCheckpoint(
                WORKSPACE_ID, auth, "step1", HEAD_SHA);

        assertEquals(ProgressOperationStatus.CONFLICT, result.getStatus());
        verify(workspaceManager, never()).commitAll(any(), anyString());
    }

    @Test
    void commitCheckpoint_staleOwner_returnsStaleOwner() {
        RequirementWorkspace ws = workspace(WORKSPACE_ID);
        ws.setOwnerRunId("other-run");  // different owner
        ws.setFencingToken(1L);
        when(workspaceDao.findOne(WORKSPACE_ID)).thenReturn(ws);

        WriteAuthorization auth = auth(RUN_ID, 1L);
        ProgressOperationResult<String> result = service.commitCheckpoint(
                WORKSPACE_ID, auth, "step1", HEAD_SHA);

        assertTrue(result.isStaleOwner());
        verify(workspaceManager, never()).commitAll(any(), anyString());
    }

    // ======================== releaseOwnership ========================

    @Test
    void releaseOwnership_releasesDbLease() {
        RequirementWorkspace ws = workspace(WORKSPACE_ID);
        when(workspaceDao.findOne(WORKSPACE_ID)).thenReturn(ws);
        var mockLock = new java.util.concurrent.locks.ReentrantLock();
        when(workspaceManager.getFileLock(any())).thenReturn(mockLock);

        service.releaseOwnership(WORKSPACE_ID, RUN_ID);

        verify(workspaceDao).releaseLease(WORKSPACE_ID, RUN_ID);
    }

    @Test
    void releaseOwnership_workspaceNotFound_doesNothing() {
        when(workspaceDao.findOne(WORKSPACE_ID)).thenReturn(null);

        service.releaseOwnership(WORKSPACE_ID, RUN_ID);

        verify(workspaceDao, never()).releaseLease(anyString(), anyString());
    }

    // ======================== isGcSafe ========================

    @Test
    void isGcSafe_activeState_returnsFalse() {
        RequirementWorkspace ws = workspace(WORKSPACE_ID);
        ws.setState(RequirementWorkspaceState.ACTIVE);
        when(workspaceDao.findOne(WORKSPACE_ID)).thenReturn(ws);

        assertFalse(service.isGcSafe(WORKSPACE_ID));
    }

    @Test
    void isGcSafe_blockedState_returnsFalse() {
        RequirementWorkspace ws = workspace(WORKSPACE_ID);
        ws.setState(RequirementWorkspaceState.BLOCKED);
        when(workspaceDao.findOne(WORKSPACE_ID)).thenReturn(ws);

        assertFalse(service.isGcSafe(WORKSPACE_ID));
    }

    @Test
    void isGcSafe_completedWithRetentionExpired_returnsTrue() {
        RequirementWorkspace ws = workspace(WORKSPACE_ID);
        ws.setState(RequirementWorkspaceState.COMPLETED);
        ws.setRetentionUntil(new Date(System.currentTimeMillis() - 86400000)); // yesterday
        when(workspaceDao.findOne(WORKSPACE_ID)).thenReturn(ws);

        assertTrue(service.isGcSafe(WORKSPACE_ID));
    }

    @Test
    void isGcSafe_completedWithRetentionNotExpired_returnsFalse() {
        RequirementWorkspace ws = workspace(WORKSPACE_ID);
        ws.setState(RequirementWorkspaceState.COMPLETED);
        ws.setRetentionUntil(new Date(System.currentTimeMillis() + 86400000)); // tomorrow
        when(workspaceDao.findOne(WORKSPACE_ID)).thenReturn(ws);

        assertFalse(service.isGcSafe(WORKSPACE_ID));
    }

    @Test
    void isGcSafe_notFound_returnsTrue() {
        when(workspaceDao.findOne(WORKSPACE_ID)).thenReturn(null);
        assertTrue(service.isGcSafe(WORKSPACE_ID));
    }

    // ======================== reconcileAfterRestart ========================

    @Test
    void reconcileAfterRestart_cleanState_reportsClean() {
        RequirementWorkspace ws = workspace(WORKSPACE_ID);
        ws.setBranchName("feature/REQ-001");
        ws.setCurrentHead(HEAD_SHA);
        when(workspaceDao.findOne(WORKSPACE_ID)).thenReturn(ws);
        when(workspaceManager.getCurrentHead(any())).thenReturn(HEAD_SHA);
        when(workspaceManager.getCurrentBranch(any())).thenReturn("feature/REQ-001");
        when(workspaceManager.hasUncommittedChanges(any())).thenReturn(false);

        WorkspaceLeaseService.ReconciliationReport report = service.reconcileAfterRestart(WORKSPACE_ID);

        assertNotNull(report);
        assertTrue(report.isClean());
        assertTrue(report.headMatches());
        assertTrue(report.branchMatches());
        assertFalse(report.hasUncommittedChanges());
    }

    @Test
    void reconcileAfterRestart_dirtyWorkspace_reportsDirty() {
        RequirementWorkspace ws = workspace(WORKSPACE_ID);
        ws.setBranchName("feature/REQ-001");
        ws.setCurrentHead(HEAD_SHA);
        when(workspaceDao.findOne(WORKSPACE_ID)).thenReturn(ws);
        when(workspaceManager.getCurrentHead(any())).thenReturn(HEAD_SHA);
        when(workspaceManager.getCurrentBranch(any())).thenReturn("feature/REQ-001");
        when(workspaceManager.hasUncommittedChanges(any())).thenReturn(true);
        when(workspaceManager.getGitDiff(any())).thenReturn("M file1.java");

        WorkspaceLeaseService.ReconciliationReport report = service.reconcileAfterRestart(WORKSPACE_ID);

        assertNotNull(report);
        assertFalse(report.isClean());
        assertTrue(report.hasUncommittedChanges());
    }

    // ======================== helpers ========================

    private static RequirementWorkspace workspace(String id) {
        RequirementWorkspace ws = new RequirementWorkspace();
        ws.setId(id);
        ws.setProjectId(PROJECT_ID);
        ws.setRequirementId(REQUIREMENT_ID);
        ws.setWorkspacePath("/tmp/test/" + id);
        ws.setBranchName("feature/test");
        ws.setBaseCommit(HEAD_SHA);
        ws.setCurrentHead(HEAD_SHA);
        ws.setFencingToken(0L);
        ws.setSnapshotVersion(0L);
        ws.setState(RequirementWorkspaceState.ACTIVE);
        return ws;
    }

    private static WriteAuthorization auth(String runId, Long fencingToken) {
        WriteAuthorization a = new WriteAuthorization();
        a.setRunId(runId);
        a.setClaimToken("claim-token-1");
        a.setFencingToken(fencingToken);
        return a;
    }
}
