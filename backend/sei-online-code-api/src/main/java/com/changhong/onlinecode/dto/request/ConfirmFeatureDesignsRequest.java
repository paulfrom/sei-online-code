package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量确认功能设计请求体。契约 §2.5 / 端点 P10：POST /featureDesign/confirm。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "批量确认功能设计请求")
public class ConfirmFeatureDesignsRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "ids 不能为空")
    @Schema(description = "待确认的功能设计 id 列表")
    private List<String> ids;
}
