package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.MemoryJobStatus;
import com.changhong.onlinecode.dto.enums.MemoryJobTriggerSource;
import com.changhong.onlinecode.dto.enums.MemoryJobType;
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
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 记忆任务：初始化、刷新、重建、CodingTask 后回写、校验。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.4、§12。
 *
 * <p>同一 {@code project_id} 同一时间只允许一个 active job（PENDING/RUNNING），第一版按 {@code project_id}
 * 串行执行。使用 {@link #idempotencyKey} 防重复投递。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_memory_job", indexes = {
        @Index(name = "idx_memory_job_project_status", columnList = "project_id,status"),
        @Index(name = "idx_memory_job_next_retry", columnList = "status,next_retry_at"),
        @Index(name = "uk_memory_job_idempotency", columnList = "idempotency_key", unique = true)
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class MemoryJob extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    /** 关联项目。 */
    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    /** 关联需求，可空。 */
    @Column(name = "requirement_id", length = 36)
    private String requirementId;

    @Column(name = "loop_id", length = 64)
    private String loopId;

    /** 关联 CodingTask，可空。 */
    @Column(name = "coding_task_id", length = 36)
    private String codingTaskId;

    /** 关联执行 run id，可空。 */
    @Column(name = "run_id", length = 36)
    private String runId;

    /** 任务类型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 48)
    private MemoryJobType jobType;

    /** 任务状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MemoryJobStatus status = MemoryJobStatus.PENDING;

    /** 触发来源。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 32)
    private MemoryJobTriggerSource triggerSource;

    /** 前一个 WorkspaceMemory id。 */
    @Column(name = "previous_workspace_memory_id", length = 36)
    private String previousWorkspaceMemoryId;

    /** 任务产出的新 WorkspaceMemory id。 */
    @Column(name = "new_workspace_memory_id", length = 36)
    private String newWorkspaceMemoryId;

    /** 增量回写基准 WorkspaceMemory id。 */
    @Column(name = "base_workspace_memory_id", length = 36)
    private String baseWorkspaceMemoryId;

    /** Traceability payload for requirement-level delivery memory jobs. */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    /** 幂等键。 */
    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    /** 优先级（数字越小越高）。 */
    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    /** 已重试次数。 */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /** 最大重试次数。 */
    @Column(name = "max_retry_count", nullable = false)
    private Integer maxRetryCount = 3;

    /** 下次重试时间。 */
    @Column(name = "next_retry_at")
    private Date nextRetryAt;

    /** 开始执行时间。 */
    @Column(name = "started_at")
    private Date startedAt;

    /** 完成时间。 */
    @Column(name = "finished_at")
    private Date finishedAt;

    /** 失败摘要。 */
    @Column(name = "failure_summary", columnDefinition = "TEXT")
    private String failureSummary;

    /** 失败详情。 */
    @Column(name = "failure_detail", columnDefinition = "TEXT")
    private String failureDetail;

    @Override
    @Transient
    public String getDisplay() {
        return projectId + ":" + jobType;
    }
}
