package com.changhong.onlinecode.dto;

import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 项目 DTO。契约 §2.1 ProjectDto。
 *
 * @author sei-online-code
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "项目 DTO")
public class ProjectDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称长度不能超过200")
    @Schema(description = "项目名称", example = "库存管理台")
    private String name;

    @NotBlank(message = "Project Design 不能为空")
    @Schema(description = "用户输入的原始 Project Design 文字")
    private String design;

    @Schema(description = "编码前聚合状态", example = "READY_TO_BUILD")
    private String state;

    @Schema(description = "当前已确认/活动的 Spec id")
    private String currentSpecId;

    @Schema(description = "项目 Git 地址")
    private String gitUrl;

    @Schema(description = "项目编码/模板占位 projectCode；为空则按 gitUrl/name 推导")
    private String projectCode;

    @Schema(description = "项目版本；为空则默认 1.0.0-SNAPSHOT")
    private String projectVersion;

    @Schema(description = "后端包名；mono 模板 backend/ 目录优先使用该值，为空则自动推导")
    private String packageName;

    @Schema(description = "项目工作区路径")
    private String workspacePath;

    @Schema(description = "确认详细设计后是否自动执行编码任务", example = "false")
    private Boolean autoRunCodingTask;

    @Schema(description = "创建时间")
    private Date createdDate;

    @Schema(description = "最后编辑时间")
    private Date lastEditedDate;
}
