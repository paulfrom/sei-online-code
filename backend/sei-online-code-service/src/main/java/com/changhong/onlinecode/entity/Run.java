package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.RunState;
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

import java.util.Date;

/**
 * Run 实体。契约 Phase 2 §1.2 —— 一次 ClaudeRunner 在 worktree 中对某 Task 的执行。
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_run", indexes = {
        @Index(name = "idx_run_iteration", columnList = "iteration_id"),
        @Index(name = "idx_run_task", columnList = "task_id")
})
@Access(AccessType.FIELD)
public class Run extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    @Column(name = "iteration_id", nullable = false, length = 36)
    private String iterationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private RunState state;

    @Column(name = "worktree_path", length = 500)
    private String worktreePath;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "started_date")
    private Date startedDate;

    @Column(name = "finished_date")
    private Date finishedDate;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getIterationId() {
        return iterationId;
    }

    public void setIterationId(String iterationId) {
        this.iterationId = iterationId;
    }

    public RunState getState() {
        return state;
    }

    public void setState(RunState state) {
        this.state = state;
    }

    public String getWorktreePath() {
        return worktreePath;
    }

    public void setWorktreePath(String worktreePath) {
        this.worktreePath = worktreePath;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public Date getStartedDate() {
        return startedDate;
    }

    public void setStartedDate(Date startedDate) {
        this.startedDate = startedDate;
    }

    public Date getFinishedDate() {
        return finishedDate;
    }

    public void setFinishedDate(Date finishedDate) {
        this.finishedDate = finishedDate;
    }

    @Override
    @Transient
    public String getDisplay() {
        return taskId + " [" + state + "]";
    }
}
