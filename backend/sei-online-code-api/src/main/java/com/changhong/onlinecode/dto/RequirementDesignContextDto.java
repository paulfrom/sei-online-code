package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.MemoryRecordStatus;
import com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * RequirementDesignContext DTO。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.3、§14。
 *
 * @author sei-online-code
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "需求设计上下文 DTO")
public class RequirementDesignContextDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "所属项目 id")
    private String projectId;

    @Schema(description = "所属需求 id")
    private String requirementId;

    @Schema(description = "生成时引用的 WorkspaceMemory id")
    private String workspaceMemoryId;

    @Schema(description = "上下文版本号")
    private Integer version;

    @Schema(description = "版本状态", example = "CURRENT")
    private MemoryRecordStatus status;

    @Schema(description = "上下文可用状态", example = "READY")
    private RequirementDesignContextStatus contextStatus;

    @Schema(description = "需求指纹")
    private String requirementFingerprint;

    @Schema(description = "需求相关补扫快照（JSON 字符串）")
    private String requirementRelatedSnapshotJson;

    @Schema(description = "RequirementConflictReport（JSON 字符串）")
    private String requirementConflictReportJson;

    @Schema(description = "DesignBasis 正文（markdown）")
    private String designBasis;

    @Schema(description = "校验结果（JSON 字符串）")
    private String validationResultJson;

    @Schema(description = "源文件指纹集合（JSON 数组字符串）")
    private String sourceFingerprintsJson;

    @Schema(description = "失败摘要")
    private String failureSummary;

    @Schema(description = "失败详情")
    private String failureDetail;

    @Schema(description = "生成时间")
    private Date generatedAt;
}