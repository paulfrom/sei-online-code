package com.changhong.onlinecode.entity.converter;

import com.changhong.onlinecode.dto.spec.SpecEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * {@code List<SpecEntity>} ↔ JSON 转换器。
 *
 * @author sei-online-code
 */
@Converter
public class SpecEntityListConverter extends AbstractJsonListConverter<SpecEntity> {

    @Override
    protected TypeReference<List<SpecEntity>> typeReference() {
        return new TypeReference<>() {
        };
    }
}
