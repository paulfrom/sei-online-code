package com.changhong.onlinecode.service.progress;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementWorkspaceDao;
import com.changhong.onlinecode.dao.TaskExecutionDao;
import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.enums.RequirementWorkspaceState;
import com.changhong.onlinecode.dto.progress.ProgressOperationResult;
import com.changhong.onlinecode.dto.progress.WriteAuthorization;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementWorkspace;
import com.changhong.onlinecode.entity.TaskExecution;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.service.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WorkspaceLeaseService —— ADR-001 §3/§5/§10.2/§10.5 工作区协调层（EXE-005）。
 *
 * <p>桥接 DB {@link RequirementWorkspace} 与物理 workspace 目录，统一 lease/fencing/commit 协议。
 * 保证同一需求只有一个 workspace、一个 feature branch 和一个写 owner；Git 写操作受 fencing token 保护；
 * 工作区 HEAD 由 CAS 推进，不匹配时进入对账而非覆盖。</p>
 *
 * <p>不在此 scope：push/MR（EXE-006）、进程终止、patch artifact 持久化（EXE-007）、跨进程锁。</p>
 *
 * @author sei-online-code
 */
@Service
@Slf4j
public class WorkspaceLeaseService {

    private final RequirementWorkspaceDao workspaceDao;
    private final WorkspaceManager workspaceManager;
    private final ProgressService progressService;
    private final RequirementDao requirementDao;
    private final TaskExecutionDao taskExecutionDao;
    private final ProjectDao projectDao;
    private final ConfigService configService;

    public WorkspaceLeaseService(RequirementWorkspaceDao workspaceDao,
                                  WorkspaceManager workspaceManager,
                                  ProgressService progressService,
                                  RequirementDao requirementDao,
                                  TaskExecutionDao taskExecutionDao,
                                  ProjectDao projectDao,
                                  ConfigService configService) {
        this.workspaceDao = workspaceDao;
        this.workspaceManager = workspaceManager;
        this.progressService = progressService;
        this.requirementDao = requirementDao;
        this.taskExecutionDao = taskExecutionDao;
        this.projectDao = projectDao;
        this.configService = configService;
    }

    // ======================== bindOrResolveWorkspace ========================

