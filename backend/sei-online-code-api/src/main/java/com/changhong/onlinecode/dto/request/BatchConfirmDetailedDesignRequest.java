package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 批量确认详细设计请求。
 *
 * @author sei-online-code
 */
@Schema(description = "批量确认详细设计请求")
public class BatchConfirmDetailedDesignRequest {

    @NotEmpty(message = "ID 列表不能为空")
    @Schema(description = "详细设计 ID 列表")
    private List<String> ids;

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }
}
