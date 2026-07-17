package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.ExecutionEffectStatus;
import com.changhong.onlinecode.dto.enums.ExecutionEffectType;
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
 * ExecutionEffect 实体。契约 ADR-001 §5 / 数据模型 §8 —— 平台受控副作用及首次结果快照。
 *
 * <p>相同 effectKey + requestHash 返回首次结果；key 相同但 hash 不同稳定冲突，不覆盖原请求。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_execution_effect", indexes = {
        @Index(name = "idx_effect_exec_status", columnList = "execution_id,status"),
        @Index(name = "idx_effect_step_status", columnList = "step_id,status"),
        @Index(name = "idx_effect_type_reference", columnList = "effect_type,external_reference")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionEffect extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "effect_key", length = 200, nullable = false)
    private String effectKey;

    @Column(name = "execution_id", length = 36, nullable = false)
    private String executionId;

    @Column(name = "step_id", length = 36, nullable = false)
    private String stepId;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect_type", nullable = false, length = 32)
    private ExecutionEffectType effectType;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExecutionEffectStatus status;

    @Column(name = "fencing_token", nullable = false)
    private Long fencingToken;

    /** 脱敏后的版本化 JSON。 */
    @Column(name = "request_snapshot", columnDefinition = "TEXT")
    private String requestSnapshot;

    @Column(name = "result_snapshot", columnDefinition = "TEXT")
    private String resultSnapshot;

    /** job key、branch、MR IID 等可查询引用。 */
    @Column(name = "external_reference", length = 500)
    private String externalReference;

    @Column(name = "prepared_at", nullable = false)
    private Date preparedAt;

    @Column(name = "applied_at")
    private Date appliedAt;

    @Column(name = "confirmed_at")
    private Date confirmedAt;

    @Column(name = "last_reconciled_at")
    private Date lastReconciledAt;

    /** CAS/乐观锁版本。 */
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Override
    @Transient
    public String getDisplay() {
        return effectType + " " + effectKey + " [" + status + "]";
    }
}