    /**
     * 绑定或解析需求工作区（ADR-001 不变量 1：同一 requirement 只有一个 workspace）。
     *
     * <p>查询 DB：已存在 → 确保物理目录与分支存在（重启恢复），返回已有记录。
     * 不存在 → 生成唯一 branch + path，创建物理目录，INSERT DB 记录（并发冲突回读已有）。</p>
     *
     * @param projectId     项目 ID
     * @param requirementId 需求 ID
     * @return 工作区记录（已持久化）; projectId/requirementId 为空时返回 null
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RequirementWorkspace bindOrResolveWorkspace(String projectId, String requirementId) {
        if (projectId == null || projectId.isBlank() || requirementId == null || requirementId.isBlank()) {
            return null;
        }
        // 已有 DB 记录：复用（重启恢复路径）
        Optional<RequirementWorkspace> existing = workspaceDao.findByProjectIdAndRequirementId(projectId, requirementId);
        if (existing.isPresent()) {
            RequirementWorkspace ws = existing.get();
            ensurePhysicalWorkspace(ws);
            return ws;
        }
        // 新建
        Requirement requirement = requirementDao.findOne(requirementId);
        String branchName = deriveBranchName(requirement);
        String workspaceKey = workspaceManager.requirementWorkspaceKey(requirementId);
        Path wsPath = workspaceManager.resolveRequirementWorkspace(projectId, requirementId);
        String workspacePath = wsPath.toAbsolutePath().normalize().toString();
        String baseCommit = workspaceManager.getCurrentHead(wsPath);

        RequirementWorkspace ws = new RequirementWorkspace();
        ws.setProjectId(projectId);
        ws.setRequirementId(requirementId);
        ws.setWorkspacePath(workspacePath);
        ws.setBranchName(branchName);
        ws.setBaseCommit(baseCommit);
        ws.setCurrentHead(baseCommit);
        ws.setFencingToken(0L);
        ws.setSnapshotVersion(0L);
        ws.setState(RequirementWorkspaceState.ACTIVE);
        try {
            return workspaceDao.saveAndFlush(ws);
        } catch (DataIntegrityViolationException e) {
            // 并发：另一个线程已创建，回读唯一记录（ADR-001 不变量 1）
            log.debug("bindOrResolveWorkspace: (project={}, requirement={}) 并发冲突，回读已有记录",
                    projectId, requirementId);
            return workspaceDao.findByProjectIdAndRequirementId(projectId, requirementId)
                    .orElseThrow(() -> new IllegalStateException(
                            "workspace vanished after unique-key conflict: " + projectId + "/" + requirementId, e));
        }
    }

    /**
     * Re-read the physical Git state and persist the authoritative workspace head/branch.
     * This operation deliberately does not reset, merge or rebase completed artifacts.
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceRefreshResult refreshWorkspace(String requirementId) {
        Requirement requirement = requirementDao.findOne(requirementId);
        if (requirement == null) {
            throw new IllegalArgumentException("需求不存在: " + requirementId);
        }
        RequirementWorkspace ws = workspaceDao
                .findByProjectIdAndRequirementId(requirement.getProjectId(), requirementId)
                .orElse(null);
        if (ws == null || ws.getWorkspacePath() == null
                || !Files.isDirectory(Path.of(ws.getWorkspacePath()))) {
            ws = bindOrResolveWorkspace(requirement.getProjectId(), requirementId);
        }
        if (ws == null) {
            throw new IllegalStateException("需求工作区解析失败: " + requirementId);
        }
        Date now = new Date();
        if (ws.getOwnerRunId() != null && ws.getLeaseExpiresAt() != null
                && ws.getLeaseExpiresAt().after(now)) {
            throw new IllegalStateException("工作区正在被运行占用，暂不能手动刷新");
        }

        Path path = Path.of(ws.getWorkspacePath());
        String physicalBranch = workspaceManager.getCurrentBranch(path);
        String physicalHead = workspaceManager.getCurrentHead(path);
        List<String> changedFiles = workspaceManager.getChangedFiles(path);
        if (!physicalBranch.isBlank()) {
            ws.setBranchName(physicalBranch);
        }
        ws.setCurrentHead(physicalHead);
        ws.setSnapshotVersion((ws.getSnapshotVersion() == null ? 0L : ws.getSnapshotVersion()) + 1L);
        ws.setLastProgressAt(now);
        workspaceDao.save(ws);

        Project project = projectDao.findOne(requirement.getProjectId());
        String baseBranch = project == null || project.getWorkspaceBaseBranch() == null
                || project.getWorkspaceBaseBranch().isBlank() ? "main" : project.getWorkspaceBaseBranch();
        String targetBranch = project == null ? null : project.getDeliveryTargetBranch();
        if (targetBranch == null || targetBranch.isBlank()) {
            targetBranch = configService.resolveGitlabTargetBranch(null);
        }
        return new WorkspaceRefreshResult(ws.getWorkspacePath(), ws.getBranchName(), baseBranch,
                targetBranch, ws.getBaseCommit(), physicalHead, !changedFiles.isEmpty(), changedFiles, now);
    }

    /** Update the configured base branch and merge it into the requirement's current branch. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public WorkspaceRefreshResult synchronizeWorkspace(String requirementId) {
        Requirement requirement = requirementDao.findOne(requirementId);
        if (requirement == null) {
            throw new IllegalArgumentException("需求不存在: " + requirementId);
        }
        RequirementWorkspace ws = workspaceDao.findByRequirementId(requirementId)
                .orElseThrow(() -> new IllegalStateException("需求工作区不存在，请先刷新工作区"));
        Date now = new Date();
        if (ws.getOwnerRunId() != null && ws.getLeaseExpiresAt() != null
                && ws.getLeaseExpiresAt().after(now)) {
            throw new IllegalStateException("工作区正在被运行占用，暂不能同步基线分支");
        }
        Project project = projectDao.findOne(requirement.getProjectId());
        String baseBranch = project == null || project.getWorkspaceBaseBranch() == null
                || project.getWorkspaceBaseBranch().isBlank() ? "main" : project.getWorkspaceBaseBranch().trim();
        WorkspaceManager.WorkspaceSyncResult synced = workspaceManager.syncBaseBranch(
                Path.of(ws.getWorkspacePath()), baseBranch);
        ws.setBranchName(synced.branchName());
        ws.setBaseCommit(synced.baseHead());
        ws.setCurrentHead(synced.currentHead());
        ws.setSnapshotVersion((ws.getSnapshotVersion() == null ? 0L : ws.getSnapshotVersion()) + 1L);
        ws.setLastProgressAt(now);
        workspaceDao.save(ws);
        String targetBranch = project == null ? null : project.getDeliveryTargetBranch();
        if (targetBranch == null || targetBranch.isBlank()) {
            targetBranch = configService.resolveGitlabTargetBranch(null);
        }
        return new WorkspaceRefreshResult(ws.getWorkspacePath(), synced.branchName(), baseBranch,
                targetBranch, synced.baseHead(), synced.currentHead(), false, List.of(), now);
    }

    // ======================== acquireOwnership ========================

    /**
     * 获取工作区写所有权（ADR-001 §10.2）。
     *
     * <p>先在 DB 获取 lease（CAS → fencing_token 递增）；成功后获取物理文件锁。
     * 失败（lease 被他人持有）→ STALE_OWNER，不获取文件锁。</p>
     *
     * @param workspaceId   工作区 ID
     * @param runId         申请 owner Run ID
     * @param executionId   关联 Execution ID
     * @param leaseDuration lease 有效期
     * @return 成功时 data 为更新后的 RequirementWorkspace（含新 fencingToken）
     */
    @Transactional(rollbackFor = Exception.class)
    public ProgressOperationResult<RequirementWorkspace> acquireOwnership(String workspaceId, String runId,
                                                                           String executionId, Duration leaseDuration) {
        Date leaseExpiresAt = Date.from(Instant.now().plus(leaseDuration));
        ProgressOperationResult<RequirementWorkspace> result = progressService.acquireLease(
                workspaceId, runId, executionId, leaseExpiresAt);
        if (!result.isOk()) {
            return result;
        }
        RequirementWorkspace ws = result.getData();
        Path wsPath = Path.of(ws.getWorkspacePath());
        ReentrantLock fileLock = workspaceManager.getFileLock(wsPath);
        fileLock.lock();
        log.info("acquireOwnership: workspace={}, runId={}, fencingToken={}, leaseExpiresAt={}",
                workspaceId, runId, ws.getFencingToken(), leaseExpiresAt);
        return result;
    }

