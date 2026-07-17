package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.ObservationSourceType;
import com.changhong.onlinecode.dto.enums.RunObservationType;
import com.changhong.onlinecode.dto.enums.VerificationStatus;
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
 * RunObservation 实体。契约 ADR-001 §4 / 数据模型 §9 —— Run 追加式备注/验证/对账时间线，只允许 INSERT。
 *
 * <p>更正通过 supersedesObservationId 指向旧记录，不覆盖历史；人工 observation 不能直接改 step/effect 状态。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_run_observation", indexes = {
        @Index(name = "idx_observation_run_observed", columnList = "run_id,observed_at"),
        @Index(name = "idx_observation_step_observed", columnList = "step_id,observed_at"),
        @Index(name = "idx_observation_status_observed", columnList = "verification_status,observed_at")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class RunObservation extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "run_id", length = 36, nullable = false)
    private String runId;

    /** Run 内单调递增。 */
    @Column(name = "sequence_no", nullable = false)
    private Long sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "observation_type", nullable = false, length = 32)
    private RunObservationType observationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 32)
    private VerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private ObservationSourceType sourceType;

    @Column(name = "source_id", length = 100)
    private String sourceId;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "step_id", length = 36)
    private String stepId;

    @Column(name = "checkpoint_id", length = 36)
    private String checkpointId;

    /** 脱敏后的版本化 JSON。 */
    @Column(name = "evidence_data", columnDefinition = "TEXT")
    private String evidenceData;

    @Column(name = "supersedes_observation_id", length = 36)
    private String supersedesObservationId;

    @Column(name = "observed_at", nullable = false)
    private Date observedAt;

    @Override
    @Transient
    public String getDisplay() {
        return observationType + " #" + sequenceNo + " [" + verificationStatus + "]";
    }
}
