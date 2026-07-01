package com.changhong.onlinecode.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * {@code List<String>} ↔ JSON 转换器。用于 Task.fileScope（文件边界声明）以 JSON 列持久化。
 *
 * @author sei-online-code
 */
@Converter
public class StringListConverter extends AbstractJsonListConverter<String> {

    @Override
    protected TypeReference<List<String>> typeReference() {
        return new TypeReference<>() {
        };
    }
}
