package com.changhong.onlinecode.dto.progress;

import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Requirement 执行进度聚合 overview（ADR-001 §11 / 计划 §3 findOverview）。
 *
 * <p>前端权威快照：自动化状态与 MR 状态分离；snapshotVersion 单调；staleAfter 指示何时应视为过期。</p>
 */
@Data
@Schema(description = "Requirement 执行进度聚合 overview")
public class RequirementExecutionOverviewDto {

    private String requirementId;

    @Schema(description = "需求自动化状态")
    private RequirementAutomationStatus automationStatus;

    @Schema(description = "MR 状态（OPEN/MERGED/CLOSED），独立于自动化 COMPLETED")
    private String mrStatus;

    private String activeLoopId;
    private Long revisionSeq;
    private Long appliedRevisionSeq;
    private RequirementRevisionState revisionState;
    private String revisionFailureReason;
    private Integer planVersion;
    private Long snapshotVersion;

    private WorkspaceStateDto workspace;
    private StepSummary stepSummary;
    private CurrentStepView currentStep;
    private ExecutionCheckpointDto latestCheckpoint;
    private String nextAction;
    private List<RecentRunDto> recentRuns;

    private Date serverTime;
    private Date updatedAt;

    @Schema(description = "快照建议过期时间（超过应重新查询）")
    private Date staleAfter;
}
