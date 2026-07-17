package com.changhong.onlinecode.dto.progress;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Execution 进度聚合快照（ADR-001 §11 / EXE-002 内部生成）。
 *
 * <p>EXE-003 在此基础上冻结 HTTP overview DTO；本类为服务层权威内部快照。</p>
 */
@Data
@Schema(description = "Execution 进度聚合快照")
public class ExecutionProgressSnapshot {

    @Schema(description = "需求 ID")
    private String requirementId;

    @Schema(description = "Execution ID")
    private String executionId;

    @Schema(description = "workspace 快照版本（单调递增）")
    private Long snapshotVersion;

    @Schema(description = "workspace 分支名")
    private String workspaceBranch;

    @Schema(description = "workspace 当前 HEAD")
    private String currentHead;

    @Schema(description = "必填步骤汇总")
    private StepSummary stepSummary;

    @Schema(description = "当前待处理步骤（首个非 VERIFIED 必填步骤）")
    private CurrentStepView currentStep;

    @Schema(description = "下一步建议动作")
    private String nextAction;
}
