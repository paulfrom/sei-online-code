package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.EvidenceCompleteness;
import com.changhong.onlinecode.dto.enums.RunArtifactState;
import com.changhong.onlinecode.dto.enums.RunArtifactType;
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
 * Run 原始材料元数据。内容由 {@link RunArtifactChunk} 按序分块保存。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "oc_run_artifact", indexes = {
        @Index(name = "idx_run_artifact_run_type", columnList = "run_id,artifact_type"),
        @Index(name = "idx_run_artifact_expires", columnList = "expires_at")
})
@Access(AccessType.FIELD)
public class RunArtifact extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 32)
    private RunArtifactType artifactType;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private RunArtifactState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "completeness", nullable = false, length = 32)
    private EvidenceCompleteness completeness;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "encoding", length = 32)
    private String encoding;

    @Column(name = "byte_length")
    private Long byteLength;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "redacted", nullable = false)
    private Boolean redacted = Boolean.TRUE;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "completed_at")
    private Date completedAt;

    @Column(name = "expires_at")
    private Date expiresAt;

    @Override
    @Transient
    public String getDisplay() {
        return runId + ":" + artifactType + "[" + state + "]";
    }
}
