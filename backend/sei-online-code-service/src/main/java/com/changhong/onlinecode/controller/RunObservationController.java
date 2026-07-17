package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.RunObservationApi;
import com.changhong.onlinecode.dto.enums.ObservationSourceType;
import com.changhong.onlinecode.dto.enums.RunObservationType;
import com.changhong.onlinecode.dto.enums.VerificationStatus;
import com.changhong.onlinecode.dto.progress.AppendManualRunObservationRequest;
import com.changhong.onlinecode.dto.progress.RunObservationDto;
import com.changhong.onlinecode.entity.RunObservation;
import com.changhong.onlinecode.service.progress.ExecutionProgressQueryService;
import com.changhong.onlinecode.service.progress.ProgressService;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Run observation 查询与人工追加控制器。实现 {@link RunObservationApi}。
 *
 * <p>人工追加委托 {@link ProgressService#appendObservation}（source=USER, type=MANUAL_REVIEW）。
 * TODO(EXE-003): 接入项目鉴权模型——未授权用户拒绝、sourceId 取当前会话用户。</p>
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "RunObservationApi", description = "Run observation 查询与人工追加")
@RequestMapping(path = RunObservationApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class RunObservationController implements RunObservationApi {

    private final ExecutionProgressQueryService queryService;
    private final ProgressService progressService;
    private final ObjectMapper objectMapper;

    @Override
    public ResultData<PageResult<RunObservationDto>> findByRun(String runId, int page, int rows) {
        return ResultData.success(queryService.findObservationsByRun(runId, page, rows));
    }

    @Override
    public ResultData<RunObservationDto> appendManual(AppendManualRunObservationRequest request) {
        // TODO(EXE-003): 鉴权 + sourceId 取会话用户。
        String evidenceJson = serializeEvidence(request.getEvidence());
        VerificationStatus verificationStatus = request.getVerificationStatus() == null
                ? VerificationStatus.UNVERIFIED : request.getVerificationStatus();
        RunObservation saved = progressService.appendObservation(
                request.getRunId(),
                RunObservationType.MANUAL_REVIEW,
                verificationStatus,
                ObservationSourceType.USER,
                null,
                request.getSummary(),
                request.getDetail(),
                null, null, evidenceJson, null);
        return ResultData.success(toDto(saved));
    }

    private String serializeEvidence(Map<String, Object> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private RunObservationDto toDto(RunObservation observation) {
        RunObservationDto dto = new RunObservationDto();
        dto.setObservationId(observation.getId());
        dto.setRunId(observation.getRunId());
        dto.setSequenceNo(observation.getSequenceNo());
        dto.setObservationType(observation.getObservationType());
        dto.setVerificationStatus(observation.getVerificationStatus());
        dto.setSourceType(observation.getSourceType());
        dto.setSummary(observation.getSummary());
        dto.setObservedAt(observation.getObservedAt());
        return dto;
    }
}
