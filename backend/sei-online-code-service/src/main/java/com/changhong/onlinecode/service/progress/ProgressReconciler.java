package com.changhong.onlinecode.service.progress;

import com.changhong.onlinecode.dao.ExecutionStepDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.TaskExecutionDao;
import com.changhong.onlinecode.dao.ExecutionEffectDao;
import com.changhong.onlinecode.dto.enums.BlockedRecoveryPolicy;
import com.changhong.onlinecode.dto.enums.ExecutionEffectStatus;
import com.changhong.onlinecode.dto.enums.ExecutionStepStatus;
import com.changhong.onlinecode.dto.enums.ObservationSourceType;
import com.changhong.onlinecode.dto.enums.RunObservationType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.dto.enums.TaskExecutionStatus;
import com.changhong.onlinecode.dto.enums.VerificationStatus;
import com.changhong.onlinecode.dto.progress.ProgressOperationResult;
import com.changhong.onlinecode.dto.progress.WriteAuthorization;
import com.changhong.onlinecode.entity.ExecutionStep;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.TaskExecution;
import com.changhong.sei.core.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ProgressReconciler —— 超时后的内部账本对账与租约释放。
 *
 * <p>Run 超时由执行层收敛为 FAILED/TIMEOUT；本服务只处理遗留的 step/workspace/effect 状态，
 * 不决定 CodingTask 是否成功、是否跳过或何时重试。</p>
 *
 * @author sei-online-code
 */
@Service
@Slf4j
public class ProgressReconciler {

    private final RunDao runDao;
    private final TaskExecutionDao taskExecutionDao;
    private final ExecutionStepDao executionStepDao;
    private final ExecutionEffectDao executionEffectDao;
    private final ProgressService progressService;
    private final WorkspaceLeaseService workspaceLeaseService;

    public ProgressReconciler(RunDao runDao,
                               TaskExecutionDao taskExecutionDao,
                               ExecutionStepDao executionStepDao,
                               ExecutionEffectDao executionEffectDao,
                               ProgressService progressService,
                               WorkspaceLeaseService workspaceLeaseService) {
        this.runDao = runDao;
        this.taskExecutionDao = taskExecutionDao;
        this.executionStepDao = executionStepDao;
        this.executionEffectDao = executionEffectDao;
        this.progressService = progressService;
        this.workspaceLeaseService = workspaceLeaseService;
    }

    // ======================== reconcileTimedOutRun ========================

    /**
     * 对账超时 Run：确保 Run 为 FAILED/TIMEOUT，将仍在执行的内部 step 标记为 UNKNOWN，并释放租约。
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
        // 兼容直接调用：超时 Run 必须是终态，不能继续以 RUNNING/UNKNOWN 表示仍在执行。
        if (run.getState() == RunState.RUNNING) {
            run.setState(RunState.FAILED);
            run.setTerminalReason(RunTerminalReason.TIMEOUT);
            run.setFinishedDate(new Date());
            run.setFailureSummary("运行超时（已终止）");
            run.setFailureReason("Run 超时；恢复由对应 CodingTask 的计划重试负责");
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
                    "Run 超时已终止，内部账本状态已进入对账",
                    "恢复由 CodingTask 计划重试负责；账本只记录证据并释放内部租约",
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
        int recoveredBlocked = 0;
        int humanBlocked = 0;
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
                BlockedRecovery recovery = blockedRecovery(step);
                if (recovery.canAutoRetryNow()) {
                    ProgressOperationResult<ExecutionStep> result = progressService.unblockForRetry(
                            step.getId(), unblockEvidence(step, recovery));
                    if (result.isOk()) {
                        recoveredBlocked++;
                        if (nextAction == null) {
                            nextAction = "claim:" + step.getStepKey();
                        }
                    } else {
                        blocked++;
                        humanBlocked++;
                    }
                } else {
                    blocked++;
                    if (recovery.needsHuman()) {
                        humanBlocked++;
                    }
                    if (nextAction == null) {
                        nextAction = recovery.nextAction(step.getStepKey());
                    }
                }
            } else if (status == ExecutionStepStatus.IN_PROGRESS) {
                if (nextAction == null) {
                    nextAction = "complete:" + step.getStepKey();
                }
            }
        }

        long unconfirmedEffects = executionEffectDao.countByExecutionIdAndStatusIn(executionId,
                List.of(ExecutionEffectStatus.PREPARED, ExecutionEffectStatus.APPLIED,
                        ExecutionEffectStatus.UNKNOWN, ExecutionEffectStatus.FAILED));
        if (nextAction == null && unconfirmedEffects > 0) {
            nextAction = "reconcile:effects";
        }
        boolean allVerified = totalRequired > 0 && verified == totalRequired && unconfirmedEffects == 0;
        if (allVerified && execution.getStatus() != TaskExecutionStatus.SUCCEEDED) {
            execution.setStatus(TaskExecutionStatus.SUCCEEDED);
            taskExecutionDao.save(execution);
            log.info("reconcileExecution: execution {} 全部 required steps VERIFIED → SUCCEEDED", executionId);
        }

        boolean needsHuman = humanBlocked > 0 || (unknown > 0 && applied == 0 && verified == 0);
        return new ExecutionReconciliation(executionId, totalRequired, verified, applied, unknown, blocked,
                recoveredBlocked, allVerified, needsHuman, nextAction);
    }

    /**
     * 按 requirement+loopId 查找最近 Execution 并对账（供 Compensator 使用）。
     */
    @Transactional(rollbackFor = Exception.class)
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

