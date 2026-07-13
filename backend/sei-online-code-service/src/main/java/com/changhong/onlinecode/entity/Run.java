package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunType;
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
public class Run extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(name = "coding_task_id", length = 36)
    private String codingTaskId;

    @Column(name = "run_no")
    private Integer runNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", length = 32)
    private TriggerSource triggerSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", length = 32)
    private RunType runType;

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

    /** CodingTask 开始执行前的 Git HEAD，用作完成后增量记忆采集基准。 */
    @Column(name = "base_commit", length = 64)
    private String baseCommit;

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
