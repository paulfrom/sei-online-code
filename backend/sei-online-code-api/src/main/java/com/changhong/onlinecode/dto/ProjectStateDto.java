package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.LifecycleState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 项目生命周期轮询响应。契约 §3 端点 9：GET /api/project/state?id= → { state, iterationId }。
 *
 * @author sei-online-code
 */
@Schema(description = "项目生命周期状态")
public class ProjectStateDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "生命周期状态", example = "PREVIEW")
    private LifecycleState state;

    @Schema(description = "当前迭代 id")
    private String iterationId;

    public ProjectStateDto() {
    }

    public ProjectStateDto(LifecycleState state, String iterationId) {
        this.state = state;
        this.iterationId = iterationId;
    }

    public LifecycleState getState() {
        return state;
    }

    public void setState(LifecycleState state) {
        this.state = state;
    }

    public String getIterationId() {
        return iterationId;
    }

    public void setIterationId(String iterationId) {
        this.iterationId = iterationId;
    }
}
