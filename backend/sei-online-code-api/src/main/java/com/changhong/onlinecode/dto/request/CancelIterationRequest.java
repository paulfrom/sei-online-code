package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * 取消迭代请求体。契约 Phase 4 §2 端点 28：POST /api/iteration/cancel。
 *
 * @author sei-online-code
 */
@Schema(description = "取消迭代请求")
public class CancelIterationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "iterationId 不能为空")
    @Schema(description = "迭代 id")
    private String iterationId;

    public String getIterationId() {
        return iterationId;
    }

    public void setIterationId(String iterationId) {
        this.iterationId = iterationId;
    }
}
