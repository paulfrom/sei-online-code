package com.changhong.onlinecode.dto.revision;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * PM 针对当前 loop 生成的增量计划补丁。
 */
@Data
@Schema(description = "增量计划补丁")
public class PlanPatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "需求 ID")
    private String requirementId;

    @Schema(description = "自动化循环 ID")
    private String loopId;

    @Schema(description = "修订序号")
    private Long revisionSeq;

    @Schema(description = "基础执行计划 ID")
    private String basePlanId;

    @Schema(description = "基础执行计划版本")
    private Integer basePlanVersion;

    @Schema(description = "修订摘要")
    private String summary;

    @Schema(description = "任务级修订操作")
    private List<PlanPatchOperation> operations;
}
