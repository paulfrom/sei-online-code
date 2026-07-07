package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 编辑详细设计内容请求。
 *
 * @author sei-online-code
 */
@Schema(description = "编辑详细设计内容请求")
public class EditDetailedDesignRequest {

    @Schema(description = "详细设计内容（JSON 字符串）")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
