package com.changhong.onlinecode.dto.revision;

import com.changhong.onlinecode.dto.enums.PlanPatchAction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 计划补丁中的单个任务操作。
 */
@Data
@Schema(description = "计划补丁任务操作")
public class PlanPatchOperation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "新计划内的任务 key")
    private String taskKey;

    @Schema(description = "修订动作")
    private PlanPatchAction action;

    @Schema(description = "被复用或替代的来源任务 ID")
    private String sourceTaskId;

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "任务描述")
    private String description;

    @Schema(description = "任务区域：frontend / backend")
    private String area;

    @Schema(description = "允许修改的文件范围")
    private List<String> fileScope;

    @Schema(description = "依赖任务 key 列表")
    private List<String> dependsOn;

    @Schema(description = "分配的 agent")
    private String assignedAgent;

    @Schema(description = "采取该动作的原因")
    private String reason;
}
