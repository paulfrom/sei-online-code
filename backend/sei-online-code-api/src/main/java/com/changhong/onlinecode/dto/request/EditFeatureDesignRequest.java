package com.changhong.onlinecode.dto.request;

import com.changhong.onlinecode.dto.featuredesign.FeatureDesignContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 编辑功能设计请求体。契约 §2.5 / 端点 P8：PUT /featureDesign/{id}。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "编辑功能设计请求")
public class EditFeatureDesignRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "content 不能为空")
    @Valid
    @Schema(description = "功能设计内容")
    private FeatureDesignContent content;
}
