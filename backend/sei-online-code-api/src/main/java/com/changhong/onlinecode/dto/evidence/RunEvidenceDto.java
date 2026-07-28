package com.changhong.onlinecode.dto.evidence;

import com.changhong.onlinecode.dto.enums.EvidenceCompleteness;
import com.changhong.onlinecode.dto.enums.RunEvidenceStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Run 的版本化结构化证据包")
public class RunEvidenceDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    private String runId;
    private Integer evidenceVersion;
    private RunEvidenceStatus status;
    private EvidenceCompleteness completeness;
    private Integer processExitCode;
    private String gitBaseCommit;
    private String gitHeadCommit;
    private String summary;
    private String commandResultsJson;
    private String acceptanceCriteriaJson;
    private String findingsJson;
    private Date capturedAt;
    private List<RunArtifactDto> artifacts = new ArrayList<>();
}