    // ======================== commitCheckpoint ========================

    /**
     * 提交 checkpoint commit 并 CAS 推进 workspace HEAD（ADR-001 §5/§10.5）。
     *
     * <p>校验链：DB owner/fencing → DB currentHead == expectedHead → 物理 HEAD == expectedHead
     * → git add -A + commit → CAS advanceCurrentHead。任一不匹配进入 UNKNOWN/BLOCKED 对账，不覆盖。
     * DB CAS 失败时 best-effort git reset --soft HEAD~1 回滚。</p>
     *
     * @param workspaceId   工作区 ID
     * @param auth          写授权（runId, claimToken, fencingToken）
     * @param stepKey       步骤 key（用于 commit message）
     * @param expectedHead  期望的当前 HEAD（即 parentGitHead）
     * @return 成功时 data 为新 HEAD SHA
     */
    @Transactional(rollbackFor = Exception.class)
    public ProgressOperationResult<String> commitCheckpoint(String workspaceId, WriteAuthorization auth,
                                                             String stepKey, String expectedHead) {
        RequirementWorkspace ws = workspaceDao.findOne(workspaceId);
        if (ws == null) {
            return ProgressOperationResult.notFound("workspace not found: " + workspaceId);
        }
        // 校验 owner + fencing token
        if (!auth.getRunId().equals(ws.getOwnerRunId()) || !auth.getFencingToken().equals(ws.getFencingToken())) {
            return ProgressOperationResult.staleOwner(
                    "commitCheckpoint rejected: stale owner or fencing token mismatch");
        }
        // 校验 DB currentHead
        if (!expectedHead.equals(ws.getCurrentHead())) {
            log.warn("commitCheckpoint: DB HEAD mismatch workspace={}, expected={}, actual={} → BLOCKED 对账",
                    workspaceId, expectedHead, ws.getCurrentHead());
            return ProgressOperationResult.conflict(
                    "commitCheckpoint: DB HEAD mismatch. expected=" + expectedHead + " actual=" + ws.getCurrentHead());
        }
        // 校验物理 HEAD
        Path wsPath = Path.of(ws.getWorkspacePath());
        String physicalHead = workspaceManager.getCurrentHead(wsPath);
        if (!expectedHead.equals(physicalHead)) {
            log.warn("commitCheckpoint: 物理 HEAD 不匹配 workspace={}, expected={}, actual={} → BLOCKED 对账",
                    workspaceId, expectedHead, physicalHead);
            return ProgressOperationResult.conflict(
                    "commitCheckpoint: physical HEAD mismatch. expected=" + expectedHead + " actual=" + physicalHead);
        }
        // git commit
        String newHead;
        try {
            String message = "checkpoint: " + (stepKey != null ? stepKey : "progress");
            newHead = workspaceManager.commitAll(wsPath, message);
        } catch (IllegalStateException e) {
            return ProgressOperationResult.invalidState("git commit failed: " + e.getMessage());
        }
        // 无变更：newHead 等于 expectedHead，DB HEAD 无需推进
        if (newHead.equals(expectedHead)) {
            return ProgressOperationResult.ok(expectedHead);
        }
        // CAS 推进 DB currentHead
        int updated = workspaceDao.advanceCurrentHead(
                workspaceId, expectedHead, newHead, auth.getRunId(), auth.getFencingToken());
        if (updated == 0) {
            // DB CAS 失败 → best-effort git rollback
            log.warn("commitCheckpoint: DB CAS 失败 workspace={}, expectedHead={}, newHead={} → git reset 回滚",
                    workspaceId, expectedHead, newHead);
            workspaceManager.resetSoftHead(wsPath);
            return ProgressOperationResult.conflict(
                    "commitCheckpoint: DB CAS failed (concurrent head advance). git reset triggered, manual reconcile needed.");
        }
        // 更新 Execution.latestHead
        if (ws.getOwnerExecutionId() != null) {
            TaskExecution execution = taskExecutionDao.findOne(ws.getOwnerExecutionId());
            if (execution != null) {
                execution.setLatestHead(newHead);
                taskExecutionDao.save(execution);
            }
        }
        progressService.bumpSnapshotForWorkspace(workspaceId);
        log.info("commitCheckpoint: workspace={}, stepKey={}, head {}→{}", workspaceId, stepKey, expectedHead, newHead);
        return ProgressOperationResult.ok(newHead);
    }

