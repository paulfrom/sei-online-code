package com.changhong.onlinecode.entity.converter;

import com.changhong.onlinecode.dto.skill.SkillConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

/**
 * {@code SkillConfig} ↔ JSON 转换器（单对象，oc_skill.config 列）。
 *
 * <p>对称于 {@link PlanContentConverter} / {@link FeatureDesignContentConverter}，
 * 复用 {@link AbstractJsonConverter}。</p>
 *
 * @author sei-online-code
 */
@Converter
public class SkillConfigConverter extends AbstractJsonConverter<SkillConfig> {

    @Override
    protected TypeReference<SkillConfig> typeReference() {
        return new TypeReference<>() {
        };
    }
}
