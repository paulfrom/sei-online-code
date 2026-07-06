package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 重新生成 Spec 请求体。
 *
 * @author sei-online-code
 */
@Schema(description = "重新生成 Spec 请求")
public class RegenerateSpecRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "修改提示", example = "增加导出功能")
    private String modifyHint;

    public String getModifyHint() {
        return modifyHint;
    }

    public void setModifyHint(String modifyHint) {
        this.modifyHint = modifyHint;
    }
}
