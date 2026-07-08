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
import lombok.Data;
import lombok.EqualsAndHashCode;

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
@Data
@EqualsAndHashCode(callSuper = true)
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

    @Column(name = "git_url", length = 500)
    private String gitUrl;

    @Column(name = "workspace_path", length = 500)
    private String workspacePath;

    @Column(name = "auto_run_coding_task", nullable = false)
    private Boolean autoRunCodingTask = Boolean.FALSE;

    @Override
    @Transient
    public String getDisplay() {
        return name;
    }
}
