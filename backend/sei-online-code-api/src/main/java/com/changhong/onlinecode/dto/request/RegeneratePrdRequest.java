package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 重生成 PRD 请求。
 *
 * @author sei-online-code
 */
@Schema(description = "重生成 PRD 请求")
public class RegeneratePrdRequest {

    @NotBlank(message = "提示词不能为空")
    @Schema(description = "重生成提示词")
    private String prompt;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}
