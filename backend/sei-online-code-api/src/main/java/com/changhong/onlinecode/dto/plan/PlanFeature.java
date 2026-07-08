package com.changhong.onlinecode.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * PlanFeature DTO。契约 §2.2 —— 规划书内的单个功能项。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "规划功能项")
public class PlanFeature implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "智能体分配的功能 id", example = "FEAT001")
    private String featureId;

    @Schema(description = "功能标题", example = "库存列表页")
    private String title;

    @Schema(description = "功能概要（供功能设计智能体展开）", example = "展示库存列表，支持搜索和筛选")
    private String outline;
}