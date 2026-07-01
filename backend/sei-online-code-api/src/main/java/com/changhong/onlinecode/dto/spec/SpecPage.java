package com.changhong.onlinecode.dto.spec;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * Spec 页面定义。契约 §2.2 pages[]。
 *
 * @author sei-online-code
 */
@Schema(description = "Spec 页面定义")
public class SpecPage implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "页面 key", example = "list")
    private String key;

    @Schema(description = "页面标题", example = "库存列表")
    private String title;

    @Schema(description = "路由", example = "/stock/list")
    private String route;

    @Schema(description = "页面描述")
    private String description;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
