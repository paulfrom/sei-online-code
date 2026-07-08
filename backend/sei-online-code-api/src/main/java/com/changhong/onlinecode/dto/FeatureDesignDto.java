package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.featuredesign.FeatureDesignContent;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * FeatureDesignDto DTO。契约 §2.3 —— 功能设计。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "功能设计 DTO")
public class FeatureDesignDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "所属项目 id")
    private String projectId;

    @Schema(description = "对齐 PlanContent.features[].featureId", example = "FEAT001")
    private String featureId;

    @Schema(description = "版本号", example = "1")
    private Integer version;

    @Schema(description = "设计状态", example = "DRAFT")
    private FeatureDesignStatus status;

    @Schema(description = "构建状态（编码执行生命周期）", example = "IDLE")
    private FeatureDesignBuildStatus buildStatus;

    @Schema(description = "设计内容")
    private FeatureDesignContent content;

    @Schema(description = "上次重生时的修改提示", example = "优化页面布局")
    private String modifyHint;

    @Schema(description = "是否最新版本", example = "true")
    private Boolean isLatest;

    @Schema(description = "创建时间")
    private Date createdDate;

    @Schema(description = "最后编辑时间")
    private Date lastEditedDate;

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