package com.changhong.onlinecode.service.progress;

import com.changhong.onlinecode.dao.ExecutionCheckpointDao;
import com.changhong.onlinecode.dao.ExecutionStepDao;
import com.changhong.onlinecode.dao.RequirementWorkspaceDao;
import com.changhong.onlinecode.dao.RunObservationDao;
import com.changhong.onlinecode.dao.TaskExecutionDao;
import com.changhong.onlinecode.dto.enums.ExecutionCheckpointType;
import com.changhong.onlinecode.dto.enums.ExecutionPhase;
import com.changhong.onlinecode.dto.enums.ExecutionStepStatus;
import com.changhong.onlinecode.dto.enums.ObservationSourceType;
import com.changhong.onlinecode.dto.enums.RunObservationType;
import com.changhong.onlinecode.dto.enums.TaskExecutionStatus;
import com.changhong.onlinecode.dto.enums.TaskExecutionType;
import com.changhong.onlinecode.dto.enums.VerificationStatus;
import com.changhong.onlinecode.dto.progress.ExecutionProgressSnapshot;
import com.changhong.onlinecode.dto.progress.CurrentStepView;
import com.changhong.onlinecode.dto.progress.ProgressOperationResult;
import com.changhong.onlinecode.dto.progress.StepSummary;
import com.changhong.onlinecode.dto.progress.WriteAuthorization;
import com.changhong.onlinecode.entity.ExecutionCheckpoint;
import com.changhong.onlinecode.entity.ExecutionStep;
import com.changhong.onlinecode.entity.RequirementWorkspace;
import com.changhong.onlinecode.entity.RunObservation;
import com.changhong.onlinecode.entity.TaskExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ProgressService —— ADR-001 核心原子协议（EXE-002）。
 *
 * <p>承担 Execution find-or-create、Requirement lease、step declare/claim/heartbeat、checkpoint、
 * markApplied/markVerified/markUnknown、observation 与 reconcile 命令；所有写命令校验 runId、claimToken、
 * fencingToken、loop 与 planVersion（通过 CAS WHERE 子句隐式校验，不匹配返回 STALE_OWNER，不产生部分写入）；
 * 每次可观测提交递增 requirement snapshotVersion；并提供 {@link ExecutionProgressSnapshot} 与 nextAction 生成。</p>
 *
 * <p>多实体协调器（非单实体 CRUD），采用 {@code @Service} + 注入多 DAO + {@code @Transactional}，
 * 与 {@code CodingTaskExecutionService} 一致。完整的补偿对账编排属 EXE-007（ProgressReconciler）。</p>
 *
 * @author sei-online-code
 */
@Service
@Slf4j
public class ProgressService {

    /** 可被 claim 的步骤状态：未开始或失败/阻塞/未知后重试（ADR-001 §6）。 */
    private static final EnumSet<ExecutionStepStatus> CLAIMABLE_STATUSES = EnumSet.of(
            ExecutionStepStatus.PENDING,
            ExecutionStepStatus.UNKNOWN,
            ExecutionStepStatus.BLOCKED,
            ExecutionStepStatus.FAILED);

    private final TaskExecutionDao taskExecutionDao;
    private final RequirementWorkspaceDao requirementWorkspaceDao;
    private final ExecutionStepDao executionStepDao;
    private final ExecutionCheckpointDao executionCheckpointDao;
    private final RunObservationDao runObservationDao;

    public ProgressService(TaskExecutionDao taskExecutionDao,
                           RequirementWorkspaceDao requirementWorkspaceDao,
                           ExecutionStepDao executionStepDao,
                           ExecutionCheckpointDao executionCheckpointDao,
                           RunObservationDao runObservationDao) {
        this.taskExecutionDao = taskExecutionDao;
        this.requirementWorkspaceDao = requirementWorkspaceDao;
        this.executionStepDao = executionStepDao;
        this.executionCheckpointDao = executionCheckpointDao;
        this.runObservationDao = runObservationDao;
    }

    // ============================ Execution ============================