    // ======================== reconcileAfterRestart ========================

    /**
     * 进程重启后对账工作区状态（ADR-001 §5 接管前对账）。
     *
     * <p>只读操作：比较 DB 记录与物理 git 状态，生成报告。不修改任何文件或 DB 记录。
     * 调用方根据报告决定 safe-to-continue / reconcile / takeover。</p>
     *
     * @param workspaceId 工作区 ID
     * @return 对账报告；workspace 不存在返回 null
     */
    public ReconciliationReport reconcileAfterRestart(String workspaceId) {
        RequirementWorkspace ws = workspaceDao.findOne(workspaceId);
        if (ws == null) {
            return null;
        }
        Path wsPath = Path.of(ws.getWorkspacePath());
        String physicalHead = workspaceManager.getCurrentHead(wsPath);
        String physicalBranch = workspaceManager.getCurrentBranch(wsPath);
        boolean hasUncommitted = workspaceManager.hasUncommittedChanges(wsPath);
        String diffStat = hasUncommitted ? workspaceManager.getGitDiff(wsPath) : "";
        boolean headMatches = ws.getCurrentHead().equals(physicalHead);
        boolean branchMatches = ws.getBranchName().equals(physicalBranch);
        boolean leaseExpired = ws.getLeaseExpiresAt() == null
                || ws.getLeaseExpiresAt().before(new Date());
        return new ReconciliationReport(
                ws.getId(),
                ws.getRequirementId(),
                ws.getOwnerRunId(),
                ws.getOwnerExecutionId(),
                ws.getFencingToken(),
                ws.getState(),
                ws.getBranchName(),
                ws.getCurrentHead(),
                physicalBranch,
                physicalHead,
                hasUncommitted,
                diffStat,
                headMatches,
                branchMatches,
                leaseExpired);
    }

    // ======================== releaseOwnership ========================

