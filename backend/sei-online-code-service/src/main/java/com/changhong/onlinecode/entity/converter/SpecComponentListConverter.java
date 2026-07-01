package com.changhong.onlinecode.entity.converter;

import com.changhong.onlinecode.dto.spec.SpecComponent;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * {@code List<SpecComponent>} ↔ JSON 转换器。
 *
 * @author sei-online-code
 */
@Converter
public class SpecComponentListConverter extends AbstractJsonListConverter<SpecComponent> {

    @Override
    protected TypeReference<List<SpecComponent>> typeReference() {
        return new TypeReference<>() {
        };
    }
}