    /**
     * 查找或创建 Execution（ADR-001 §10.1 / 不变量 2）。
     *
     * <p>WHY：重复 Run 通过稳定 execution_key 共享同一 Execution；并发争抢同一 key 时必须回读首次记录，
     * 而非报错或生成第二条。先查；不存在则 saveAndFlush 触发唯一约束；命中约束（并发败者）时回读胜出者。
     * {@link Propagation#NOT_SUPPORTED} 挂起外层事务，使 saveAndFlush 在自身事务中隔离失败。</p>
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TaskExecution findOrCreateExecution(String executionKey, TaskExecutionType taskType,
                                                String businessTaskId, String codingTaskId,
                                                String requirementId, String loopId,
                                                String inputHash, Integer planVersion,
                                                String requirementWorkspaceId, String baseCommit) {
        Optional<TaskExecution> existing = taskExecutionDao.findByExecutionKey(executionKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        TaskExecution candidate = buildExecution(executionKey, taskType, businessTaskId, codingTaskId,
                requirementId, loopId, inputHash, planVersion, requirementWorkspaceId, baseCommit);
        try {
            return taskExecutionDao.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException e) {
            log.debug("findOrCreateExecution: execution_key={} 并发冲突，回读首次记录", executionKey);
            return taskExecutionDao.findByExecutionKey(executionKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "execution vanished after unique-key conflict: " + executionKey, e));
        }
    }

    // ============================ Requirement lease ============================

    /**
     * 获取 Requirement 写 lease（ADR-001 §10.2）。成功时 fencing_token 递增；被他人持有有效 lease 时返回 STALE_OWNER。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProgressOperationResult<RequirementWorkspace> acquireLease(String workspaceId, String runId,
                                                                      String executionId, Date leaseExpiresAt) {
        int updated = requirementWorkspaceDao.acquireLease(workspaceId, runId, executionId, leaseExpiresAt);
        if (updated == 0) {
            return ProgressOperationResult.staleOwner("requirement lease held by another active owner");
        }
        return ProgressOperationResult.ok(requirementWorkspaceDao.findOne(workspaceId));
    }

    // ============================ Step lifecycle ============================

    /**
     * 声明步骤（find-or-create）。同 Execution 计划版本内 (executionId, stepKey, planVersion) 唯一；
     * 并发声明命中约束时回读既有步骤。初始状态 PENDING。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ExecutionStep declareStep(String executionId, String stepKey, ExecutionPhase phase, Integer planVersion,
                                     String title, String description, String inputHash, boolean requiredStep) {
        Optional<ExecutionStep> existing = executionStepDao
                .findByExecutionIdAndStepKeyAndPlanVersion(executionId, stepKey, planVersion);
        if (existing.isPresent()) {
            return existing.get();
        }
        ExecutionStep candidate = buildStep(executionId, stepKey, phase, planVersion, title, description,
                inputHash, requiredStep);
        try {
            return executionStepDao.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException e) {
            log.debug("declareStep: (execution={}, stepKey={}, planVersion={}) 并发冲突，回读既有步骤",
                    executionId, stepKey, planVersion);
            return executionStepDao.findByExecutionIdAndStepKeyAndPlanVersion(executionId, stepKey, planVersion)
                    .orElseThrow(() -> new IllegalStateException("step vanished after unique conflict: " + stepKey, e));
        }
    }

    /**
     * Claim 步骤（ADR-001 §10.3）。CAS 校验期望 version 与状态可 claim；成功生成新 claimToken、捕获 fencingToken、
     * 递增 attempt_count/version。并发 claim 只有一个成功（不变量）。失败返回 STALE_OWNER。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProgressOperationResult<ExecutionStep> claimStep(String stepId, WriteAuthorization auth,
                                                            Date leaseExpiresAt) {
        ExecutionStep step = executionStepDao.findOne(stepId);
        if (step == null) {
            return ProgressOperationResult.notFound("step not found: " + stepId);
        }
        String claimToken = UUID.randomUUID().toString();
        int updated = executionStepDao.claimStep(stepId, step.getVersion(), CLAIMABLE_STATUSES,
                ExecutionStepStatus.IN_PROGRESS, auth.getRunId(), claimToken,
                auth.getFencingToken(), leaseExpiresAt);
        if (updated == 0) {
            return ProgressOperationResult.staleOwner("step not claimable (wrong version/state) or held by another owner");
        }
        bumpSnapshot(step.getExecutionId());
        return ProgressOperationResult.ok(executionStepDao.findOne(stepId));
    }

    /**
     * 心跳续 lease。owner_run_id/claim_token 不匹配返回 STALE_OWNER。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProgressOperationResult<ExecutionStep> heartbeat(String stepId, WriteAuthorization auth,
                                                            Date leaseExpiresAt) {
        int updated = executionStepDao.heartbeat(stepId, auth.getRunId(), auth.getClaimToken(),
                leaseExpiresAt, ExecutionStepStatus.IN_PROGRESS);
        if (updated == 0) {
            return ProgressOperationResult.staleOwner("heartbeat rejected: stale owner or step not in progress");
        }
        return ProgressOperationResult.ok(executionStepDao.findOne(stepId));
    }

    /**
     * 标记 APPLIED。仅 IN_PROGRESS/UNKNOWN 且 owner 匹配时推进；已是 APPLIED 视为幂等成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProgressOperationResult<ExecutionStep> markApplied(String stepId, WriteAuthorization auth) {
        int updated = executionStepDao.markApplied(stepId, auth.getRunId(), auth.getClaimToken(),
                ExecutionStepStatus.APPLIED, ExecutionStepStatus.IN_PROGRESS, ExecutionStepStatus.UNKNOWN);
        ExecutionStep step = executionStepDao.findOne(stepId);
        if (updated == 0) {
            if (step != null && step.getStatus() == ExecutionStepStatus.APPLIED && matchesOwner(step, auth)) {
                return ProgressOperationResult.ok(step);
            }
            return staleOrInvalid(step, auth, "markApplied");
        }
        bumpSnapshot(step.getExecutionId());
        return ProgressOperationResult.ok(step);
    }

    /**
     * 标记 VERIFIED（ADR-001 不变量 5：不可由普通更新回退）。仅 APPLIED 且 owner 匹配时推进；
     * 已 VERIFIED 视为幂等成功（非回退）；其余返回 INVALID_STATE 或 STALE_OWNER。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProgressOperationResult<ExecutionStep> markVerified(String stepId, WriteAuthorization auth) {
        int updated = executionStepDao.markVerified(stepId, auth.getRunId(), auth.getClaimToken(),
                ExecutionStepStatus.VERIFIED, ExecutionStepStatus.APPLIED);
        ExecutionStep step = executionStepDao.findOne(stepId);
        if (updated == 0) {
            if (step != null && step.getStatus() == ExecutionStepStatus.VERIFIED) {
                return ProgressOperationResult.ok(step);
            }
            return staleOrInvalid(step, auth, "markVerified");
        }
        bumpSnapshot(step.getExecutionId());
        return ProgressOperationResult.ok(step);
    }

    /**
     * 标记 UNKNOWN（IN_PROGRESS→UNKNOWN）：结果不确定，需对账后再续作（ADR-001 §2 reconcile 入口命令）。
     * 已 UNKNOWN 且 owner 匹配视为幂等成功。完整补偿编排属 EXE-007。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProgressOperationResult<ExecutionStep> markUnknown(String stepId, WriteAuthorization auth) {
        int updated = executionStepDao.markUnknown(stepId, auth.getRunId(), auth.getClaimToken(),
                ExecutionStepStatus.UNKNOWN, ExecutionStepStatus.IN_PROGRESS);
        ExecutionStep step = executionStepDao.findOne(stepId);
        if (updated == 0) {
            if (step != null && step.getStatus() == ExecutionStepStatus.UNKNOWN && matchesOwner(step, auth)) {
                return ProgressOperationResult.ok(step);
            }
            return staleOrInvalid(step, auth, "markUnknown");
        }
        bumpSnapshot(step.getExecutionId());
        return ProgressOperationResult.ok(step);
    }

    // ============================ Checkpoint & Observation ============================

    /**
     * 追加 checkpoint journal（ADR-001 §4/§7）。同一事务内：分配单调 sequence_no、INSERT journal、
     * 更新 step.latest_checkpoint_id，任一失败（含 step CAS 返回 0）整体回滚——关键 checkpoint 原子性（不变量 6）。
     *
     * @param stepId 可空（PLAN 等执行级 checkpoint 无 step）
     */
    @Transactional(rollbackFor = Exception.class)
    public ProgressOperationResult<ExecutionCheckpoint> appendCheckpoint(String stepId, String executionId,
                                                                         WriteAuthorization auth,
                                                                         ExecutionCheckpointType checkpointType,
                                                                         String payload, String evidenceDigest,
                                                                         String gitHead, String parentGitHead) {
        long previousSeq = executionCheckpointDao.findTopByExecutionIdOrderBySequenceNoDesc(executionId)
                .map(ExecutionCheckpoint::getSequenceNo)
                .orElse(-1L);
        ExecutionCheckpoint checkpoint = new ExecutionCheckpoint();
        checkpoint.setExecutionId(executionId);
        checkpoint.setStepId(stepId);
        checkpoint.setRunId(auth.getRunId());
        checkpoint.setSequenceNo(previousSeq + 1);
        checkpoint.setCheckpointType(checkpointType);
        checkpoint.setClaimToken(auth.getClaimToken());
        checkpoint.setWorkspaceFencingToken(auth.getFencingToken());
        checkpoint.setPayload(payload);
        checkpoint.setEvidenceDigest(evidenceDigest);
        checkpoint.setGitHead(gitHead);
        checkpoint.setParentGitHead(parentGitHead);
        ExecutionCheckpoint saved = executionCheckpointDao.saveAndFlush(checkpoint);

        if (stepId != null) {
            int updated = executionStepDao.updateLatestCheckpoint(stepId, auth.getRunId(), auth.getClaimToken(),
                    saved.getId(), payload);
            if (updated == 0) {
                throw new IllegalStateException(
                        "appendCheckpoint: step latest-checkpoint update rejected (stale owner) -> rollback");
            }
        }
        bumpSnapshot(executionId);
        return ProgressOperationResult.ok(saved);
    }

