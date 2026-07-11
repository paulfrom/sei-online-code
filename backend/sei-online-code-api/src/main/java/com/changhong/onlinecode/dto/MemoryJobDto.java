package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.MemoryJobStatus;
import com.changhong.onlinecode.dto.enums.MemoryJobTriggerSource;
import com.changhong.onlinecode.dto.enums.MemoryJobType;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MemoryJob DTO。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.4、§12。
 *
 * @author sei-online-code
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "记忆任务 DTO")
public class MemoryJobDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "所属项目 id")
    private String projectId;

    @Schema(description = "关联需求 id")
    private String requirementId;

    @Schema(description = "关联 CodingTask id")
    private String codingTaskId;

    @Schema(description = "关联执行 run id")
    private String runId;

    @Schema(description = "任务类型", example = "MEMORY_INITIALIZE")
    private MemoryJobType jobType;

    @Schema(description = "任务状态", example = "PENDING")
    private MemoryJobStatus status;

    @Schema(description = "触发来源", example = "PROJECT_WORKSPACE_READY")
    private MemoryJobTriggerSource triggerSource;

    @Schema(description = "前一个 WorkspaceMemory id")
    private String previousWorkspaceMemoryId;

    @Schema(description = "任务产出的新 WorkspaceMemory id")
    private String newWorkspaceMemoryId;

    @Schema(description = "增量回写基准 WorkspaceMemory id")
    private String baseWorkspaceMemoryId;

    @Schema(description = "幂等键")
    private String idempotencyKey;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "已重试次数")
    private Integer retryCount;

    @Schema(description = "最大重试次数")
    private Integer maxRetryCount;

    @Schema(description = "下次重试时间")
    private Date nextRetryAt;

    @Schema(description = "开始执行时间")
    private Date startedAt;

    @Schema(description = "完成时间")
    private Date finishedAt;

    @Schema(description = "失败摘要")
    private String failureSummary;

    @Schema(description = "失败详情")
    private String failureDetail;
}