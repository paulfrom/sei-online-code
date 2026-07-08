package com.changhong.onlinecode.dto.spec;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

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
@Data
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
}