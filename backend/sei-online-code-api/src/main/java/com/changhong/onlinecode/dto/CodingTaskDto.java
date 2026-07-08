package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * CodingTask DTO。契约 §2.5。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "编码任务 DTO")
public class CodingTaskDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "项目 ID")
    private String projectId;

    @Schema(description = "需求 ID")
    private String requirementId;

    @Schema(description = "详细设计 ID")
    private String detailedDesignId;

    @Schema(description = "详细设计版本")
    private Integer detailedDesignVersion;

    @Schema(description = "状态")
    private CodingTaskStatus status;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "文件范围")
    private List<String> fileScope;

    @Schema(description = "失败摘要")
    private String failureSummary;

    @Schema(description = "创建时间")
    private Date createdDate;

    @Schema(description = "最后编辑时间")
    private Date lastEditedDate;
}