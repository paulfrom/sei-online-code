package com.changhong.onlinecode.dto.featuredesign;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * FeatureDesignContent DTO。契约 §2.4 —— 功能设计完整内容。
 *
 * <p>design 字段为自由结构 JSON，使用 {@link JsonNode} 类型以支持任意结构。
 *
 * @author sei-online-code
 */
@Schema(description = "功能设计内容")
public class FeatureDesignContent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "对齐 PlanContent.features[].featureId", example = "FEAT001")
    private String featureId;

    @Schema(description = "该功能的用户故事", example = "作为仓库管理员，我想查看库存列表，以便了解当前库存状态")
    private String goal;

    @Schema(description = "页面/组件/交互/数据/接口契约片段（智能体自定粒度）")
    private JsonNode design;

    @Schema(description = "验收点", example = "[\"列表展示所有库存\", \"支持按名称搜索\"]")
    private List<String> acceptance;

    @Schema(description = "编码时触及的文件边界", example = "[\"src/pages/stock/list.tsx\", \"src/mocks/stock.ts\"]")
    private List<String> fileScope;

    public String getFeatureId() {
        return featureId;
    }

    public void setFeatureId(String featureId) {
        this.featureId = featureId;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public JsonNode getDesign() {
        return design;
    }

    public void setDesign(JsonNode design) {
        this.design = design;
    }

    public List<String> getAcceptance() {
        return acceptance;
    }

    public void setAcceptance(List<String> acceptance) {
        this.acceptance = acceptance;
    }

    public List<String> getFileScope() {
        return fileScope;
    }

    public void setFileScope(List<String> fileScope) {
        this.fileScope = fileScope;
    }
}
