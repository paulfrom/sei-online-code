package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * 补偿执行日志。
 */
@Entity
@Table(name = "oc_compensation_log", indexes = {
        @Index(name = "idx_comp_log_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_comp_log_created", columnList = "created_date")
})
@Access(AccessType.FIELD)
public class CompensationLog extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 36)
    private String entityId;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 32)
    private TriggerSource triggerSource;

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public TriggerSource getTriggerSource() {
        return triggerSource;
    }

    public void setTriggerSource(TriggerSource triggerSource) {
        this.triggerSource = triggerSource;
    }

    @Override
    @Transient
    public String getDisplay() {
        return entityType + ":" + entityId;
    }
}
