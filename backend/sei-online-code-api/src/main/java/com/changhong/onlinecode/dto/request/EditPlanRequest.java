package com.changhong.onlinecode.dto.request;

import com.changhong.onlinecode.dto.plan.PlanContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 编辑规划书请求体。契约 §2.5 / 端点 P3：PUT /plan/{projectId}。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "编辑规划书请求")
public class EditPlanRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "content 不能为空")
    @Valid
    @Schema(description = "规划内容")
    private PlanContent content;
}
