package com.changhong.onlinecode.dto.progress;

import com.changhong.onlinecode.dto.enums.ExecutionPhase;
import com.changhong.onlinecode.dto.enums.ExecutionStepStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 当前步骤轻量视图（api 层不得引用 service 层实体）。
 */
@Data
@Schema(description = "当前步骤视图")
public class CurrentStepView {

    @Schema(description = "步骤 ID")
    private String stepId;

    @Schema(description = "步骤键")
    private String stepKey;

    @Schema(description = "阶段")
    private ExecutionPhase phase;

    @Schema(description = "步骤状态")
    private ExecutionStepStatus status;
}
