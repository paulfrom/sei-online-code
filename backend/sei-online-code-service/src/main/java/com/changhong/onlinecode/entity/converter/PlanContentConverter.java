package com.changhong.onlinecode.entity.converter;

import com.changhong.onlinecode.dto.plan.PlanContent;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

/**
 * {@code PlanContent} ↔ JSON 转换器（单对象）。
 *
 * @author sei-online-code
 */
@Converter
public class PlanContentConverter extends AbstractJsonConverter<PlanContent> {

    @Override
    protected TypeReference<PlanContent> typeReference() {
        return new TypeReference<>() {
        };
    }
}
