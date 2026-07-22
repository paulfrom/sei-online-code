package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.ExecutionPlanStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * PM 执行计划 DTO。
 */
@Data
@Schema(description = "PM 执行计划 DTO")
public class ExecutionPlanDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Requirement ID")
    private String requirementId;

    @Schema(description = "自动化循环 ID")
    private String loopId;

    @Schema(description = "计划版本")
    private Integer version;

    @Schema(description = "计划类型")
    private ExecutionPlanType planType;

    @Schema(description = "计划状态")
    private ExecutionPlanStatus status;

    @Schema(description = "结构化计划 JSON")
    private String planJson;

    @Schema(description = "计划摘要")
    private String summary;

    @Schema(description = "创建计划的 agent")
    private String createdByAgent;

    @Schema(description = "RequirementDesignContext ID")
    private String memoryContextId;

    @Schema(description = "WorkspaceMemory ID")
    private String workspaceMemoryId;

    @Schema(description = "基础执行计划 ID")
    private String basePlanId;

    @Schema(description = "触发修订的评论 ID")
    private String triggerCommentId;

    @Schema(description = "修订序号")
    private Long revisionSeq;

    @Schema(description = "任务级变更集 JSON")
    private String changeSetJson;

    @Schema(description = "创建时间")
    private Date createdDate;
}
