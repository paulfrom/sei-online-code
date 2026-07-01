package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.LifecycleState;
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
 * 项目实体。契约 §2.1。
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_project", indexes = {
        @Index(name = "idx_project_state", columnList = "state")
})
@Access(AccessType.FIELD)
public class Project extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "design", columnDefinition = "TEXT")
    private String design;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private LifecycleState state;

    @Column(name = "current_spec_id", length = 36)
    private String currentSpecId;

    @Column(name = "current_iteration_id", length = 36)
    private String currentIterationId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesign() {
        return design;
    }

    public void setDesign(String design) {
        this.design = design;
    }

    public LifecycleState getState() {
        return state;
    }

    public void setState(LifecycleState state) {
        this.state = state;
    }

    public String getCurrentSpecId() {
        return currentSpecId;
    }

    public void setCurrentSpecId(String currentSpecId) {
        this.currentSpecId = currentSpecId;
    }

    public String getCurrentIterationId() {
        return currentIterationId;
    }

    public void setCurrentIterationId(String currentIterationId) {
        this.currentIterationId = currentIterationId;
    }

    @Override
    @Transient
    public String getDisplay() {
        return name;
    }
}
