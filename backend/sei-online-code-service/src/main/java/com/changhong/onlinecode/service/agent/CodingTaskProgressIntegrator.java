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
import com.changhong.sei.core.util.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把 CodingTask Run 与 ProgressService 观测账本接线的协调器。
 *
 * <p>职责：
 * <ul>
 *   <li>计算稳定 executionKey（数据模型 §4：taskType + businessTaskId + loopId + planVersion + inputHash 的 SHA-256）；</li>
 *   <li>{@link ProgressService#findOrCreateExecution} 绑定稳定 Execution；</li>
 *   <li>提供只读进度摘要，并记录 Run 的过程证据与终态；</li>
 *   <li>解析 invocation：存在活跃 Run 则复用其 invocationKey（幂等重入），否则生成新 UUID（最小可确定唯一 id）。</li>
 * </ul>
 *
 * <p>本类不创建 Run（Run 构造仍由 CodingTaskExecutionService 负责，保留其 workspace/runNumber 逻辑）；
 * 账本不拥有 CodingTask 的执行步骤，也不决定 Agent 是否启动或任务是否成功。</p>
 *
 * @author sei-online-code
 */
@Service
public class CodingTaskProgressIntegrator {

    /** Git 仓库尚无首个提交时使用的基线占位，与 WorkspaceManager#getCurrentHead 保持一致。 */
    private static final String UNBORN_HEAD = "0000000000000000000000000000000000000000";

    private final ProgressService progressService;
    private final RunDao runDao;

    public CodingTaskProgressIntegrator(ProgressService progressService, RunDao runDao) {
        this.progressService = progressService;
        this.runDao = runDao;
    }

    /**
     * Agent 启动前绑定观测 Execution。绑定结果只用于审计，不参与调度决策。
     *
     * @param codingTaskId           编码任务 ID（业务任务 ID）
     * @param requirementId          需求 ID
     * @param taskType               执行类型
     * @param loopId                 loop ID
     * @param planVersion            计划版本，null 视为 1
     * @param prompt                 本次 prompt（参与 inputHash）
     * @param requirementWorkspaceId 需求工作区 ID
     * @param baseCommit             基线 commit
     * @return preflight 上下文（executionId/executionKey/invocationKey/reusedRunId）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProgressPreflight preflight(String codingTaskId, String requirementId, TaskExecutionType taskType,
                                       String loopId, Integer planVersion, String prompt,
                                       String requirementWorkspaceId, String baseCommit) {
        InvocationResolution invocation = resolveInvocation(codingTaskId);
        if (requirementWorkspaceId == null || loopId == null) {
            // RequirementWorkspace（EXE-005）或 loop 未就绪：延迟 Execution 绑定，仅记录 invocationKey，保持既有调度行为
            return new ProgressPreflight(null, null, invocation.invocationKey(), invocation.reusedRunId());
        }
        int effectivePlanVersion = planVersion == null ? 1 : planVersion;
        String inputHash = sha256(prompt == null ? "" : prompt);
        String executionKey = computeExecutionKey(taskType, codingTaskId, loopId, effectivePlanVersion, inputHash);
        String effectiveBaseCommit = baseCommit == null || baseCommit.isBlank()
                ? UNBORN_HEAD : baseCommit.trim();

        TaskExecution execution = progressService.findOrCreateExecution(executionKey, taskType, codingTaskId,
                null, requirementId, loopId, inputHash, effectivePlanVersion,
                requirementWorkspaceId, effectiveBaseCommit);
        return new ProgressPreflight(execution.getId(), executionKey,
                invocation.invocationKey(), invocation.reusedRunId());
    }

    /**
     * 构造只读账本摘要。摘要仅供 Agent 参考，执行计划与工作区现场始终优先。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public String buildProgressBrief(String executionId) {
        if (executionId == null) {
            return "";
        }
        ExecutionProgressSnapshot snapshot = progressService.generateSnapshot(executionId);
        if (snapshot == null) {
            return "";
        }
        StepSummary summary = snapshot.getStepSummary();
        if ((summary == null || summary.getRequired() == 0) && snapshot.getNextAction() == null) {
            return "";
        }
        StringBuilder brief = new StringBuilder("\n账本历史（仅供参考，不替代执行计划和工作区检查）：\n");
        if (summary != null) {
            brief.append("- 历史步骤：").append(summary.getRequired())
                    .append("，已验证：").append(summary.getVerified())
                    .append("，未知：").append(summary.getUnknown()).append('\n');
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendTerminalObservation(Run run, boolean success, String summary, String failureReason) {
        if (run == null || run.getExecutionId() == null) {
            return;
        }
        progressService.appendObservation(
                run.getId(),
                RunObservationType.TERMINAL,
                success ? VerificationStatus.CONFIRMED : VerificationStatus.CONTRADICTED,
                ObservationSourceType.SYSTEM,
                null,
                success ? summary : failureReason,
                null, null, null, null,
                run.getExecutionId());
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
     * Agent 成功后追加一条包含变更文件的过程证据。记录失败由调用方降级为告警。
     *
     * @return true 表示无需记录或记录成功
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordSuccessfulCodingTaskCompletion(Run run, String summary, List<String> changedFiles) {
        if (run == null || run.getExecutionId() == null) {
            return true;
        }
        progressService.appendObservation(run.getId(), RunObservationType.CHECKPOINT,
                VerificationStatus.CONFIRMED, ObservationSourceType.SYSTEM, null, summary, null,
                null, null, evidencePayload(changedFiles), run.getExecutionId());
        return true;
    }

    private String evidencePayload(List<String> changedFiles) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schemaVersion", 1);
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
    public record ProgressPreflight(String executionId, String executionKey,
                                    String invocationKey, String reusedRunId) {
    }

    private record InvocationResolution(String invocationKey, String reusedRunId) {
    }
}
