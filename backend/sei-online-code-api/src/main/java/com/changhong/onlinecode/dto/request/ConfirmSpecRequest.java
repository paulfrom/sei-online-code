package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 确认 Spec 请求体。契约 §3 端点 6：POST /api/spec/confirm。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "确认 Spec 请求")
public class ConfirmSpecRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "specId 不能为空")
    @Schema(description = "Spec id")
    private String specId;
}
