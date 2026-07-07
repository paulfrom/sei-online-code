package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 运行编码任务请求。
 *
 * @author sei-online-code
 */
@Schema(description = "运行编码任务请求")
public class RunCodingTaskRequest {

    @Schema(description = "用户提示词（首次运行可空）")
    private String userPrompt;

    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }
}
