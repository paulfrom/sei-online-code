package com.changhong.onlinecode.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * PlanContent DTO。契约 §2.2 —— 规划书完整内容。
 *
 * @author sei-online-code
 */
@Schema(description = "规划书内容")
public class PlanContent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "项目目标与范围", example = "构建一个库存管理系统，支持库存查询、入库、出库等功能")
    private String summary;

    @Schema(description = "技术假设", example = "[\"React + TypeScript\", \"@ead/suid\", \"MSW\"]")
    private List<String> techAssumptions;

    @Schema(description = "功能项列表")
    private List<PlanFeature> features;

    @Schema(description = "本期不做项", example = "[\"支付功能\", \"多租户支持\"]")
    private List<String> nonGoals;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getTechAssumptions() {
        return techAssumptions;
    }

    public void setTechAssumptions(List<String> techAssumptions) {
        this.techAssumptions = techAssumptions;
    }

    public List<PlanFeature> getFeatures() {
        return features;
    }

    public void setFeatures(List<PlanFeature> features) {
        this.features = features;
    }

    public List<String> getNonGoals() {
        return nonGoals;
    }

    public void setNonGoals(List<String> nonGoals) {
        this.nonGoals = nonGoals;
    }
}
