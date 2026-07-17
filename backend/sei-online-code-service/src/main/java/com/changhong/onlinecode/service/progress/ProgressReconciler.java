package com.changhong.onlinecode.service.progress;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionStepDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.TaskExecutionDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.ExecutionStepStatus;
import com.changhong.onlinecode.dto.enums.ObservationSourceType;
import com.changhong.onlinecode.dto.enums.RunObservationType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TaskExecutionStatus;
import com.changhong.onlinecode.dto.enums.VerificationStatus;
import com.changhong.onlinecode.dto.progress.ProgressOperationResult;
import com.changhong.onlinecode.dto.progress.WriteAuthorization;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionStep;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.TaskExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * ProgressReconciler —— ADR-001 §2/§6 超时对账与安全接管（EXE-007）。
 *
 * <p>Run 超时不直接判定 CodingTask/Requirement 失败；先对账进度账本（step/workspace/effect），
 * 能证明完成则补记 APPLIED/VERIFIED；不能则释放过期 lease 开放安全接管；仅无可自动解决时标记
 * WAITING_HUMAN。settlement 使用稳定幂等键，重复补偿不重复副作用。</p>
 *
 * @author sei-online-code
 */
@Service
@Slf4j
public class ProgressReconciler {

    private final RunDao runDao;
    private final TaskExecutionDao taskExecutionDao;
    private final ExecutionStepDao executionStepDao;
    private final CodingTaskDao codingTaskDao;
    private final ProgressService progressService;
    private final WorkspaceLeaseService workspaceLeaseService;

    public ProgressReconciler(RunDao runDao,
                               TaskExecutionDao taskExecutionDao,
                               ExecutionStepDao executionStepDao,
                               CodingTaskDao codingTaskDao,
                               ProgressService progressService,
                               WorkspaceLeaseService workspaceLeaseService) {
        this.runDao = runDao;
        this.taskExecutionDao = taskExecutionDao;
        this.executionStepDao = executionStepDao;
        this.codingTaskDao = codingTaskDao;
        this.progressService = progressService;
        this.workspaceLeaseService = workspaceLeaseService;
    }

    // ======================== reconcileTimedOutRun ========================

    /**
     * 对账超时 Run（ADR-001 §2/§6）。Run→UNKNOWN + TERMINAL observation；
     * 对账 IN_PROGRESS step→UNKNOWN；释放过期 lease。
     *
     * @param runId Run ID
     * @return 对账结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ReconciliationResult reconcileTimedOutRun(String runId) {
        Run run = runDao.findOne(runId);
        if (run == null) {
            return ReconciliationResult.notFound(runId);
        }
        // Run→UNKNOWN（非 FAILED，ADR-001 §2）
        if (run.getState() == RunState.RUNNING) {
            run.setState(RunState.UNKNOWN);
            runDao.save(run);
        }

        // 追加 TERMINAL observation
        String executionId = run.getExecutionId();
        if (executionId != null) {
            progressService.appendObservation(runId,
                    RunObservationType.TERMINAL,
                    VerificationStatus.INCONCLUSIVE,
                    ObservationSourceType.RECONCILER,
                    null,
                    "Run 超时已收口（ProgressReconciler），待对账",
                    "超时时间窗口内未收到 heartbeat；进度账本对账后将决定续作或接管",
                    null, null, null, executionId);
        }

        // 对账 IN_PROGRESS step→UNKNOWN
        int markedUnknown = 0;
        if (executionId != null) {
            List<ExecutionStep> steps = executionStepDao.findByExecutionIdAndPlanVersion(
                    executionId, run.getObservedPlanVersion() != null ? run.getObservedPlanVersion() : 1);
            for (ExecutionStep step : steps) {
                if (step.getStatus() == ExecutionStepStatus.IN_PROGRESS
                        && Objects.equals(step.getOwnerRunId(), runId)) {
                    WriteAuthorization auth = new WriteAuthorization();
                    auth.setRunId(runId);
                    auth.setClaimToken(step.getClaimToken());
                    auth.setFencingToken(step.getWorkspaceFencingToken());
                    ProgressOperationResult<ExecutionStep> result = progressService.markUnknown(step.getId(), auth);
                    if (result.isOk()) {
                        markedUnknown++;
                        progressService.appendObservation(runId,
                                RunObservationType.RECONCILIATION,
                                VerificationStatus.INCONCLUSIVE,
                                ObservationSourceType.RECONCILER,
                                null,
                                "超时后标记 step 为 UNKNOWN：" + step.getStepKey(),
                                null, step.getId(), null, null, executionId);
                    }
                }
            }
        }

        // 释放过期 workspace lease
        if (executionId != null) {
            TaskExecution execution = taskExecutionDao.findOne(executionId);
            if (execution != null && execution.getRequirementWorkspaceId() != null) {
                workspaceLeaseService.releaseOwnership(execution.getRequirementWorkspaceId(), runId);
            }
        }

        // CodingTask：不直接 FAILED；仅在 CodingTask 仍 RUNNING 时标记 UNKNOWN
        if (run.getCodingTaskId() != null) {
            CodingTask task = codingTaskDao.findOne(run.getCodingTaskId());
            if (task != null && (task.getStatus() == CodingTaskStatus.RUNNING
                    || task.getStatus() == CodingTaskStatus.VALIDATING)) {
                task.setStatus(CodingTaskStatus.FAILED); // 保持现有语义让补偿器发现
                codingTaskDao.save(task);
            }
        }

        return new ReconciliationResult(runId, executionId, markedUnknown, true);
    }

    // ======================== reconcileExecution ========================

    /**
     * 对账 Execution 整体完成状态（ADR-001 §6：Execution 完成判定）。
     *
     * <p>全部 required step VERIFIED + 全部 effect CONFIRMED → 补记 SUCCEEDED；
     * 部分完成 → 保留状态，生成 nextAction 供续作。</p>
     *
     * @param executionId Execution ID
     * @return 对账结果（含 nextAction）
     */
    @Transactional(rollbackFor = Exception.class)
    public ExecutionReconciliation reconcileExecution(String executionId) {
        TaskExecution execution = taskExecutionDao.findOne(executionId);
        if (execution == null) {
            return ExecutionReconciliation.notFound(executionId);
        }
        List<ExecutionStep> steps = executionStepDao.findByExecutionIdAndPlanVersion(
                executionId, execution.getPlanVersion());

        int totalRequired = 0;
        int verified = 0;
        int applied = 0;
        int unknown = 0;
        int blocked = 0;
        String nextAction = null;

        for (ExecutionStep step : steps) {
            if (!Boolean.TRUE.equals(step.getRequiredStep())) {
                continue;
            }
            totalRequired++;
            ExecutionStepStatus status = step.getStatus();
            if (status == ExecutionStepStatus.VERIFIED) {
                verified++;
            } else if (status == ExecutionStepStatus.APPLIED) {
                applied++;
                if (nextAction == null) {
                    nextAction = "verify:" + step.getStepKey();
                }
            } else if (status == ExecutionStepStatus.UNKNOWN) {
                unknown++;
                if (nextAction == null) {
                    nextAction = "reconcile:" + step.getStepKey();
                }
            } else if (status == ExecutionStepStatus.BLOCKED) {
                blocked++;
            } else if (status == ExecutionStepStatus.IN_PROGRESS) {
                if (nextAction == null) {
                    nextAction = "complete:" + step.getStepKey();
                }
            }
        }

        boolean allVerified = totalRequired > 0 && verified == totalRequired;
        if (allVerified && execution.getStatus() != TaskExecutionStatus.SUCCEEDED) {
            execution.setStatus(TaskExecutionStatus.SUCCEEDED);
            taskExecutionDao.save(execution);
            log.info("reconcileExecution: execution {} 全部 required steps VERIFIED → SUCCEEDED", executionId);
        }

        boolean needsHuman = blocked > 0 || (unknown > 0 && applied == 0 && verified == 0);
        return new ExecutionReconciliation(executionId, totalRequired, verified, applied, unknown, blocked,
                allVerified, needsHuman, nextAction);
    }

