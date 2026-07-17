package com.changhong.onlinecode.dto.progress;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 进度写操作授权上下文（ADR-001 §10.4）。
 *
 * <p>所有改变 step/effect/workspace 的写命令必须携带，并在事务中重新校验 runId、claimToken、
 * fencingToken、loop 与 planVersion；任一不匹配返回稳定 STALE_OWNER，不产生部分写入。</p>
 */
@Data
@Schema(description = "进度写操作授权上下文")
public class WriteAuthorization {

    @Schema(description = "当前写 owner Run ID")
    private String runId;

    @Schema(description = "步骤 claim token，每次 claim 重新生成")
    private String claimToken;

    @Schema(description = "workspace fencing token，接管时递增")
    private Long fencingToken;

    @Schema(description = "loop ID")
    private String loopId;

    @Schema(description = "计划版本")
    private Integer planVersion;
}
