package com.changhong.onlinecode.dto.progress;

import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Requirement 级进度事件（计划 §3 WebSocket /ws/requirement/{requirementId}/progress）。
 *
 * <p>事件可携带修订状态供界面即时展示，并触发客户端重新查询权威 overview。
 * {@code snapshotVersion} 仅在执行进度账本事件中保证存在。</p>
 */
@Data
@Schema(description = "Requirement 进度刷新事件")
public class RequirementProgressEvent {

    @Schema(description = "事件类型，如 step.updated")
    private String eventType;

    @Schema(description = "需求 ID")
    private String requirementId;

    @Schema(description = "自动化 loop ID")
    private String loopId;

    @Schema(description = "计划修订序号")
    private Long revisionSeq;

    @Schema(description = "计划修订状态")
    private RequirementRevisionState revisionState;

    @Schema(description = "计划修订失败原因")
    private String revisionFailureReason;

    @Schema(description = "触发实体 ID")
    private String entityId;

    @Schema(description = "最新快照版本")
    private Long snapshotVersion;

    @Schema(description = "发生时间")
    private java.util.Date occurredAt;
}
