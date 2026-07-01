package com.changhong.onlinecode.dto.spec;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * Spec 实体定义。契约 §2.2 entities[]。
 *
 * @author sei-online-code
 */
@Schema(description = "Spec 实体定义")
public class SpecEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "实体 key", example = "Stock")
    private String key;

    @Schema(description = "实体字段列表")
    private List<SpecEntityField> fields;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List<SpecEntityField> getFields() {
        return fields;
    }

    public void setFields(List<SpecEntityField> fields) {
        this.fields = fields;
    }
}
