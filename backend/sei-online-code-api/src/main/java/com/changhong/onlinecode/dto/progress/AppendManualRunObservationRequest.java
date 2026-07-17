package com.changhong.onlinecode.dto.progress;

import com.changhong.onlinecode.dto.enums.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 人工 observation 追加请求（计划 §3 runObservation/appendManual）。
 *
 * <p>只能追加 observation，不能直接改 step/effect 状态（ADR-001 §9）。</p>
 */
@Data
@Schema(description = "人工 observation 追加请求")
public class AppendManualRunObservationRequest {

    @Schema(description = "Run ID")
    private String runId;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "详情")
    private String detail;

    @Schema(description = "验证状态")
    private VerificationStatus verificationStatus;

    @Schema(description = "证据（结构化，后端将脱敏存储）")
    private Map<String, Object> evidence;
}