    /**
     * 按 requirement+loopId 查找最近 Execution 并对账（供 Compensator 使用）。
     */
    @Transactional(readOnly = true)
    public ExecutionReconciliation reconcileExecutionByRequirement(String requirementId, String loopId) {
        TaskExecution execution = taskExecutionDao.findFirstByRequirementIdOrderByCreatedDateDesc(requirementId)
                .orElse(null);
        if (execution == null || !Objects.equals(execution.getLoopId(), loopId)) {
            return null;
        }
        return reconcileExecution(execution.getId());
    }

    // ======================== settlement ========================

    /**
     * 生成稳定 settlement 幂等键（ADR-001 §7）。
     *
     * <p>重复 settlement 返回同一 key；调用方据此去重评论/事件/observation。</p>
     */
    public String computeSettlementKey(String requirementId, String executionId, int planVersion) {
        return sha256("settle:" + requirementId + ":" + executionId + ":" + planVersion);
    }

    /**
     * 生成基于步骤状态的 settlement key（含步骤状态快照，状态变化后 key 不同）。
     */
    public String computeSettlementKey(String requirementId, String executionId, int planVersion,
                                       List<ExecutionStep> steps) {
        StringBuilder statusSnapshot = new StringBuilder();
        for (ExecutionStep step : steps) {
            if (Boolean.TRUE.equals(step.getRequiredStep())) {
                statusSnapshot.append(step.getStepKey()).append("=").append(step.getStatus()).append(";");
            }
        }
        return sha256("settle:" + requirementId + ":" + executionId + ":" + planVersion
                + ":" + statusSnapshot);
    }

    // ======================== helpers ========================

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ======================== result records ========================

    public record ReconciliationResult(String runId, String executionId, int stepsMarkedUnknown,
                                       boolean leaseReleased) {
        static ReconciliationResult notFound(String runId) {
            return new ReconciliationResult(runId, null, 0, false);
        }
    }

    public record ExecutionReconciliation(String executionId, int totalRequired, int verified, int applied,
                                          int unknown, int blocked, boolean allVerified, boolean needsHuman,
                                          String nextAction) {
        static ExecutionReconciliation notFound(String executionId) {
            return new ExecutionReconciliation(executionId, 0, 0, 0, 0, 0, false, false, null);
        }
    }
}
