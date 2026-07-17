package com.changhong.onlinecode.dto.progress;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 必填步骤状态汇总（ADR-001 §11 聚合查询模型）。
 */
@Data
@Schema(description = "必填步骤状态汇总")
public class StepSummary {

    @Schema(description = "必填步骤总数")
    private int required;

    @Schema(description = "已验证（后续 Run 永久跳过）")
    private int verified;

    @Schema(description = "已应用未验证")
    private int applied;

    @Schema(description = "结果不确定，需对账")
    private int unknown;

    @Schema(description = "阻塞")
    private int blocked;
}
