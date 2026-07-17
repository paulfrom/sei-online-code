package com.changhong.onlinecode.dto.progress;

import com.changhong.onlinecode.dto.enums.ExecutionPhase;
import com.changhong.onlinecode.dto.enums.ExecutionStepStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * ExecutionStep 查询 DTO（计划 §3 findSteps）。claimToken 等敏感字段不返回。
 */
@Data
@Schema(description = "ExecutionStep 查询视图")
public class ExecutionStepDto {

    private String stepId;
    private String executionId;
    private String stepKey;
    private ExecutionPhase phase;
    private Integer planVersion;
    private String title;
    private ExecutionStepStatus status;
    private String ownerRunId;
    private Date leaseExpiresAt;
    private Integer attemptCount;
    private Integer progressPercent;
    private Date startedAt;
    private Date appliedAt;
    private Date completedAt;
}
