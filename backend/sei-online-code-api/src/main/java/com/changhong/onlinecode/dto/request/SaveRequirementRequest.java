package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/编辑 Requirement 请求。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "保存 Requirement 请求")
public class SaveRequirementRequest {

    @Schema(description = "需求 ID（新建时为空）")
    private String id;

    @NotBlank(message = "项目 ID 不能为空")
    @Schema(description = "所属项目 ID")
    private String projectId;

    @Schema(description = "需求编号；新建时可空，由服务端自动生成")
    private String requirementNo;

    @NotBlank(message = "需求标题不能为空")
    @Schema(description = "需求标题")
    private String title;

    @Schema(description = "需求描述")
    private String description;
}
