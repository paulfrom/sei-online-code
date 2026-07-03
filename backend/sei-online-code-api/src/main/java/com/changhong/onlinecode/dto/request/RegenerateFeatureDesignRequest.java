package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 重新生成功能设计请求体。契约 §2.5 / 端点 P9：POST /featureDesign/{id}/regenerate。
 *
 * @author sei-online-code
 */
@Schema(description = "重新生成功能设计请求")
public class RegenerateFeatureDesignRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "上次重生时的修改提示", example = "增加搜索框")
    private String modifyHint;

    public String getModifyHint() {
        return modifyHint;
    }

    public void setModifyHint(String modifyHint) {
        this.modifyHint = modifyHint;
    }
}