    /**
     * 追加 observation（ADR-001 §4/§9）。Run 内单调 sequence_no，只允许 INSERT。
     * observation 不能直接改 step/effect 状态；状态变化须经受控命令并另行追加 checkpoint。
     *
     * @param executionId 可空；非空时同事务递增 snapshotVersion
     */
    @Transactional(rollbackFor = Exception.class)
    public RunObservation appendObservation(String runId, RunObservationType observationType,
                                            VerificationStatus verificationStatus, ObservationSourceType sourceType,
                                            String sourceId, String summary, String detail,
                                            String stepId, String checkpointId, String evidenceData,
                                            String executionId) {
        long previousSeq = runObservationDao.findTopByRunIdOrderBySequenceNoDesc(runId)
                .map(RunObservation::getSequenceNo)
                .orElse(-1L);
        RunObservation observation = new RunObservation();
        observation.setRunId(runId);
        observation.setSequenceNo(previousSeq + 1);
        observation.setObservationType(observationType);
        observation.setVerificationStatus(verificationStatus);
        observation.setSourceType(sourceType);
        observation.setSourceId(sourceId);
        observation.setSummary(summary);
        observation.setDetail(detail);
        observation.setStepId(stepId);
        observation.setCheckpointId(checkpointId);
        observation.setEvidenceData(evidenceData);
        observation.setObservedAt(new Date());
        RunObservation saved = runObservationDao.saveAndFlush(observation);
        if (executionId != null) {
            bumpSnapshot(executionId);
        }
        return saved;
    }

