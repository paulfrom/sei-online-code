package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.TaskExecutionStatus;
import com.changhong.onlinecode.dto.enums.TaskExecutionType;
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

import java.util.Date;

/**
 * TaskExecution 实体。契约 ADR-001 §1/§6 / 数据模型 §4 —— 同一业务任务与输入快照的一次逻辑执行，
 * 重复 Run 通过稳定 execution_key 共享同一 Execution 与进度账本。
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_task_execution", indexes = {
        @Index(name = "idx_task_exec_req_loop_status", columnList = "requirement_id,loop_id,status"),
        @Index(name = "idx_task_exec_biz_input", columnList = "business_task_id,input_hash"),
        @Index(name = "idx_task_exec_workspace", columnList = "requirement_workspace_id")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskExecution extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "execution_key", length = 128, nullable = false)
    private String executionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private TaskExecutionType taskType;

    @Column(name = "business_task_id", length = 36, nullable = false)
    private String businessTaskId;

    @Column(name = "coding_task_id", length = 36)
    private String codingTaskId;

    @Column(name = "requirement_id", length = 36, nullable = false)
    private String requirementId;

    @Column(name = "loop_id", length = 64, nullable = false)
    private String loopId;

    @Column(name = "input_hash", length = 64, nullable = false)
    private String inputHash;

    @Column(name = "plan_version", nullable = false)
    private Integer planVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TaskExecutionStatus status;

    @Column(name = "requirement_workspace_id", length = 36, nullable = false)
    private String requirementWorkspaceId;

    @Column(name = "base_commit", length = 64, nullable = false)
    private String baseCommit;

    @Column(name = "latest_head", length = 64)
    private String latestHead;

    /** 仅用于观测，不作为完成判定依据。 */
    @Column(name = "active_run_id", length = 36)
    private String activeRunId;

    @Column(name = "last_progress_at")
    private Date lastProgressAt;

    /** 完成收口幂等键，非空时全局唯一。 */
    @Column(name = "settlement_key", length = 128)
    private String settlementKey;

    /** CAS/乐观锁版本，与显式 @Query 更新配合。 */
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Override
    @Transient
    public String getDisplay() {
        return taskType + " " + businessTaskId + " [" + status + "]";
    }
}
