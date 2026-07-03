package com.changhong.onlinecode.entity.converter;

import com.changhong.onlinecode.dto.plan.PlanContent;
import com.changhong.onlinecode.dto.plan.PlanFeature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PlanContentConverter 测试。
 *
 * @author sei-online-code
 */
class PlanContentConverterTest {

    private final PlanContentConverter converter = new PlanContentConverter();

    @Test
    void testConvertAndBack() {
        // 构造含 2 个 feature 的 PlanContent
        PlanContent content = new PlanContent();
        content.setSummary("测试项目");
        content.setTechAssumptions(List.of("Spring Boot", "PostgreSQL"));
        content.setNonGoals(List.of("不做支付"));

        PlanFeature feature1 = new PlanFeature();
        feature1.setFeatureId("FEAT001");
        feature1.setTitle("功能1");
        feature1.setOutline("概要1");

        PlanFeature feature2 = new PlanFeature();
        feature2.setFeatureId("FEAT002");
        feature2.setTitle("功能2");
        feature2.setOutline("概要2");

        content.setFeatures(List.of(feature1, feature2));

        // 转换为数据库列
        String dbData = converter.convertToDatabaseColumn(content);
        assertNotNull(dbData);

        // 转换回实体属性
        PlanContent converted = converter.convertToEntityAttribute(dbData);
        assertNotNull(converted);
        assertEquals(content.getSummary(), converted.getSummary());
        assertEquals(content.getTechAssumptions(), converted.getTechAssumptions());
        assertEquals(content.getNonGoals(), converted.getNonGoals());
        assertEquals(2, converted.getFeatures().size());
        assertEquals("FEAT001", converted.getFeatures().get(0).getFeatureId());
        assertEquals("FEAT002", converted.getFeatures().get(1).getFeatureId());
    }
}
