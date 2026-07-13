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

    @Column(name = "project_code", length = 200)
    private String projectCode;

    @Column(name = "project_version", length = 100)
    private String projectVersion;

    @Column(name = "package_name", length = 255)
    private String packageName;

    @Column(name = "workspace_path", length = 500)
    private String workspacePath;

    /** Project-level validation command configuration JSON. */
    @Column(name = "validation_config", columnDefinition = "TEXT")
    private String validationConfig;

    /**
     * 项目绑定的 seed 记忆模板 id。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §9.1。
     *
     * <p>创建项目时若显式选择模板则保存其 id；未选择时解析并保存当时的全局默认模板 id，
     * 避免后续默认模板切换改变本项目缺文件补齐来源。必须指向 ACTIVE 模板；已归档仍可沿用补齐。</p>
     */
    @Column(name = "memory_seed_template_id", length = 36)
    private String memorySeedTemplateId;

    @Override
    @Transient
    public String getDisplay() {
        return name;
    }
}