    // ============================ Snapshot ============================

    /**
     * 生成 Execution 进度聚合快照（ADR-001 §11 / EXE-002）：workspace 状态 + 必填步骤汇总 + 当前步骤 + nextAction。
     */
    @Transactional(readOnly = true)
    public ExecutionProgressSnapshot generateSnapshot(String executionId) {
        TaskExecution execution = taskExecutionDao.findOne(executionId);
        if (execution == null) {
            return null;
        }
        RequirementWorkspace workspace = requirementWorkspaceDao.findOne(execution.getRequirementWorkspaceId());
        List<ExecutionStep> steps = executionStepDao.findByExecutionIdAndPlanVersion(
                executionId, execution.getPlanVersion());
        StepSummary summary = summarize(steps);
        ExecutionStep currentStep = pickCurrentStep(steps);

        ExecutionProgressSnapshot snapshot = new ExecutionProgressSnapshot();
        snapshot.setRequirementId(execution.getRequirementId());
        snapshot.setExecutionId(executionId);
        snapshot.setSnapshotVersion(workspace == null ? null : workspace.getSnapshotVersion());
        snapshot.setWorkspaceBranch(workspace == null ? null : workspace.getBranchName());
        snapshot.setCurrentHead(workspace == null ? null : workspace.getCurrentHead());
        snapshot.setStepSummary(summary);
        snapshot.setCurrentStep(toView(currentStep));
        snapshot.setNextAction(deriveNextAction(currentStep));
        return snapshot;
    }

    // ============================ helpers ============================

