package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.MemoryRecordStatus;
import com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus;
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
 * 需求生成 PRD/概览设计/详细设计时使用的设计依据。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.3、§14。
 *
 * <p>每个 {@code requirement_id} 同一时间只有一个 {@link MemoryRecordStatus#CURRENT}；由 service 在同一
 * 事务维护版本切换。包含需求指纹、需求相关补扫快照、RequirementConflictReport、DesignBasis、校验结果。
 * {@code platform-memory/requirement-conflict-report.md} 与 {@code design-basis.md} 只保存 latest，
 * 历史上下文只在 DB 保留。</p>
 *
 * <p>JSON 列以 TEXT 承载字符串，序列化由 service 负责；第一版不引入结构化 POJO。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_requirement_design_context", indexes = {
        @Index(name = "idx_req_design_ctx_project", columnList = "project_id"),
        @Index(name = "idx_req_design_ctx_requirement", columnList = "requirement_id"),
        @Index(name = "idx_req_design_ctx_requirement_status", columnList = "requirement_id,status")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class RequirementDesignContext extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    /** 关联项目。 */
    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    /** 关联需求。 */
    @Column(name = "requirement_id", nullable = false, length = 36)
    private String requirementId;

    /** 生成时引用的 WorkspaceMemory id。 */
    @Column(name = "workspace_memory_id", length = 36)
    private String workspaceMemoryId;

    /** 上下文版本号，单调递增。 */
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /** 版本状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MemoryRecordStatus status;

    /** 上下文可用状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "context_status", nullable = false, length = 32)
    private RequirementDesignContextStatus contextStatus;

    /** 需求指纹。 */
    @Column(name = "requirement_fingerprint", length = 128)
    private String requirementFingerprint;

    /** 需求相关补扫快照（JSON 字符串）。 */
    @Column(name = "requirement_related_snapshot_json", columnDefinition = "TEXT")
    private String requirementRelatedSnapshotJson;

    /** RequirementConflictReport（JSON 字符串）。 */
    @Column(name = "requirement_conflict_report_json", columnDefinition = "TEXT")
    private String requirementConflictReportJson;

    /** DesignBasis 正文（markdown）。 */
    @Column(name = "design_basis", columnDefinition = "TEXT")
    private String designBasis;

    /** 校验结果（JSON 字符串）。 */
    @Column(name = "validation_result_json", columnDefinition = "TEXT")
    private String validationResultJson;

    /** 源文件指纹集合（JSON 数组字符串）。 */
    @Column(name = "source_fingerprints_json", columnDefinition = "TEXT")
    private String sourceFingerprintsJson;

    /** 失败摘要。 */
    @Column(name = "failure_summary", columnDefinition = "TEXT")
    private String failureSummary;

    /** 失败详情。 */
    @Column(name = "failure_detail", columnDefinition = "TEXT")
    private String failureDetail;

    /** 生成时间。 */
    @Column(name = "generated_at")
    private Date generatedAt;

    @Override
    @Transient
    public String getDisplay() {
        return requirementId + ":v" + version;
    }
}