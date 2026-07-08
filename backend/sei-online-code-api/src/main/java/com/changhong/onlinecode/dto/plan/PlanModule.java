package com.changhong.onlinecode.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * PlanModule DTO。概要设计中的模块划分。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "概要设计模块")
public class PlanModule implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模块 id", example = "MOD001")
    private String moduleId;

    @Schema(description = "模块标题", example = "库存模块")
    private String title;

    @Schema(description = "模块概要", example = "负责库存查询、入库、出库等能力")
    private String summary;

    @Schema(description = "模块内功能项")
    private List<PlanFeature> features;
}