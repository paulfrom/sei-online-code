package com.changhong.onlinecode.entity.converter;

import com.changhong.onlinecode.dto.featuredesign.FeatureDesignContent;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

/**
 * {@code FeatureDesignContent} ↔ JSON 转换器（单对象）。
 *
 * @author sei-online-code
 */
@Converter
public class FeatureDesignContentConverter extends AbstractJsonConverter<FeatureDesignContent> {

    @Override
    protected TypeReference<FeatureDesignContent> typeReference() {
        return new TypeReference<>() {
        };
    }
}
