package com.changhong.onlinecode.entity.converter;

import com.changhong.sei.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.AttributeConverter;
import org.apache.commons.lang3.StringUtils;

/**
 * 单对象 T ↔ JSON 字符串 的通用 JPA 转换基类。
 *
 * <p>与 {@link AbstractJsonListConverter} 对称，用于 Plan/FeatureDesign 的 content
 * （单对象，非列表）。子类提供具体的 {@link TypeReference} 即可。</p>
 *
 * @author sei-online-code
 */
public abstract class AbstractJsonConverter<T> implements AttributeConverter<T, String> {


    protected abstract TypeReference<T> typeReference();

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return JsonUtils.mapper().writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 JSON 列失败", e);
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (StringUtils.isBlank(dbData)) {
            return null;
        }
        try {
            return JsonUtils.mapper().readValue(dbData, typeReference());
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 JSON 列失败", e);
        }
    }
}
