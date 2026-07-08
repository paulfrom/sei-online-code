package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.spec.SpecApiContract;
import com.changhong.onlinecode.dto.spec.SpecComponent;
import com.changhong.onlinecode.dto.spec.SpecEntity;
import com.changhong.onlinecode.dto.spec.SpecPage;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Spec DTO。契约 §2.2 SpecDto —— 可评审、可确认的精炼结构。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "Spec DTO")
public class SpecDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "所属项目 id")
    private String projectId;

    @Schema(description = "版本号，每次增量精炼递增", example = "1")
    private Integer version;

    @Schema(description = "Spec 状态", example = "SPEC_REVIEW")
    private SpecState state;

    @Schema(description = "所属概要设计模块 id", example = "MOD001")
    private String moduleId;

    @Schema(description = "所属概要设计模块标题", example = "库存模块")
    private String moduleTitle;

    @Schema(description = "所属概要设计模块概要")
    private String moduleSummary;

    @Schema(description = "页面列表")
    private List<SpecPage> pages;

    @Schema(description = "组件列表")
    private List<SpecComponent> components;

    @Schema(description = "实体列表")
    private List<SpecEntity> entities;

    @Schema(description = "API 契约列表")
    private List<SpecApiContract> apiContract;

    @Schema(description = "重生/精炼修改提示")
    private String modifyHint;

    @Schema(description = "创建时间")
    private Date createdDate;

    @Schema(description = "失败码")
    private FailureCode failureCode;

    @Schema(description = "失败阶段")
    private FailureStage failureStage;

    @Schema(description = "失败摘要")
    private String failureSummary;

    @Schema(description = "失败详情")
    private String failureDetail;

    @Schema(description = "最近失败时间")
    private Date lastFailedAt;

    @Schema(description = "最近重试时间")
    private Date lastRetryAt;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @Schema(description = "下次可重试时间")
    private Date nextRetryAt;

    @Schema(description = "最近触发来源")
    private TriggerSource lastTriggerSource;
}