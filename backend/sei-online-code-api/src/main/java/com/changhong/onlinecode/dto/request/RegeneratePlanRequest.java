package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 重新生成规划书请求体。契约 §2.5 / 端点 P4：POST /plan/{projectId}/regenerate。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "重新生成规划书请求")
public class RegeneratePlanRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "上次重生时的修改提示", example = "增加导出功能")
    private String modifyHint;
}
