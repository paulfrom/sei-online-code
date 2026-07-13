package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunType;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * Run DTO。契约 Phase 2 §1.2 —— 一次 ClaudeRunner 在 worktree 中的执行。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "Run DTO")
public class RunDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "所属 Task id")
    private String taskId;

    @Schema(description = "所属 CodingTask id")
    private String codingTaskId;

    @Schema(description = "所属 Requirement id")
    private String requirementId;

    @Schema(description = "Run 序号")
    private Integer runNo;

    @Schema(description = "触发来源")
    private TriggerSource triggerSource;

    @Schema(description = "Run 类型")
    private RunType runType;

    @Schema(description = "自动化循环 ID")
    private String loopId;

    @Schema(description = "是否已请求取消")
    private Boolean cancelRequested;

    @Schema(description = "使本 Run 失效的人类评论 ID")
    private String invalidatedByCommentId;

    @Schema(description = "RequirementDesignContext ID")
    private String memoryContextId;

    @Schema(description = "WorkspaceMemory ID")
    private String workspaceMemoryId;

    @Schema(description = "用户提示词")
    private String userPrompt;

    @Schema(description = "失败摘要")
    private String failureSummary;

    @Schema(description = "失败原因")
    private String failureReason;

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
}
