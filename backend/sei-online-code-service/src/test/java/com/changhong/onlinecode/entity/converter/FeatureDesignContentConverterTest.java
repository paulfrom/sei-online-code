package com.changhong.onlinecode.entity.converter;

import com.changhong.onlinecode.dto.featuredesign.FeatureDesignContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * FeatureDesignContentConverter 测试。
 *
 * @author sei-online-code
 */
class FeatureDesignContentConverterTest {

    private final FeatureDesignContentConverter converter = new FeatureDesignContentConverter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testConvertAndBack() throws Exception {
        // 构造 FeatureDesignContent
        FeatureDesignContent content = new FeatureDesignContent();
        content.setFeatureId("FEAT001");
        content.setGoal("作为用户，我想测试功能");
        content.setAcceptance(List.of("验收点1", "验收点2"));
        content.setFileScope(List.of("src/file1.tsx", "src/file2.tsx"));

        // 构造 design JsonNode
        String designJson = "{\"pages\":[{\"name\":\"TestPage\",\"components\":[]}]}";
        JsonNode designNode = objectMapper.readTree(designJson);
        content.setDesign(designNode);

        // 转换为数据库列
        String dbData = converter.convertToDatabaseColumn(content);
        assertNotNull(dbData);

        // 转换回实体属性
        FeatureDesignContent converted = converter.convertToEntityAttribute(dbData);
        assertNotNull(converted);
        assertEquals(content.getFeatureId(), converted.getFeatureId());
        assertEquals(content.getGoal(), converted.getGoal());
        assertEquals(content.getAcceptance(), converted.getAcceptance());
        assertEquals(content.getFileScope(), converted.getFileScope());
        assertNotNull(converted.getDesign());
        assertEquals("TestPage", converted.getDesign().get("pages").get(0).get("name").asText());
    }
}
