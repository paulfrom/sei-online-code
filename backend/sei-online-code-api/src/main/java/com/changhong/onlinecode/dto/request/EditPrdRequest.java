package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 编辑 PRD 内容请求。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "编辑 PRD 内容请求")
public class EditPrdRequest {

    @Schema(description = "PRD 内容（JSON 字符串）")
    private String prdContent;
}
