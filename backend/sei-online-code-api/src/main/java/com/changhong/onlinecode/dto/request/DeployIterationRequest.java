package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * 部署迭代请求体。契约 §3 端点 7：POST /api/iteration/deploy。
 *
 * @author sei-online-code
 */
@Schema(description = "部署迭代请求")
public class DeployIterationRequest implements Serializable {

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
