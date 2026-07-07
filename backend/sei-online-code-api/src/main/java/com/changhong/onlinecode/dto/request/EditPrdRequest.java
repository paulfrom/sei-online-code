package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 编辑 PRD 内容请求。
 *
 * @author sei-online-code
 */
@Schema(description = "编辑 PRD 内容请求")
public class EditPrdRequest {

    @Schema(description = "PRD 内容（JSON 字符串）")
    private String prdContent;

    public String getPrdContent() {
        return prdContent;
    }

    public void setPrdContent(String prdContent) {
        this.prdContent = prdContent;
    }
}