    private BlockedRecovery blockedRecovery(ExecutionStep step) {
        if (step.getEvidenceData() == null || step.getEvidenceData().isBlank()) {
            return BlockedRecovery.manual();
        }
        try {
            JsonNode root = JsonUtils.mapper().readTree(step.getEvidenceData());
            BlockedRecoveryPolicy policy = parsePolicy(root.path("recoveryPolicy").asText(null));
            Instant retryAfter = parseInstant(root.path("retryAfter").asText(null));
            String reason = root.path("blockedReason").asText(null);
            return new BlockedRecovery(policy, retryAfter, reason);
        } catch (Exception e) {
            log.warn("blockedRecovery: invalid evidenceData stepId={}", step.getId(), e);
            return BlockedRecovery.manual();
        }
    }

    private BlockedRecoveryPolicy parsePolicy(String value) {
        if (value == null || value.isBlank()) {
            return BlockedRecoveryPolicy.MANUAL_REVIEW;
        }
        try {
            return BlockedRecoveryPolicy.valueOf(value);
        } catch (IllegalArgumentException e) {
            return BlockedRecoveryPolicy.MANUAL_REVIEW;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String unblockEvidence(ExecutionStep step, BlockedRecovery recovery) {
        try {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("schemaVersion", 1);
            evidence.put("previousStatus", "BLOCKED");
            evidence.put("blockedReason", recovery.reason());
            evidence.put("recoveryPolicy", recovery.policy().name());
            evidence.put("recoveredAt", Instant.now().toString());
            evidence.put("recoveryAction", "AUTO_RETRY_UNBLOCK_TO_PENDING");
            evidence.put("previousEvidenceData", step.getEvidenceData());
            return JsonUtils.mapper().writeValueAsString(evidence);
        } catch (Exception e) {
            return "{\"schemaVersion\":1,\"recoveryAction\":\"AUTO_RETRY_UNBLOCK_TO_PENDING\"}";
        }
    }

    // ======================== result records ========================

    public record ReconciliationResult(String runId, String executionId, int stepsMarkedUnknown,
                                       boolean leaseReleased) {
        static ReconciliationResult notFound(String runId) {
            return new ReconciliationResult(runId, null, 0, false);
        }
    }

    private record BlockedRecovery(BlockedRecoveryPolicy policy, Instant retryAfter, String reason) {
        static BlockedRecovery manual() {
            return new BlockedRecovery(BlockedRecoveryPolicy.MANUAL_REVIEW, null, null);
        }

        boolean canAutoRetryNow() {
            return policy == BlockedRecoveryPolicy.AUTO_RETRY
                    && (retryAfter == null || !retryAfter.isAfter(Instant.now()));
        }

        boolean needsHuman() {
            return policy == BlockedRecoveryPolicy.MANUAL_REVIEW
                    || policy == BlockedRecoveryPolicy.REMEDIATION_STEP;
        }

        String nextAction(String stepKey) {
            if (policy == BlockedRecoveryPolicy.AUTO_RETRY || policy == BlockedRecoveryPolicy.WAIT) {
                return "wait:" + stepKey;
            }
            if (policy == BlockedRecoveryPolicy.REMEDIATION_STEP) {
                return "remediate:" + stepKey;
            }
            return "manual:" + stepKey;
        }
    }

    public record ExecutionReconciliation(String executionId, int totalRequired, int verified, int applied,
                                          int unknown, int blocked, int recoveredBlocked,
                                          boolean allVerified, boolean needsHuman,
                                          String nextAction) {
        static ExecutionReconciliation notFound(String executionId) {
            return new ExecutionReconciliation(executionId, 0, 0, 0, 0, 0, 0, false, false, null);
        }
    }
}
