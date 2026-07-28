package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.EvidenceCompleteness;
import com.changhong.onlinecode.dto.enums.RunEvidenceStatus;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 一次 Run 的不可变、版本化结构化证据快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "oc_run_evidence",
        indexes = @Index(name = "idx_run_evidence_run", columnList = "run_id,captured_at"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_run_evidence_version",
                columnNames = {"run_id", "evidence_version"}))
@Access(AccessType.FIELD)
public class RunEvidence extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(name = "evidence_version", nullable = false)
    private Integer evidenceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RunEvidenceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "completeness", nullable = false, length = 32)
    private EvidenceCompleteness completeness;

    @Column(name = "process_exit_code")
    private Integer processExitCode;

    @Column(name = "git_base_commit", length = 64)
    private String gitBaseCommit;

    @Column(name = "git_head_commit", length = 64)
    private String gitHeadCommit;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "command_results_json", columnDefinition = "TEXT")
    private String commandResultsJson;

    @Column(name = "acceptance_criteria_json", columnDefinition = "TEXT")
    private String acceptanceCriteriaJson;

    @Column(name = "findings_json", columnDefinition = "TEXT")
    private String findingsJson;

    @Column(name = "artifact_refs_json", columnDefinition = "TEXT")
    private String artifactRefsJson;

    @Column(name = "captured_at", nullable = false)
    private Date capturedAt;

    @Override
    @Transient
    public String getDisplay() {
        return runId + "@v" + evidenceVersion + "[" + status + "]";
    }
}
