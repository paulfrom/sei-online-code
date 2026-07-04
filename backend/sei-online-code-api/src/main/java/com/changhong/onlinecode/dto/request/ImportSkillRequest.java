package com.changhong.onlinecode.dto.request;

import com.changhong.onlinecode.dto.skill.SkillConfig;
import com.changhong.onlinecode.dto.skill.SkillFileDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.io.Serializable;
import java.util.List;

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

    @NotNull(message = "config 不能为空")
    @Schema(description = "技能配置（承载来源 origin）")
    private SkillConfig config;

    @Schema(description = "SKILL.md 正文")
    private String content;

    @Schema(description = "辅助文件列表（可选，缺省空）")
    @Valid
    private List<SkillFileDto> files;

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

    public SkillConfig getConfig() {
        return config;
    }

    public void setConfig(SkillConfig config) {
        this.config = config;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<SkillFileDto> getFiles() {
        return files;
    }

    public void setFiles(List<SkillFileDto> files) {
        this.files = files;
    }
}
