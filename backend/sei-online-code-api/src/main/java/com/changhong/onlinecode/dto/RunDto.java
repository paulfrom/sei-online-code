package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * Run DTO。契约 Phase 2 §1.2 —— 一次 ClaudeRunner 在 worktree 中的执行。
 *
 * @author sei-online-code
 */
@Schema(description = "Run DTO")
public class RunDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "所属 Task id")
    private String taskId;

    @Schema(description = "所属迭代 id")
    private String iterationId;

    @Schema(description = "运行状态", example = "RUNNING")
    private RunState state;

    @Schema(description = "worktree 绝对路径（服务端）",
            example = "/tmp/rapid-app-dev/PRJ0001/wt-TASK0001")
    private String worktreePath;

    @Schema(description = "退出码；终态前为 null")
    private Integer exitCode;

    @Schema(description = "开始时间")
    private Date startedDate;

    @Schema(description = "结束时间；终态前为 null")
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
}