    private StepSummary summarize(List<ExecutionStep> steps) {
        StepSummary summary = new StepSummary();
        for (ExecutionStep step : steps) {
            if (!Boolean.TRUE.equals(step.getRequiredStep())) {
                continue;
            }
            summary.setRequired(summary.getRequired() + 1);
            ExecutionStepStatus status = step.getStatus();
            if (status == ExecutionStepStatus.VERIFIED) {
                summary.setVerified(summary.getVerified() + 1);
            } else if (status == ExecutionStepStatus.APPLIED) {
                summary.setApplied(summary.getApplied() + 1);
            } else if (status == ExecutionStepStatus.UNKNOWN) {
                summary.setUnknown(summary.getUnknown() + 1);
            } else if (status == ExecutionStepStatus.BLOCKED) {
                summary.setBlocked(summary.getBlocked() + 1);
            }
        }
        return summary;
    }

    /** 实体→api 视图（api 层 DTO 不得引用 service 层实体）。 */
    private CurrentStepView toView(ExecutionStep step) {
        if (step == null) {
            return null;
        }
        CurrentStepView view = new CurrentStepView();
        view.setStepId(step.getId());
        view.setStepKey(step.getStepKey());
        view.setPhase(step.getPhase());
        view.setStatus(step.getStatus());
        return view;
    }

    private ExecutionStep pickCurrentStep(List<ExecutionStep> steps) {
        ExecutionStep current = null;
        for (ExecutionStep step : steps) {
            if (!Boolean.TRUE.equals(step.getRequiredStep())) {
                continue;
            }
            if (step.getStatus() != ExecutionStepStatus.VERIFIED) {
                current = step;
                break;
            }
        }
        return current;
    }

    private String deriveNextAction(ExecutionStep current) {
        if (current == null) {
            return "all required steps verified";
        }
        ExecutionStepStatus status = current.getStatus();
        if (status == ExecutionStepStatus.IN_PROGRESS) {
            return "complete:" + current.getStepKey();
        }
        if (status == ExecutionStepStatus.APPLIED) {
            return "verify:" + current.getStepKey();
        }
        return "claim:" + current.getStepKey();
    }

    /** 区分 stale owner（token 不匹配）与 invalid state（token 匹配但状态非法）。 */
    private ProgressOperationResult<ExecutionStep> staleOrInvalid(ExecutionStep step, WriteAuthorization auth,
                                                                  String operation) {
        if (step == null) {
            return ProgressOperationResult.notFound("step not found during " + operation);
        }
        if (!matchesOwner(step, auth)) {
            return ProgressOperationResult.staleOwner(operation + " rejected: stale owner");
        }
        return ProgressOperationResult.invalidState(
                operation + " rejected: current status=" + step.getStatus());
    }

    private static boolean matchesOwner(ExecutionStep step, WriteAuthorization auth) {
        return Objects.equals(step.getOwnerRunId(), auth.getRunId())
                && Objects.equals(step.getClaimToken(), auth.getClaimToken());
    }

    /** 递增 requirement snapshotVersion（ADR-001 §10.6）：经 Execution 反查 workspace。 */
    private void bumpSnapshot(String executionId) {
        TaskExecution execution = taskExecutionDao.findOne(executionId);
        if (execution == null) {
            return;
        }
        requirementWorkspaceDao.incrementSnapshotVersion(execution.getRequirementWorkspaceId());
    }

    private TaskExecution buildExecution(String executionKey, TaskExecutionType taskType, String businessTaskId,
                                         String codingTaskId, String requirementId, String loopId,
                                         String inputHash, Integer planVersion, String requirementWorkspaceId,
                                         String baseCommit) {
        TaskExecution execution = new TaskExecution();
        execution.setExecutionKey(executionKey);
        execution.setTaskType(taskType);
        execution.setBusinessTaskId(businessTaskId);
        execution.setCodingTaskId(codingTaskId);
        execution.setRequirementId(requirementId);
        execution.setLoopId(loopId);
        execution.setInputHash(inputHash);
        execution.setPlanVersion(planVersion);
        execution.setStatus(TaskExecutionStatus.PENDING);
        execution.setRequirementWorkspaceId(requirementWorkspaceId);
        execution.setBaseCommit(baseCommit);
        return execution;
    }

    private ExecutionStep buildStep(String executionId, String stepKey, ExecutionPhase phase, Integer planVersion,
                                    String title, String description, String inputHash, boolean requiredStep) {
        ExecutionStep step = new ExecutionStep();
        step.setExecutionId(executionId);
        step.setStepKey(stepKey);
        step.setPhase(phase);
        step.setPlanVersion(planVersion);
        step.setTitle(title);
        step.setDescription(description);
        step.setInputHash(inputHash);
        step.setRequiredStep(requiredStep);
        step.setStatus(ExecutionStepStatus.PENDING);
        return step;
    }
}
