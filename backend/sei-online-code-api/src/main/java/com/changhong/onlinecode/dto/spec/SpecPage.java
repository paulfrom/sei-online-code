package com.changhong.onlinecode.dto.spec;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * Spec 页面定义。契约 §2.2 pages[]。
 *
 * @author sei-online-code
 */
@Data
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
}