package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.UsageStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
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
        @Index(name = "idx_run_iteration", columnList = "iteration_id"),
        @Index(name = "idx_run_task", columnList = "task_id"),
        @Index(name = "idx_run_coding_task", columnList = "coding_task_id")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(name = "failure_summary", columnDefinition = "TEXT")
    private String failureSummary;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "iteration_id", length = 36)
    private String iterationId;

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

    @Override
    @Transient
    public String getDisplay() {
        String id = codingTaskId != null ? codingTaskId : taskId;
        return (id != null ? id : runNo != null ? "run#" + runNo : getId()) + " [" + state + "]";
    }
}
