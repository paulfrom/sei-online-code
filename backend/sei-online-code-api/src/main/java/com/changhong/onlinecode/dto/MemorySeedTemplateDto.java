package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.MemorySeedTemplateSourceType;
import com.changhong.onlinecode.dto.enums.MemorySeedTemplateStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MemorySeedTemplate DTO。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §6.1.0、§8.1。
 *
 * @author sei-online-code
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "平台 seed 记忆模板 DTO")
public class MemorySeedTemplateDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模板编码")
    private String code;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "模板描述")
    private String description;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "模板状态", example = "ACTIVE")
    private MemorySeedTemplateStatus status;

    @Schema(description = "是否全局默认模板", example = "false")
    private Boolean isDefault;

    @Schema(description = "来源类型", example = "BUILTIN")
    private MemorySeedTemplateSourceType sourceType;

    @Schema(description = "project-memory.md 模板正文（markdown）")
    private String projectMemoryTemplate;

    @Schema(description = "memory-rules.md 模板正文（markdown）")
    private String memoryRulesTemplate;

    @Schema(description = "decisions.md 模板正文（markdown）")
    private String decisionsTemplate;

    @Schema(description = "modules.md 模板正文（markdown）")
    private String modulesTemplate;

    @Schema(description = "发布时间")
    private Date publishedAt;

    @Schema(description = "归档时间")
    private Date archivedAt;
}