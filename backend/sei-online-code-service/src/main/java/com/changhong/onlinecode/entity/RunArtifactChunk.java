package com.changhong.onlinecode.entity;

import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * gzip 压缩后的 Run artifact 分块。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "oc_run_artifact_chunk",
        indexes = @Index(name = "idx_run_artifact_chunk_artifact", columnList = "artifact_id,sequence_no"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_run_artifact_chunk_sequence",
                columnNames = {"artifact_id", "sequence_no"}))
@Access(AccessType.FIELD)
public class RunArtifactChunk extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "artifact_id", nullable = false, length = 36)
    private String artifactId;

    @Column(name = "sequence_no", nullable = false)
    private Long sequenceNo;

    @Column(name = "first_log_sequence")
    private Long firstLogSequence;

    @Column(name = "last_log_sequence")
    private Long lastLogSequence;

    @Column(name = "uncompressed_length", nullable = false)
    private Integer uncompressedLength;

    @Column(name = "compressed_data", nullable = false, columnDefinition = "BYTEA")
    private byte[] compressedData;

    @Override
    @Transient
    public String getDisplay() {
        return artifactId + "#" + sequenceNo;
    }
}
