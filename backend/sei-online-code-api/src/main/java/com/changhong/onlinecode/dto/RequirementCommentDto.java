package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * Requirement 评论 DTO。
 */
@Data
@Schema(description = "Requirement 评论 DTO")
public class RequirementCommentDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Requirement ID")
    private String requirementId;

    @Schema(description = "自动化循环 ID")
    private String loopId;

    @Schema(description = "作者类型")
    private RequirementCommentAuthorType authorType;

    @Schema(description = "作者名称")
    private String authorName;

    @Schema(description = "评论类型")
    private RequirementCommentType commentType;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "评论元数据 JSON")
    private String metadataJson;

    @Schema(description = "创建时间")
    private Date createdDate;
}
