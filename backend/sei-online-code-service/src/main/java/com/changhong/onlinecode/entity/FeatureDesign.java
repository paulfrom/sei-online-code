package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getFeatureId() {
        return featureId;
    }

    public void setFeatureId(String featureId) {
        this.featureId = featureId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public FeatureDesignStatus getStatus() {
        return status;
    }

    public void setStatus(FeatureDesignStatus status) {
        this.status = status;
    }

    public FeatureDesignBuildStatus getBuildStatus() {
        return buildStatus;
    }

    public void setBuildStatus(FeatureDesignBuildStatus buildStatus) {
        this.buildStatus = buildStatus;
    }

    public FeatureDesignContent getContent() {
        return content;
    }

    public void setContent(FeatureDesignContent content) {
        this.content = content;
    }

    public String getModifyHint() {
        return modifyHint;
    }

    public void setModifyHint(String modifyHint) {
        this.modifyHint = modifyHint;
    }

    public Boolean getIsLatest() {
        return isLatest;
    }

    public void setIsLatest(Boolean isLatest) {
        this.isLatest = isLatest;
    }

    @Override
    @Transient
    public String getDisplay() {
        return featureId;
    }
}
