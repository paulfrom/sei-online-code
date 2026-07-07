package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.SpecState;
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

import java.util.List;

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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public SpecState getState() {
        return state;
    }

    public void setState(SpecState state) {
        this.state = state;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public String getModuleTitle() {
        return moduleTitle;
    }

    public void setModuleTitle(String moduleTitle) {
        this.moduleTitle = moduleTitle;
    }

    public String getModuleSummary() {
        return moduleSummary;
    }

    public void setModuleSummary(String moduleSummary) {
        this.moduleSummary = moduleSummary;
    }

    public List<SpecPage> getPages() {
        return pages;
    }

    public void setPages(List<SpecPage> pages) {
        this.pages = pages;
    }

    public List<SpecComponent> getComponents() {
        return components;
    }

    public void setComponents(List<SpecComponent> components) {
        this.components = components;
    }

    public List<SpecEntity> getEntities() {
        return entities;
    }

    public void setEntities(List<SpecEntity> entities) {
        this.entities = entities;
    }

    public List<SpecApiContract> getApiContract() {
        return apiContract;
    }

    public void setApiContract(List<SpecApiContract> apiContract) {
        this.apiContract = apiContract;
    }

    public String getModifyHint() {
        return modifyHint;
    }

    public void setModifyHint(String modifyHint) {
        this.modifyHint = modifyHint;
    }

    @Override
    @Transient
    public String getDisplay() {
        return projectId + " v" + version;
    }
}
