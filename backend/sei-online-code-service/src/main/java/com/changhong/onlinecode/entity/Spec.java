package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.spec.SpecApiContract;
import com.changhong.onlinecode.dto.spec.SpecComponent;
import com.changhong.onlinecode.dto.spec.SpecEntity;
import com.changhong.onlinecode.dto.spec.SpecPage;
import com.changhong.onlinecode.entity.converter.SpecApiContractListConverter;
import com.changhong.onlinecode.entity.converter.SpecComponentListConverter;
import com.changhong.onlinecode.entity.converter.SpecEntityListConverter;
import com.changhong.onlinecode.entity.converter.SpecPageListConverter;
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

import java.util.List;
import java.util.Date;

/**
 * Spec 实体。契约 §2.2。
 *
 * <p>pages/components/entities/apiContract 为自由结构，以 JSON 列（TEXT）持久化。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_spec", indexes = {
        @Index(name = "idx_spec_project", columnList = "project_id"),
        @Index(name = "idx_spec_state", columnList = "state")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class Spec extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private SpecState state;

    @Column(name = "module_id", length = 64)
    private String moduleId;

    @Column(name = "module_title", length = 200)
    private String moduleTitle;

    @Column(name = "module_summary", columnDefinition = "TEXT")
    private String moduleSummary;

    @Convert(converter = SpecPageListConverter.class)
    @Column(name = "pages", columnDefinition = "TEXT")
    private List<SpecPage> pages;

    @Convert(converter = SpecComponentListConverter.class)
    @Column(name = "components", columnDefinition = "TEXT")
    private List<SpecComponent> components;

    @Convert(converter = SpecEntityListConverter.class)
    @Column(name = "entities", columnDefinition = "TEXT")
    private List<SpecEntity> entities;

    @Convert(converter = SpecApiContractListConverter.class)
    @Column(name = "api_contract", columnDefinition = "TEXT")
    private List<SpecApiContract> apiContract;

    /** 重生/精炼修改提示（对齐 Plan.modifyHint，仅持久化以备历史追溯） */
    @Column(name = "modify_hint", columnDefinition = "TEXT")
    private String modifyHint;

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
        return projectId + " v" + version;
    }
}
