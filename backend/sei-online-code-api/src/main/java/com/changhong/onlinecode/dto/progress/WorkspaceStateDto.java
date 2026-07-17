package com.changhong.onlinecode.dto.progress;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * Requirement workspace 状态视图（ADR-001 §11 / 计划 §3 overview.workspace）。
 */
@Data
@Schema(description = "Requirement workspace 状态")
public class WorkspaceStateDto {

    @Schema(description = "feature 分支名")
    private String branch;

    @Schema(description = "当前 HEAD")
    private String currentHead;

    @Schema(description = "当前写 owner Run ID")
    private String ownerRunId;

    @Schema(description = "owner Execution ID")
    private String ownerExecutionId;

    @Schema(description = "lease 过期时间")
    private Date leaseExpiresAt;

    @Schema(description = "是否过期（lease 已失效）")
    private boolean stale;
}
