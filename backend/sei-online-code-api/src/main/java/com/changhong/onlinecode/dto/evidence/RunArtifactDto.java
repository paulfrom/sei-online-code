package com.changhong.onlinecode.dto.evidence;

import com.changhong.onlinecode.dto.enums.EvidenceCompleteness;
import com.changhong.onlinecode.dto.enums.RunArtifactState;
import com.changhong.onlinecode.dto.enums.RunArtifactType;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Run 原始材料元数据")
public class RunArtifactDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    private String runId;
    private RunArtifactType artifactType;
    private RunArtifactState state;
    private EvidenceCompleteness completeness;
    private String contentType;
    private String encoding;
    private Long byteLength;
    private Integer chunkCount;
    private String sha256;
    private Boolean redacted;
    private String metadataJson;
    private Date completedAt;
    private Date expiresAt;
    private Date createdDate;
    private Date lastEditedDate;
}
