package com.changhong.onlinecode.dto.progress;

import com.changhong.onlinecode.dto.enums.ObservationSourceType;
import com.changhong.onlinecode.dto.enums.RunObservationType;
import com.changhong.onlinecode.dto.enums.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * RunObservation 查询 DTO（计划 §3 runObservation/findByRun）。detail/evidence_data 需授权才返回。
 */
@Data
@Schema(description = "RunObservation 查询视图")
public class RunObservationDto {

    private String observationId;
    private String runId;
    private Long sequenceNo;
    private RunObservationType observationType;
    private VerificationStatus verificationStatus;
    private ObservationSourceType sourceType;
    private String sourceId;
    private String summary;
    private String stepId;
    private String checkpointId;
    private Date observedAt;
}
