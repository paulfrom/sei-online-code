package com.changhong.onlinecode.dto.skill;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * Skill 配置（JSONB）。契约 Phase 3 §1.1 —— multica 维度 d：来源信息由原 source/source_type
 * 双列收敛为单个 config JSONB 对象。本阶段仅承载 {@code origin}（导入来源串）。
 *
 * <p>{@code origin} 取原 {@code source} 列的语义与值（形如 {@code github:<owner>/<repo>[/path]} /
 * {@code local:<name>} / {@code inline}），来源类型由前缀隐式编码，不再单列枚举。
 * §6 hash recipe 的 source 部分改取 {@code config.origin}（值不变 → hash 不变）。</p>
 *
 * <p>经 {@link com.changhong.onlinecode.entity.converter.SkillConfigConverter} 序列化为
 * oc_skill.config TEXT 列（JSON 串）。Jackson 反序列化需无参构造 + setter。</p>
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "技能配置")
public class SkillConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "导入来源", example = "local:suid")
    private String origin;

    public SkillConfig() {
    }

    public SkillConfig(String origin) {
        this.origin = origin;
    }
}