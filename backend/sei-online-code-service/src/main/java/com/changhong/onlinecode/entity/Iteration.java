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

import java.util.Date;

/**
 * 迭代实体。契约 §2.3 + Phase 4 §1.1（Build Loop 回合溯源）。
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_iteration", indexes = {
        @Index(name = "idx_iteration_project", columnList = "project_id"),
        @Index(name = "idx_iteration_spec", columnList = "spec_id"),
        @Index(name = "idx_iteration_project_round", columnList = "project_id, round")
})
@Access(AccessType.FIELD)
public class Iteration extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "spec_id", nullable = false, length = 36)
    private String specId;

    @Column(name = "spec_version")
    private Integer specVersion;

    /** 项目内 1 起的 Build Loop 回合序号（Phase 4 §1.1）。 */
    @Column(name = "round")
    private Integer round;

    /** 本回合精炼自的上一回合迭代 id；第 1 回合为 null（Phase 4 §1.1）。 */
    @Column(name = "parent_iteration_id", length = 36)
    private String parentIterationId;

    /** 进入本回合时用户提交的优化诉求；第 1 回合为 null（Phase 4 §1.1）。 */
    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private LifecycleState state;

    @Column(name = "preview_url", length = 500)
    private String previewUrl;

    /** 终态（ACCEPTED/FAILED/CANCELLED）落定时间；未终结为 null（Phase 4 §1.1）。 */
    @Column(name = "finished_date")
    private Date finishedDate;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSpecId() {
        return specId;
    }

    public void setSpecId(String specId) {
        this.specId = specId;
    }

    public Integer getSpecVersion() {
        return specVersion;
    }

    public void setSpecVersion(Integer specVersion) {
        this.specVersion = specVersion;
    }

    public Integer getRound() {
        return round;
    }

    public void setRound(Integer round) {
        this.round = round;
    }

    public String getParentIterationId() {
        return parentIterationId;
    }

    public void setParentIterationId(String parentIterationId) {
        this.parentIterationId = parentIterationId;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public LifecycleState getState() {
        return state;
    }

    public void setState(LifecycleState state) {
        this.state = state;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public Date getFinishedDate() {
        return finishedDate;
    }

    public void setFinishedDate(Date finishedDate) {
        this.finishedDate = finishedDate;
    }

    @Override
    @Transient
    public String getDisplay() {
        return specId + " [" + state + "]";
    }
}
