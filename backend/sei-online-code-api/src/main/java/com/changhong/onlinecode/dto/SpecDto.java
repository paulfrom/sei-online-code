package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.dto.spec.SpecApiContract;
import com.changhong.onlinecode.dto.spec.SpecComponent;
import com.changhong.onlinecode.dto.spec.SpecEntity;
import com.changhong.onlinecode.dto.spec.SpecPage;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.List;

/**
 * Spec DTO。契约 §2.2 SpecDto —— 可评审、可确认的精炼结构。
 *
 * @author sei-online-code
 */
@Schema(description = "Spec DTO")
public class SpecDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "所属项目 id")
    private String projectId;

    @Schema(description = "版本号，每次增量精炼递增", example = "1")
    private Integer version;

    @Schema(description = "Spec 状态", example = "SPEC_REVIEW")
    private SpecState state;

    @Schema(description = "页面列表")
    private List<SpecPage> pages;

    @Schema(description = "组件列表")
    private List<SpecComponent> components;

    @Schema(description = "实体列表")
    private List<SpecEntity> entities;

    @Schema(description = "API 契约列表")
    private List<SpecApiContract> apiContract;

    @Schema(description = "重生/精炼修改提示")
    private String modifyHint;

    @Schema(description = "创建时间")
    private Date createdDate;

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

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }
}
