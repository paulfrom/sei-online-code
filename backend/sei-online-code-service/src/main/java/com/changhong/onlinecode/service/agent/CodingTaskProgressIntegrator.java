package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.ExecutionCheckpointDao;
import com.changhong.onlinecode.dto.enums.ExecutionCheckpointType;
import com.changhong.onlinecode.dto.enums.ExecutionPhase;
import com.changhong.onlinecode.dto.enums.ExecutionStepStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TaskExecutionType;
import com.changhong.onlinecode.dto.enums.ObservationSourceType;
import com.changhong.onlinecode.dto.enums.RunObservationType;
import com.changhong.onlinecode.dto.enums.VerificationStatus;
import com.changhong.onlinecode.dto.progress.CurrentStepView;
import com.changhong.onlinecode.dto.progress.ExecutionProgressSnapshot;
import com.changhong.onlinecode.dto.progress.ProgressOperationResult;
import com.changhong.onlinecode.dto.progress.StepSummary;
import com.changhong.onlinecode.dto.progress.WriteAuthorization;
import com.changhong.onlinecode.entity.ExecutionStep;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.ExecutionCheckpoint;
import com.changhong.onlinecode.entity.TaskExecution;
import com.changhong.onlinecode.service.progress.ProgressService;
import com.changhong.sei.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把 CodingTask 调度与 ProgressService 账本接线的协调器（EXE-004 batch 1，附加、未接入热路径）。
 *
 * <p>职责：
 * <ul>
 *   <li>计算稳定 executionKey（数据模型 §4：taskType + businessTaskId + loopId + planVersion + inputHash 的 SHA-256）；</li>
 *   <li>{@link ProgressService#findOrCreateExecution} 绑定稳定 Execution；</li>
 *   <li>读取 snapshot 判断是否已全部 VERIFIED（已完成则跳过模型启动，ADR-001 §1）；</li>
 *   <li>解析 invocation：存在活跃 Run 则复用其 invocationKey（幂等重入），否则生成新 UUID（最小可确定唯一 id）。</li>
 * </ul>
 *
 * <p>本类不创建 Run（Run 构造仍由 CodingTaskExecutionService 负责，保留其 workspace/runNumber 逻辑）；
 * 仅产出 {@link ProgressPreflight} 供 batch 2 在 dispatch 入口消费。</p>
 *
 * @author sei-online-code
 */
@Service
@Slf4j
public class CodingTaskProgressIntegrator {

    private static final String IMPLEMENT_STEP_KEY = "implement:coding-task";
    private static final String VERIFY_STEP_KEY = "verify:coding-task";

    private final ProgressService progressService;
    private final RunDao runDao;
    private final ExecutionCheckpointDao executionCheckpointDao;

    public CodingTaskProgressIntegrator(ProgressService progressService, RunDao runDao,
                                        ExecutionCheckpointDao executionCheckpointDao) {
        this.progressService = progressService;
        this.runDao = runDao;
        this.executionCheckpointDao = executionCheckpointDao;
    }

    /** 解析 Execution 最近 checkpoint 作为本次 Run 的恢复点（ADR-001 §4 resumeFromCheckpoint）。 */
    public String resolveResumeCheckpoint(String executionId) {
        if (executionId == null) {
            return null;
        }
        return executionCheckpointDao.findTopByExecutionIdOrderBySequenceNoDesc(executionId)
                .map(ExecutionCheckpoint::getId).orElse(null);
    }

    /**
     * Agent 启动前的进度 preflight（ADR-001 §1：已完成 Execution 不启动模型）。
     *
     * @param codingTaskId           编码任务 ID（业务任务 ID）
     * @param requirementId          需求 ID
     * @param taskType               执行类型
     * @param loopId                 loop ID
     * @param planVersion            计划版本，null 视为 1
     * @param prompt                 本次 prompt（参与 inputHash）
     * @param requirementWorkspaceId 需求工作区 ID
     * @param baseCommit             基线 commit
     * @return preflight 上下文（executionId/snapshot/shouldSkip/invocationKey/reusedRunId）
     */
    public ProgressPreflight preflight(String codingTaskId, String requirementId, TaskExecutionType taskType,
                                       String loopId, Integer planVersion, String prompt,
                                       String requirementWorkspaceId, String baseCommit) {
        InvocationResolution invocation = resolveInvocation(codingTaskId);
        if (requirementWorkspaceId == null || loopId == null) {
            // RequirementWorkspace（EXE-005）或 loop 未就绪：延迟 Execution 绑定，仅记录 invocationKey，保持既有调度行为
            return new ProgressPreflight(null, null, null, false, invocation.invocationKey(), invocation.reusedRunId());
        }
        int effectivePlanVersion = planVersion == null ? 1 : planVersion;
        String inputHash = sha256(prompt == null ? "" : prompt);
        String executionKey = computeExecutionKey(taskType, codingTaskId, loopId, effectivePlanVersion, inputHash);

        TaskExecution execution = progressService.findOrCreateExecution(executionKey, taskType, codingTaskId,
                null, requirementId, loopId, inputHash, effectivePlanVersion, requirementWorkspaceId, baseCommit);
        if (taskType == TaskExecutionType.CODING_TASK) {
            declareFixedCodingTaskSteps(execution.getId(), effectivePlanVersion, inputHash);
        }
        ExecutionProgressSnapshot snapshot = progressService.generateSnapshot(execution.getId());

        boolean shouldSkip = shouldSkipExecution(snapshot);
        if (shouldSkip) {
            log.info("coding-task progress preflight: execution {} all required steps verified, skip model start. taskId={}",
                    execution.getId(), codingTaskId);
        }
        return new ProgressPreflight(execution.getId(), executionKey, snapshot, shouldSkip,
                invocation.invocationKey(), invocation.reusedRunId());
    }

    /**
     * 构造进度简报，注入 Agent brief（ADR-001 §4：把 progress 与 nextActions 注入 brief）。
     * 已完成步骤会被跳过，提示 Agent 勿重复实现。executionId 为空时返回空串（向后兼容）。
     */
    public String buildProgressBrief(String executionId) {
        if (executionId == null) {
            return "";
        }
        ExecutionProgressSnapshot snapshot = progressService.generateSnapshot(executionId);
        if (snapshot == null) {
            return "";
        }
        StringBuilder brief = new StringBuilder("\n执行进度（已完成步骤会被跳过，请勿重复实现）：\n");
        StepSummary summary = snapshot.getStepSummary();
        if (summary != null) {
            brief.append("- 必填步骤：").append(summary.getRequired())
                    .append("，已验证：").append(summary.getVerified())
                    .append("，已应用：").append(summary.getApplied())
                    .append("，未知：").append(summary.getUnknown())
                    .append("，阻塞：").append(summary.getBlocked()).append('\n');
        }
        if (snapshot.getNextAction() != null) {
            brief.append("- 下一步：").append(snapshot.getNextAction()).append('\n');
        }
        return brief.toString();
    }

    /**
     * Run 终态时追加 TERMINAL observation（ADR-001 §4：自动采集 terminal）。
     * 仅记录，不判 Execution 完成（Execution 完成由 ProgressService markVerified 控制）。
     * workspace 未绑定（executionId 为空）时跳过；写入失败不影响 Run 收口（best-effort）。
     */
    public void appendTerminalObservation(Run run, boolean success, String summary, String failureReason) {
        if (run == null || run.getExecutionId() == null) {
            return;
        }
        try {
            progressService.appendObservation(
                    run.getId(),
                    RunObservationType.TERMINAL,
                    success ? VerificationStatus.CONFIRMED : VerificationStatus.CONTRADICTED,
                    ObservationSourceType.SYSTEM,
                    null,
                    success ? summary : failureReason,
                    null, null, null, null,
                    run.getExecutionId());
        } catch (Exception e) {
            log.warn("appendTerminalObservation failed runId={}", run.getId(), e);
        }
    }

    /** Execution 全部必填步骤已 VERIFIED 且无当前步骤 → 跳过模型启动。空 Execution（无步骤）不跳过。 */
    private boolean shouldSkipExecution(ExecutionProgressSnapshot snapshot) {
        if (snapshot == null || snapshot.getStepSummary() == null) {
            return false;
        }
        StepSummary summary = snapshot.getStepSummary();
        return summary.getRequired() > 0
                && summary.getVerified() == summary.getRequired()
                && snapshot.getCurrentStep() == null;
    }

    /**
     * 解析 invocation：当前入口没有携带外部 invocationKey，因此每次新 attempt 使用新的 UUID。
     * 若直接复用活跃 Run 的 invocationKey，新 Run 保存时会命中唯一约束；真正的“同一 invocation 重入返回
     * 首次 Run”应在 API 层显式传入 invocationKey 后实现。
     */
    private InvocationResolution resolveInvocation(String codingTaskId) {
        List<Run> runs = runDao.findByCodingTaskId(codingTaskId);
        Run active = runs.stream()
                .filter(r -> r.getState() == RunState.RUNNING || r.getState() == RunState.QUEUED)
                .findFirst()
                .orElse(null);
        if (active != null) {
            return new InvocationResolution(UUID.randomUUID().toString(), active.getId());
        }
        return new InvocationResolution(UUID.randomUUID().toString(), null);
    }

    /**
     * Phase 2 桥接：在动态步骤协议落地前，为 CodingTask 声明两个稳定固定步骤，保证账本不是空壳。
     */
    private void declareFixedCodingTaskSteps(String executionId, int planVersion, String inputHash) {
        progressService.declareStep(executionId, IMPLEMENT_STEP_KEY, ExecutionPhase.IMPLEMENT, planVersion,
                "执行编码任务", "Agent 在工作区完成本 CodingTask 的文件变更", inputHash, true);
        progressService.declareStep(executionId, VERIFY_STEP_KEY, ExecutionPhase.VERIFY, planVersion,
                "验证编码任务", "确认本 CodingTask 的工作区变更满足执行结果判定", inputHash, true);
    }

    /**
     * Agent 成功后把固定步骤推进到 VERIFIED。动态 step/tool 协议尚未接入前，这让重复 Run 能通过账本跳过。
     *
     * @return true 表示所有可推进步骤均完成；false 表示账本推进失败，应阻断旧式成功收口。
     */
    public boolean recordSuccessfulCodingTaskCompletion(Run run, String summary, List<String> changedFiles) {
        if (run == null || run.getExecutionId() == null) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            ExecutionProgressSnapshot snapshot = progressService.generateSnapshot(run.getExecutionId());
            CurrentStepView current = snapshot == null ? null : snapshot.getCurrentStep();
            if (current == null) {
                return true;
            }
            if (!advanceCurrentStep(run, current, summary, changedFiles)) {
                return false;
            }
        }
        return false;
    }

    private boolean advanceCurrentStep(Run run, CurrentStepView current, String summary, List<String> changedFiles) {
        WriteAuthorization auth = new WriteAuthorization();
        auth.setRunId(run.getId());
        auth.setFencingToken(0L);
        if (current.getStatus() != ExecutionStepStatus.APPLIED) {
            ProgressOperationResult<ExecutionStep> claim = progressService.claimStep(
                    current.getStepId(), auth, new Date(System.currentTimeMillis() + 15 * 60_000L));
            if (!claim.isOk() || claim.getData() == null) {
                log.warn("recordSuccessfulCodingTaskCompletion claim failed runId={}, stepKey={}, status={}",
                        run.getId(), current.getStepKey(), claim.getStatus());
                return false;
            }
            auth.setClaimToken(claim.getData().getClaimToken());
            auth.setFencingToken(claim.getData().getWorkspaceFencingToken());
            ProgressOperationResult<ExecutionStep> applied = progressService.markApplied(current.getStepId(), auth);
            if (!applied.isOk()) {
                log.warn("recordSuccessfulCodingTaskCompletion markApplied failed runId={}, stepKey={}, status={}",
                        run.getId(), current.getStepKey(), applied.getStatus());
                return false;
            }
            appendStepCheckpoint(run, current, auth, ExecutionCheckpointType.APPLIED, summary, changedFiles);
        }
        ProgressOperationResult<ExecutionStep> verified = progressService.markVerified(current.getStepId(), auth);
        if (!verified.isOk()) {
            log.warn("recordSuccessfulCodingTaskCompletion markVerified failed runId={}, stepKey={}, status={}",
                    run.getId(), current.getStepKey(), verified.getStatus());
            return false;
        }
        appendStepCheckpoint(run, current, auth, ExecutionCheckpointType.VERIFIED, summary, changedFiles);
        return true;
    }

    private void appendStepCheckpoint(Run run, CurrentStepView current, WriteAuthorization auth,
                                      ExecutionCheckpointType type, String summary, List<String> changedFiles) {
        progressService.appendCheckpoint(current.getStepId(), run.getExecutionId(), auth, type,
                checkpointPayload(type, summary, changedFiles), null, null, null);
    }

    private String checkpointPayload(ExecutionCheckpointType type, String summary, List<String> changedFiles) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schemaVersion", 1);
            payload.put("checkpointType", type.name());
            payload.put("summary", summary);
            payload.put("changedFiles", changedFiles == null ? List.of() : changedFiles);
            return JsonUtils.mapper().writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"schemaVersion\":1}";
        }
    }

    /** 数据模型 §4：executionKey = sha256(taskType|businessTaskId|loopId|planVersion|inputHash)。 */
    public static String computeExecutionKey(TaskExecutionType taskType, String businessTaskId, String loopId,
                                             int planVersion, String inputHash) {
        return sha256(taskType + "|" + nullSafe(businessTaskId) + "|" + nullSafe(loopId)
                + "|" + planVersion + "|" + nullSafe(inputHash));
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

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

    /** Agent 启动前进度 preflight 结果。 */
    public record ProgressPreflight(String executionId, String executionKey, ExecutionProgressSnapshot snapshot,
                                    boolean shouldSkip, String invocationKey, String reusedRunId) {
    }

    private record InvocationResolution(String invocationKey, String reusedRunId) {
    }
}
