package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.dto.enums.OverviewDesignStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * OverviewDesign DTO。契约 §2.3。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "概览设计 DTO")
public class OverviewDesignDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "项目 ID")
    private String projectId;

    @Schema(description = "需求 ID")
    private String requirementId;

    @Schema(description = "状态")
    private OverviewDesignStatus status;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "内容（JSON 字符串，modules[].features[]）")
    private String content;

    @Schema(description = "设计上下文 ID")
    private String designContextId;

    @Schema(description = "记忆校验状态")
    private MemoryValidationStatus memoryValidationStatus;

    @Schema(description = "记忆校验结果（JSON 字符串）")
    private String memoryValidationResultJson;

    @Schema(description = "失败摘要")
    private String failureSummary;

    @Schema(description = "创建时间")
    private Date createdDate;

    @Schema(description = "最后编辑时间")
    private Date lastEditedDate;
}