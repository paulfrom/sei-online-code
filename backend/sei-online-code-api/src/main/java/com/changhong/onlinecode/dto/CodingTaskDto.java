package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * CodingTask DTO。契约 §2.5。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "编码任务 DTO")
public class CodingTaskDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "项目 ID")
    private String projectId;

    @Schema(description = "需求 ID")
    private String requirementId;

    @Schema(description = "状态")
    private CodingTaskStatus status;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "文件范围")
    private List<String> fileScope;

    @Schema(description = "任务区域：frontend / backend")
    private String area;

    @Schema(description = "依赖任务 key 列表")
    private List<String> dependsOn;

    @Schema(description = "执行计划 ID")
    private String executionPlanId;

    @Schema(description = "计划任务 key")
    private String planTaskKey;

    @Schema(description = "分配代理")
    private String assignedAgent;

    @Schema(description = "循环 ID")
    private String loopId;

    @Schema(description = "任务所属修订序号")
    private Long revisionSeq;

    @Schema(description = "被当前任务替代的旧任务 ID")
    private String supersedesTaskId;

    @Schema(description = "任务被保留、修改或替代的原因")
    private String dispositionReason;

    @Schema(description = "失败摘要")
    private String failureSummary;

    @Schema(description = "创建时间")
    private Date createdDate;

    @Schema(description = "最后编辑时间")
    private Date lastEditedDate;
}
