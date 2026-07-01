package com.changhong.onlinecode.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * List&lt;T&gt; ↔ JSON 字符串 的通用 JPA 转换基类。
 *
 * <p>Spec 的 pages/components/entities/apiContract 为自由结构，直接以 JSON 列持久化，
 * 避免为 Phase 1 引入额外子表。子类提供具体的 {@link TypeReference} 即可。</p>
 *
 * @author sei-online-code
 */
public abstract class AbstractJsonListConverter<T> implements AttributeConverter<List<T>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected abstract TypeReference<List<T>> typeReference();

    @Override
    public String convertToDatabaseColumn(List<T> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 JSON 列失败", e);
        }
    }

    @Override
    public List<T> convertToEntityAttribute(String dbData) {
        if (StringUtils.isBlank(dbData)) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, typeReference());
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 JSON 列失败", e);
        }
    }
}
