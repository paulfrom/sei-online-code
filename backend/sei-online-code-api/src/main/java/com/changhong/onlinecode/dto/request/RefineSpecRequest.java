package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * 生成概要设计请求体。兼容旧端点名：POST /api/project/refineSpec。
 *
 * @author sei-online-code
 */
@Schema(description = "生成概要设计请求")
public class RefineSpecRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "projectId 不能为空")
    @Schema(description = "项目 id")
    private String projectId;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
}
