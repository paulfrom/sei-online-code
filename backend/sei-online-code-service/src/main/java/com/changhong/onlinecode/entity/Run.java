package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.UsageStatus;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunType;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.enums.VerificationStatus;
import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * Run 实体。契约 Phase 2 §1.2 —— 一次 ClaudeRunner 在 worktree 中对某 Task 的执行。
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_run", indexes = {
        @Index(name = "idx_run_log_stream", columnList = "log_stream_key"),
        @Index(name = "idx_run_task", columnList = "task_id"),
        @Index(name = "idx_run_coding_task", columnList = "coding_task_id")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class Run extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(name = "coding_task_id", length = 36)
    private String codingTaskId;

    @Column(name = "requirement_id", length = 36)
    private String requirementId;

    @Column(name = "run_no")
    private Integer runNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 20)
    private RunType runType = RunType.AGENT;

    @Column(name = "parent_run_id", length = 36)
    private String parentRunId;

    @Column(name = "compensates_run_id", length = 36)
    private String compensatesRunId;

    @Column(name = "attempt_no")
    private Integer attemptNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", length = 32)
    private TriggerSource triggerSource;

    @Column(name = "loop_id", length = 64)
    private String loopId;

    @Column(name = "cancel_requested", nullable = false)
    private Boolean cancelRequested = Boolean.FALSE;

    @Column(name = "invalidated_by_comment_id", length = 36)
    private String invalidatedByCommentId;

    @Column(name = "memory_context_id", length = 36)
    private String memoryContextId;

    @Column(name = "workspace_memory_id", length = 36)
    private String workspaceMemoryId;

    @Column(name = "user_prompt", columnDefinition = "TEXT")
    private String userPrompt;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_reason", length = 32)
    private RunTerminalReason terminalReason;

    /**
     * WS 日志流订阅键（/ws/run/{logStreamKey}）；对 spec/plan/feature-design 等无更细 FK 的 run，兼作 runNo 分组键。
     */
    @Column(name = "log_stream_key", length = 36)
    private String logStreamKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private RunState state;

    @Column(name = "worktree_path", length = 500)
    private String worktreePath;

    @Column(name = "base_commit", length = 64)
    private String baseCommit;

    @Column(name = "agent_id", length = 36)
    private String agentId;

    @Column(name = "agent_name", length = 100)
    private String agentName;

    @Column(name = "cli_tool", length = 32)
    private String cliTool;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "cache_read_tokens")
    private Long cacheReadTokens;

    @Column(name = "cache_write_tokens")
    private Long cacheWriteTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_status", nullable = false, length = 20)
    private UsageStatus usageStatus = UsageStatus.UNAVAILABLE;

    @Column(name = "raw_usage_json", columnDefinition = "TEXT")
    private String rawUsageJson;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "started_date")
    private Date startedDate;

    @Column(name = "finished_date")
    private Date finishedDate;

    // ---- ADR-001 / V8：Execution 绑定、invocation 幂等、thread/turn、heartbeat、恢复点与验证 ----

    /** 关联 TaskExecution；迁移期可空，新 Run 必填（数据模型 §5/§13）。 */
    @Column(name = "execution_id", length = 36)
    private String executionId;

    /** 调度调用幂等键；相同 key 重入返回同一 Run。 */
    @Column(name = "invocation_key", length = 128)
    private String invocationKey;

    @Column(name = "executor_id", length = 100)
    private String executorId;

    /** Codex/session thread。 */
    @Column(name = "thread_id", length = 128)
    private String threadId;

    @Column(name = "turn_id", length = 128)
    private String turnId;

    @Column(name = "heartbeat_at")
    private Date heartbeatAt;

    @Column(name = "observed_plan_version")
    private Integer observedPlanVersion;

    /** 本次恢复点。 */
    @Column(name = "resume_from_checkpoint_id", length = 36)
    private String resumeFromCheckpointId;

    @Column(name = "latest_observation_id", length = 36)
    private String latestObservationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 32)
    private VerificationStatus verificationStatus;

    @Override
    @Transient
    public String getDisplay() {
        String id = codingTaskId != null ? codingTaskId : taskId;
        return (id != null ? id : runNo != null ? "run#" + runNo : getId()) + " [" + state + "]";
    }

    /**
     * Backward-compatible accessor for callers that still use the old run failure-summary name.
     */
    @Transient
    public String getFailureSummary() {
        return summary;
    }

    public void setFailureSummary(String failureSummary) {
        this.summary = failureSummary;
    }
}
