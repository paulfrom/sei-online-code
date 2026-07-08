package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重跑编码任务请求。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "重跑编码任务请求")
public class RerunCodingTaskRequest {

    @NotBlank(message = "重跑提示词不能为空")
    @Schema(description = "重跑提示词")
    private String rerunPrompt;
}
