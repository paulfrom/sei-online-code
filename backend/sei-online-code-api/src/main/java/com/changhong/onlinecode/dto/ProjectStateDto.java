package com.changhong.onlinecode.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 项目编码前聚合状态响应。GET /api/project/state?id= → { state }。
 *
 * @author sei-online-code
 */
@Schema(description = "项目编码前聚合状态")
public class ProjectStateDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "编码前聚合状态", example = "READY_TO_BUILD")
    private String state;

    public ProjectStateDto() {
    }

    public ProjectStateDto(String state) {
        this.state = state;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
