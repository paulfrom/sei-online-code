package com.changhong.onlinecode.dto.progress;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Requirement 级进度事件（计划 §3 WebSocket /ws/requirement/{requirementId}/progress）。
 *
 * <p>事件只通知刷新（携带 snapshotVersion），不承担权威状态；前端收到更高版本后重新查询 overview。</p>
 */
@Data
@Schema(description = "Requirement 进度刷新事件")
public class RequirementProgressEvent {

    @Schema(description = "事件类型，如 step.updated")
    private String eventType;

    @Schema(description = "需求 ID")
    private String requirementId;

    @Schema(description = "触发实体 ID")
    private String entityId;

    @Schema(description = "最新快照版本")
    private Long snapshotVersion;

    @Schema(description = "发生时间")
    private java.util.Date occurredAt;
}
