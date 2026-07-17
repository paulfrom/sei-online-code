package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TaskExecutionType;
import com.changhong.onlinecode.dto.enums.ObservationSourceType;
import com.changhong.onlinecode.dto.enums.RunObservationType;
import com.changhong.onlinecode.dto.enums.VerificationStatus;
import com.changhong.onlinecode.dto.progress.ExecutionProgressSnapshot;
import com.changhong.onlinecode.dto.progress.StepSummary;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.TaskExecution;
import com.changhong.onlinecode.service.progress.ProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
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

    private final ProgressService progressService;
    private final RunDao runDao;

    public CodingTaskProgressIntegrator(ProgressService progressService, RunDao runDao) {
        this.progressService = progressService;
        this.runDao = runDao;
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
     * 解析 invocation：存在活跃 Run（RUNNING/QUEUED）则复用其 invocationKey（幂等重入返回同一 Run），
     * 否则生成新 UUID（最小可确定唯一 id）。legacy 活跃 Run 无 invocationKey 时补一个。
     */
    private InvocationResolution resolveInvocation(String codingTaskId) {
        List<Run> runs = runDao.findByCodingTaskId(codingTaskId);
        Run active = runs.stream()
                .filter(r -> r.getState() == RunState.RUNNING || r.getState() == RunState.QUEUED)
                .findFirst()
                .orElse(null);
        if (active != null) {
            String invocationKey = active.getInvocationKey() != null
                    ? active.getInvocationKey()
                    : UUID.randomUUID().toString();
            return new InvocationResolution(invocationKey, active.getId());
        }
        return new InvocationResolution(UUID.randomUUID().toString(), null);
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
