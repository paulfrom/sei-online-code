package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.WorkspaceMemoryFreshness;
import com.changhong.onlinecode.dto.enums.WorkspaceMemoryStatus;
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
 * 目标项目工作区的一版记忆。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.2。
 *
 * <p>聚合 {@link MemorySeedTemplate}（项目绑定的 seed 版本）、agent-memory 与项目规范指纹、
 * 代码扫描得到的 NormClaim/RealityClaim/ConflictFinding 及 WorkspaceNorms/WorkspaceSnapshot。
 * 每个 {@code project_id} 同一时间只有一个 {@link WorkspaceMemoryStatus#CURRENT}；重建时新行置 CURRENT、
 * 旧行置 ARCHIVED；失败新增 FAILED 行，旧 CURRENT 不变（由 service 在同一事务维护）。</p>
 *
 * <p>各 JSON 列以 TEXT 承载字符串（序列化由 service 负责）；第一版不为 NormClaim/RealityClaim 等
 * 引入结构化 POJO 与明细表（契约 §2 非目标）。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_workspace_memory", indexes = {
        @Index(name = "idx_workspace_memory_project", columnList = "project_id"),
        @Index(name = "idx_workspace_memory_project_status", columnList = "project_id,status")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkspaceMemory extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    /** 关联项目。 */
    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    /** 记忆版本号，单调递增。 */
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /** 版本状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WorkspaceMemoryStatus status;

    /** 新鲜度。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "freshness", nullable = false, length = 48)
    private WorkspaceMemoryFreshness freshness = WorkspaceMemoryFreshness.FRESH;

    /** 记忆规范版本（平台 schema/template/prompt 版本）。 */
    @Column(name = "memory_spec_version", nullable = false)
    private Integer memorySpecVersion = 1;

    /** 项目绑定的 seed 模板 id；已归档仍保留，用于缺文件补齐。 */
    @Column(name = "memory_seed_template_id", length = 36)
    private String memorySeedTemplateId;

    /** 写入 agent-memory 时使用的 seed 版本号。 */
    @Column(name = "agent_memory_seed_version")
    private Integer agentMemorySeedVersion;

    /** 工作区根目录路径快照。 */
    @Column(name = "workspace_path", length = 500)
    private String workspacePath;

    /** agent-memory 内容指纹。 */
    @Column(name = "agent_memory_fingerprint", length = 128)
    private String agentMemoryFingerprint;

    /** agent-memory 四文件聚合 markdown 快照。 */
    @Column(name = "agent_memory_markdown", columnDefinition = "TEXT")
    private String agentMemoryMarkdown;

    /** 项目规范文件指纹。 */
    @Column(name = "project_rule_fingerprint", length = 128)
    private String projectRuleFingerprint;

    /** 项目规范文件聚合 markdown 快照。 */
    @Column(name = "project_rule_markdown", columnDefinition = "TEXT")
    private String projectRuleMarkdown;

    /** 源文件指纹集合（JSON 数组字符串）。 */
    @Column(name = "source_fingerprints_json", columnDefinition = "TEXT")
    private String sourceFingerprintsJson;

    /** NormClaim 列表（JSON 字符串）。 */
    @Column(name = "norm_claims_json", columnDefinition = "TEXT")
    private String normClaimsJson;

    /** RealityClaim 列表（JSON 字符串）。 */
    @Column(name = "reality_claims_json", columnDefinition = "TEXT")
    private String realityClaimsJson;

    /** ConflictFinding 列表（JSON 字符串）。 */
    @Column(name = "conflict_findings_json", columnDefinition = "TEXT")
    private String conflictFindingsJson;

    /** WorkspaceNorms（JSON 字符串）。 */
    @Column(name = "workspace_norms_json", columnDefinition = "TEXT")
    private String workspaceNormsJson;

    /** WorkspaceSnapshot（JSON 字符串）。 */
    @Column(name = "workspace_snapshot_json", columnDefinition = "TEXT")
    private String workspaceSnapshotJson;

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
        return projectId + ":v" + version;
    }
}