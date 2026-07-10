package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 兼容旧 refineSpec 端点的请求体。
 *
 * <p>该端点名称保留为 refineSpec，但当前语义已切换为触发 Plan 重生成。</p>
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "兼容旧 refineSpec 端点的请求体")
public class RefineSpecRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "projectId 不能为空")
    @Schema(description = "项目 id")
    private String projectId;
}
