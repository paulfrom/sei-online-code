package com.changhong.onlinecode.dto.spec;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * Spec API 契约条目。契约 §2.2 apiContract[]。
 *
 * @author sei-online-code
 */
@Schema(description = "Spec API 契约条目")
public class SpecApiContract implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "HTTP 方法", example = "POST")
    private String method;

    @Schema(description = "请求路径", example = "/api/stock/findByPage")
    private String path;

    @Schema(description = "请求体形状", example = "Search")
    private String requestShape;

    @Schema(description = "响应体形状", example = "ResultData<PageResult<StockDto>>")
    private String responseShape;

    @Schema(description = "契约描述")
    private String description;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRequestShape() {
        return requestShape;
    }

    public void setRequestShape(String requestShape) {
        this.requestShape = requestShape;
    }

    public String getResponseShape() {
        return responseShape;
    }

    public void setResponseShape(String responseShape) {
        this.responseShape = responseShape;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
