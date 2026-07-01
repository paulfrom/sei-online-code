package com.changhong.onlinecode.dto.spec;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * Spec 组件定义。契约 §2.2 components[]。
 *
 * @author sei-online-code
 */
@Schema(description = "Spec 组件定义")
public class SpecComponent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "组件 key", example = "StockTable")
    private String key;

    @Schema(description = "组件类型", example = "ExtTable")
    private String type;

    @Schema(description = "所属页面 key", example = "list")
    private String page;

    @Schema(description = "组件描述")
    private String description;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
