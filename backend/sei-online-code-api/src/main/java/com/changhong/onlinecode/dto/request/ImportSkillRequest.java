package com.changhong.onlinecode.dto.request;

import com.changhong.onlinecode.dto.enums.SkillSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.io.Serializable;

/**
 * 导入技能请求体。契约 Phase 3 §2 端点 16：POST /api/skill/import。
 *
 * @author sei-online-code
 */
@Schema(description = "导入技能请求")
public class ImportSkillRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "name 不能为空")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,63}$",
            message = "name 必须匹配 ^[a-z0-9][a-z0-9-]{0,63}$")
    @Schema(description = "技能名（唯一，映射目录名）", example = "suid")
    private String name;

    @Schema(description = "技能描述")
    private String description;

    @Schema(description = "导入来源", example = "github:owner/repo/path")
    private String source;

    @NotNull(message = "sourceType 不能为空")
    @Schema(description = "来源类型", example = "GITHUB")
    private SkillSourceType sourceType;

    @Schema(description = "SKILL.md 正文")
    private String content;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public SkillSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SkillSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
