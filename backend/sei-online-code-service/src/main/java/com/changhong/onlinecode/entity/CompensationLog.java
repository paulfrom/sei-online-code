package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.sei.core.entity.BaseAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "oc_compensation_log", indexes = {
        @Index(name = "idx_comp_log_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_comp_log_created", columnList = "created_date")
})
@Access(AccessType.FIELD)
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
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

    @Override
    @Transient
    public String getDisplay() {
        return entityType + ":" + entityId;
    }
}