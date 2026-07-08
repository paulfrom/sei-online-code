package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 运行编码任务请求。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "运行编码任务请求")
public class RunCodingTaskRequest {

    @Schema(description = "用户提示词（首次运行可空）")
    private String userPrompt;
}
