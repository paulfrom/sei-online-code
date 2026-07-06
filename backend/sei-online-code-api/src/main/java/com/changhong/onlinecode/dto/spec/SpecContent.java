package com.changhong.onlinecode.dto.spec;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * Spec 内容聚合 DTO —— Requirement Agent JSON 输出的解析目标。
 *
 * <p>对齐 {@code PlanContent}：作为 {@link com.changhong.onlinecode.service.SpecAgentService}
 * 解析 LLM JSON 的载体，四字段与 {@link com.changhong.onlinecode.entity.Spec} 的 JSON 列一一对应。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "Spec 内容聚合")
public class SpecContent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "页面列表")
    private List<SpecPage> pages;

    @Schema(description = "组件列表")
    private List<SpecComponent> components;

    @Schema(description = "实体列表")
    private List<SpecEntity> entities;

    @Schema(description = "API 契约列表")
    private List<SpecApiContract> apiContract;

    public List<SpecPage> getPages() {
        return pages;
    }

    public void setPages(List<SpecPage> pages) {
        this.pages = pages;
    }

    public List<SpecComponent> getComponents() {
        return components;
    }

    public void setComponents(List<SpecComponent> components) {
        this.components = components;
    }

    public List<SpecEntity> getEntities() {
        return entities;
    }

    public void setEntities(List<SpecEntity> entities) {
        this.entities = entities;
    }

    public List<SpecApiContract> getApiContract() {
        return apiContract;
    }

    public void setApiContract(List<SpecApiContract> apiContract) {
        this.apiContract = apiContract;
    }
}
