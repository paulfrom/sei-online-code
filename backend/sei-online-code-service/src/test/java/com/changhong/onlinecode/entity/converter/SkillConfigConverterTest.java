package com.changhong.onlinecode.entity.converter;

import com.changhong.onlinecode.dto.skill.SkillConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SkillConfigConverter 测试。
 *
 * <p>WHY：oc_skill.config 列承载来源信息（multica 维度 d），converter 必须保证 origin 字段
 * 经 JSON 序列化/反序列化往返不丢失——否则导入的来源在重新加载后会变 null，hash 复现也会失配。</p>
 *
 * @author sei-online-code
 */
class SkillConfigConverterTest {

    private final SkillConfigConverter converter = new SkillConfigConverter();

    @Test
    void convertToDatabaseColumn_null_returnsNull() {
        // WHY：未设置 config 的旧数据/空值不应序列化成字符串 "null" 污染列。
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void convertToEntityAttribute_blank_returnsNull() {
        // WHY：DB 中空串/空白列反序列化应得 null，而非抛 IllegalStateException 中断加载。
        assertNull(converter.convertToEntityAttribute(""));
        assertNull(converter.convertToEntityAttribute("   "));
    }

    @Test
    void roundTrip_preservesOrigin() {
        // WHY：origin 是 §6 hash 的输入之一，往返必须逐字保留，否则重算 hash 与磁盘 .lock 失配。
        SkillConfig config = new SkillConfig("local:suid");

        String dbData = converter.convertToDatabaseColumn(config);
        assertNotNull(dbData);

        SkillConfig converted = converter.convertToEntityAttribute(dbData);
        assertNotNull(converted);
        assertEquals("local:suid", converted.getOrigin());
    }
}
