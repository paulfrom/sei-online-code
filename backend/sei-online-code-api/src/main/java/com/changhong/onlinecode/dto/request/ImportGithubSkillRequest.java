package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * GitHub 地址导入技能请求。
 *
 * <p>支持仓库根地址、{@code /tree/<ref>/<path>} 和
 * {@code /blob/<ref>/<path>/SKILL.md} 三种 GitHub URL 形式。</p>
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "GitHub 导入技能请求")
public class ImportGithubSkillRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "url 不能为空")
    @Schema(description = "GitHub skill 地址", example = "https://github.com/acme/skills/tree/main/suid")
    private String url;
}
