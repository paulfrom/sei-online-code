package com.changhong.onlinecode.entity;

import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 计划修订时对编码任务工作区和进度账本的成果交接快照。
 *
 * <p>快照内容由采集服务在写入前截断和脱敏。这里集中声明上限，供采集服务复用，
 * 数据库使用 TEXT 避免不同 PostgreSQL 驱动对超长 VARCHAR 的校验差异。
 */
@Entity
@Table(name = "oc_task_handoff_snapshot", indexes = {
        @Index(name = "uk_task_handoff_task_revision", columnList = "coding_task_id, revision_seq", unique = true),
        @Index(name = "idx_task_handoff_requirement_revision", columnList = "requirement_id, revision_seq"),
        @Index(name = "idx_task_handoff_run", columnList = "run_id")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskHandoffSnapshot extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    public static final int MAX_CHANGED_FILES_JSON_LENGTH = 65_535;
    public static final int MAX_DIFF_STAT_LENGTH = 32_768;
    public static final int MAX_DIFF_SUMMARY_LENGTH = 262_144;
    public static final int MAX_PROGRESS_SNAPSHOT_JSON_LENGTH = 131_072;
    public static final int MAX_RUN_SUMMARY_LENGTH = 32_768;

    @Column(name = "requirement_id", nullable = false, length = 36)
    private String requirementId;

    @Column(name = "coding_task_id", nullable = false, length = 36)
    private String codingTaskId;

    @Column(name = "run_id", length = 36)
    private String runId;

    @Column(name = "revision_seq", nullable = false)
    private Long revisionSeq;

    @Column(name = "trigger_comment_id", nullable = false, length = 36)
    private String triggerCommentId;

    @Column(name = "head_commit", length = 64)
    private String headCommit;

    @Column(name = "base_commit", length = 64)
    private String baseCommit;

    @Column(name = "changed_files_json", columnDefinition = "TEXT")
    private String changedFilesJson;

    @Column(name = "diff_stat", columnDefinition = "TEXT")
    private String diffStat;

    @Column(name = "diff_summary", columnDefinition = "TEXT")
    private String diffSummary;

    @Column(name = "progress_snapshot_json", columnDefinition = "TEXT")
    private String progressSnapshotJson;

    @Column(name = "run_summary", columnDefinition = "TEXT")
    private String runSummary;

    @Override
    @Transient
    public String getDisplay() {
        return codingTaskId + ":r" + revisionSeq;
    }
}
