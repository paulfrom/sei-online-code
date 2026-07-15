package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.featuredesign.FeatureDesignContent;
import com.changhong.onlinecode.entity.converter.FeatureDesignContentConverter;
import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * FeatureDesign 实体。契约 §8.2 —— 功能设计（单表多行 + is_latest 版本历史）。
 *
 * <p>每个 feature 一份设计，重生时 version+1 新增行。content 为 FeatureDesignContent JSON（TEXT，
 * 经 {@link FeatureDesignContentConverter}）。build_status 为编码执行生命周期，由
 * FeatureDesignBuildService 维护。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_feature_design", indexes = {
        @Index(name = "uk_fd_proj_feat_ver", columnList = "project_id,feature_id,version", unique = true),
        @Index(name = "idx_fd_project", columnList = "project_id")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class FeatureDesign extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "feature_id", nullable = false, length = 128)
    private String featureId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FeatureDesignStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "build_status", nullable = false, length = 32)
    private FeatureDesignBuildStatus buildStatus = FeatureDesignBuildStatus.IDLE;

    @Convert(converter = FeatureDesignContentConverter.class)
    @Column(name = "content", columnDefinition = "TEXT")
    private FeatureDesignContent content;

    @Column(name = "modify_hint", columnDefinition = "TEXT")
    private String modifyHint;

    @Column(name = "is_latest", nullable = false)
    private Boolean isLatest = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 64)
    private FailureCode failureCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage", length = 32)
    private FailureStage failureStage;

    @Column(name = "failure_summary", columnDefinition = "TEXT")
    private String failureSummary;

    @Column(name = "failure_detail", columnDefinition = "TEXT")
    private String failureDetail;

    @Column(name = "last_failed_at")
    private Date lastFailedAt;

    @Column(name = "last_retry_at")
    private Date lastRetryAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private Date nextRetryAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_trigger_source", length = 32)
    private TriggerSource lastTriggerSource;

    @Override
    @Transient
    public String getDisplay() {
        return featureId;
    }
}
