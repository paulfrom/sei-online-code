package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.ExecutionPlanStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
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
 * PM 执行计划。
 */
@Entity
@Table(name = "oc_execution_plan", indexes = {
        @Index(name = "idx_execution_plan_requirement", columnList = "requirement_id"),
        @Index(name = "idx_execution_plan_loop", columnList = "loop_id"),
        @Index(name = "idx_execution_plan_status", columnList = "status")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionPlan extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "requirement_id", nullable = false, length = 36)
    private String requirementId;

    @Column(name = "loop_id", nullable = false, length = 64)
    private String loopId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 32)
    private ExecutionPlanType planType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExecutionPlanStatus status;

    @Column(name = "plan_json", columnDefinition = "TEXT")
    private String planJson;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_by_agent", length = 100)
    private String createdByAgent;

    @Column(name = "memory_context_id", length = 36)
    private String memoryContextId;

    @Column(name = "workspace_memory_id", length = 36)
    private String workspaceMemoryId;

    @Override
    @Transient
    public String getDisplay() {
        return requirementId + ":v" + version + ":" + status;
    }
}
