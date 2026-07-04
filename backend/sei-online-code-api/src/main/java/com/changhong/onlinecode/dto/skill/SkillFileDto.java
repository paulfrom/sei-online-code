package com.changhong.onlinecode.dto.skill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.io.Serializable;

/**
 * 技能辅助文件条目。契约 Phase 3 §1.1 —— multica 维度 e：兑现 deferred 的 per-file
 * {@code FileRef[]}。每个条目是 {@code .claude/skills/<name>/} 目录下相对路径的一个文件
 *（如 {@code resources/foo.md}），由 {@link com.changhong.onlinecode.agent.SkillMaterializer}
 * 在 spawn 前随 SKILL.md 一并写入 worktree。
 *
 * <p>{@code path} 为相对路径，允许子目录（含 {@code /}），但禁止绝对路径与 {@code ..} 段
 *（bean-validation {@code @Pattern} 拦截 → 400；materializer 再加 normalize + startsWith 越界 guard）。
 * 辅助文件不进 §6 hash recipe（lock 仍只覆盖 SKILL.md 五元组），详见 {@link
 * com.changhong.onlinecode.entity.Skill}。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "技能辅助文件（相对路径 + 正文）")
public class SkillFileDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "path 不能为空")
    @Pattern(regexp = "^(?!/)(?!.*(?:^|/)\\.\\.(?:/|$)).+$",
            message = "path 必须为相对路径且不含 .. 段")
    @Schema(description = "相对路径（允许子目录，禁止绝对路径与 .. 段）", example = "resources/foo.md")
    private String path;

    @Schema(description = "文件正文")
    private String content;

    public SkillFileDto() {
    }

    public SkillFileDto(String path, String content) {
        this.path = path;
        this.content = content;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
