package com.changhong.onlinecode.entity.converter;

import com.changhong.onlinecode.dto.spec.SpecApiContract;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * {@code List<SpecApiContract>} ↔ JSON 转换器。
 *
 * @author sei-online-code
 */
@Converter
public class SpecApiContractListConverter extends AbstractJsonListConverter<SpecApiContract> {

    @Override
    protected TypeReference<List<SpecApiContract>> typeReference() {
        return new TypeReference<>() {
        };
    }
}