    /**
     * 释放工作区所有权（DB lease + 物理文件锁）。
     *
     * @param workspaceId 工作区 ID
     * @param runId       当前 owner Run ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseOwnership(String workspaceId, String runId) {
        RequirementWorkspace ws = workspaceDao.findOne(workspaceId);
        if (ws == null) {
            return;
        }
        // 释放 DB lease
        workspaceDao.releaseLease(workspaceId, runId);
        // 释放物理文件锁
        Path wsPath = Path.of(ws.getWorkspacePath());
        ReentrantLock fileLock = workspaceManager.getFileLock(wsPath);
        if (fileLock.isHeldByCurrentThread()) {
            fileLock.unlock();
        }
        log.info("releaseOwnership: workspace={}, runId={}", workspaceId, runId);
    }

    // ======================== isGcSafe ========================

    /**
     * 判断工作区是否可安全 GC（ADR-001 §12）。
     *
     * <p>ACTIVE/UNKNOWN/BLOCKED → 禁止 GC；DELIVERING → 禁止 GC；
     * 终态（COMPLETED） + retention 未到期 → 禁止 GC；retention 已到期 → 可 GC。</p>
     *
     * @param workspaceId 工作区 ID
     * @return true 表示可安全清理
     */
    public boolean isGcSafe(String workspaceId) {
        RequirementWorkspace ws = workspaceDao.findOne(workspaceId);
        if (ws == null) {
            return true; // 无记录，可清理
        }
        RequirementWorkspaceState state = ws.getState();
        if (state == RequirementWorkspaceState.ACTIVE
                || state == RequirementWorkspaceState.BLOCKED) {
            log.debug("isGcSafe: workspace={} state={} → 禁止 GC", workspaceId, state);
            return false;
        }
        if (state == RequirementWorkspaceState.DELIVERING) {
            log.debug("isGcSafe: workspace={} state=DELIVERING → 禁止 GC", workspaceId);
            return false;
        }
        // COMPLETED: 检查 retention
        if (ws.getRetentionUntil() != null && ws.getRetentionUntil().after(new Date())) {
            log.debug("isGcSafe: workspace={} state=COMPLETED retentionUntil={} → 禁止 GC",
                    workspaceId, ws.getRetentionUntil());
            return false;
        }
        return true;
    }

    // ======================== helpers ========================

    /** 确保物理工作区目录与分支存在（重启恢复路径）。 */
    private void ensurePhysicalWorkspace(RequirementWorkspace ws) {
        Path wsPath = Path.of(ws.getWorkspacePath());
        workspaceManager.resolveIsolatedWorkspace(
                ws.getProjectId(),
                workspaceManager.requirementWorkspaceKey(ws.getRequirementId()));
        // 确保在记录的分支上
        String currentBranch = workspaceManager.getCurrentBranch(wsPath);
        if (!ws.getBranchName().equals(currentBranch) && !currentBranch.isBlank()) {
            // 分支不匹配：以 DB 为准 checkout
            log.info("ensurePhysicalWorkspace: 分支不匹配 DB={}, physical={}, 以 DB 为准 checkout",
                    ws.getBranchName(), currentBranch);
            workspaceManager.ensureOnBranch(wsPath, ws.getBranchName());
        }
    }

    /** 生成 feature branch 名称。 */
    private String deriveBranchName(Requirement requirement) {
        if (requirement != null && requirement.getRequirementNo() != null
                && !requirement.getRequirementNo().isBlank()) {
            return "feature/" + requirement.getRequirementNo().trim();
        }
        return "feature/requirement-workspace";
    }

    // ======================== ReconciliationReport ========================

    /**
     * 工作区对账报告（ADR-001 §5/§6：Run 崩溃后接手前对账）。
     */
    public record ReconciliationReport(
            String workspaceId,
            String requirementId,
            String dbOwnerRunId,
            String dbOwnerExecutionId,
            Long dbFencingToken,
            RequirementWorkspaceState dbState,
            String dbBranch,
            String dbHead,
            String physicalBranch,
            String physicalHead,
            boolean hasUncommittedChanges,
            String diffStat,
            boolean headMatches,
            boolean branchMatches,
            boolean leaseExpired
    ) {
        /** DB 与物理状态完全一致且无未提交变更。 */
        public boolean isClean() {
            return headMatches && branchMatches && !hasUncommittedChanges;
        }

        /** 可安全接管（lease 已过期或兼容）。 */
        public boolean isSafeToTakeOver() {
            return leaseExpired;
        }
    }


    public record WorkspaceRefreshResult(
            String workspacePath,
            String branchName,
            String baseBranch,
            String deliveryTargetBranch,
            String baseCommit,
            String currentHead,
            boolean dirty,
            List<String> changedFiles,
            Date refreshedAt
    ) {
    }
}
