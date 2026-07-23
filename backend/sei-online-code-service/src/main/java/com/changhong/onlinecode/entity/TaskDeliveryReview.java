package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.DeliveryFailureCategory;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewStatus;
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
import jakarta.persistence.UniqueConstraint;

import java.util.Date;

/**
 * 任务交付审阅记录（方案 §4.2）。
 *
 * <p>记录 pm-agent 对单次交付 Run 的审阅生命周期。对 {@code (coding_task_id, delivery_run_id)}
 * 建唯一约束，保证重复完成事件不会创建两次 PM 审阅。</p>
 *
 * @author sei-online-code
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "oc_task_delivery_review",
        indexes = {
                @Index(name = "idx_tdr_requirement", columnList = "requirement_id"),
                @Index(name = "idx_tdr_plan", columnList = "execution_plan_id"),
                @Index(name = "idx_tdr_coding_task", columnList = "coding_task_id"),
                @Index(name = "idx_tdr_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tdr_task_run", columnNames = {"coding_task_id", "delivery_run_id"})
        }
)
@Access(AccessType.FIELD)
public class TaskDeliveryReview extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "requirement_id", nullable = false, length = 36)
    private String requirementId;

    @Column(name = "execution_plan_id", length = 36)
    private String executionPlanId;

    @Column(name = "coding_task_id", nullable = false, length = 36)
    private String codingTaskId;

    /** 被审阅的最新交付 Run ID。 */
    @Column(name = "delivery_run_id", nullable = false, length = 36)
    private String deliveryRunId;

    /** pm-agent 审阅 Run ID。 */
    @Column(name = "review_run_id", length = 36)
    private String reviewRunId;

    /** 所属 loopId，用于过期判定。 */
    @Column(name = "loop_id", length = 64)
    private String loopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TaskDeliveryReviewStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 32)
    private TaskDeliveryReviewDecision decision;

    /** PM 结论摘要。 */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** 完整结构化决定 JSON（含 findings / retryReason / remediationTasks / failureCategory）。 */
    @Column(name = "decision_json", columnDefinition = "TEXT")
    private String decisionJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", length = 32)
    private DeliveryFailureCategory failureCategory;

    /** 交付是否成功（交付时记录，用于决策校验：FAILED+APPROVE 必须被拒绝）。 */
    @Column(name = "delivery_succeeded")
    private Boolean deliverySucceeded;

    @Column(name = "reviewed_date")
    private Date reviewedDate;

    @Override
    @Transient
    public String getDisplay() {
        return codingTaskId + ":" + status;
    }
}
