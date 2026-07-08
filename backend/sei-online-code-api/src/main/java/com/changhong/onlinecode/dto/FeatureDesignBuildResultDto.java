package com.changhong.onlinecode.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 功能设计编码执行结果。契约 §5 端点 P12a：POST /featureDesign/{id}/build。
 *
 * <p>仅返 runId，前端据其按 frame.runId 过滤 /ws/run/{iterationId} 日志流。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "功能设计编码执行结果")
public class FeatureDesignBuildResultDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "本次编码运行的 run id")
    private String runId;
}