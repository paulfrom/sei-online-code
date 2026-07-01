package com.changhong.onlinecode.entity.converter;

import com.changhong.onlinecode.dto.spec.SpecPage;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * {@code List<SpecPage>} ↔ JSON 转换器。
 *
 * @author sei-online-code
 */
@Converter
public class SpecPageListConverter extends AbstractJsonListConverter<SpecPage> {

    @Override
    protected TypeReference<List<SpecPage>> typeReference() {
        return new TypeReference<>() {
        };
    }
}
