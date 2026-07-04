package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.skill.SkillConfig;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * Skill DTO。契约 Phase 3 §1.1 —— 可导入、hash 锁定的指令包。
 *
 * <p>{@code computedHash} 由服务端按 §6 recipe 计算并返回，前端不重算（服务端权威）。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "Skill DTO")
public class SkillDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "技能名（唯一，映射 .claude/skills/<name>/）", example = "suid")
    private String name;

    @Schema(description = "技能描述", example = "@ead/suid component library skill")
    private String description;

    @Schema(description = "技能配置（承载来源 origin）")
    private SkillConfig config;

    @Schema(description = "SKILL.md 正文（frontmatter + markdown）")
    private String content;

    @Schema(description = "内容锁（sha256），服务端权威计算", example = "sha256:ab12...")
    private String computedHash;

    @Schema(description = "创建时间")
    private Date createdDate;

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

    public String getComputedHash() {
        return computedHash;
    }

    public void setComputedHash(String computedHash) {
        this.computedHash = computedHash;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }
}
