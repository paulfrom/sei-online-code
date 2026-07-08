package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重生成/编辑概览设计请求。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "重生成概览设计请求")
public class RegenerateOverviewRequest {

    @NotBlank(message = "提示词不能为空")
    @Schema(description = "重生成提示词")
    private String prompt;
}
