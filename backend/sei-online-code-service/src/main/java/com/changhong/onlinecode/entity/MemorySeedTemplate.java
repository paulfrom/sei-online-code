package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.MemorySeedTemplateSourceType;
import com.changhong.onlinecode.dto.enums.MemorySeedTemplateStatus;
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
 * 平台可配置的 seed 记忆模板。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §6.1.0、§8.1。
 *
 * <p>保存四个 {@code agent-memory} 文件模板内容（markdown 字符串）。同一时间全局只能有一个
 * {@code ACTIVE + is_default=true} 模板，由 service 在切换默认事务中维护。</p>
 *
 * <p>四个模板内容以 TEXT 列直接承载 markdown 字符串；本版本不为模板正文引入结构化 POJO，保持「仅最少代码」。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_memory_seed_template", indexes = {
        @Index(name = "idx_memory_seed_template_code", columnList = "code"),
        @Index(name = "idx_memory_seed_template_status", columnList = "status"),
        @Index(name = "idx_memory_seed_template_default", columnList = "is_default,status"),
        @Index(name = "uk_memory_seed_template_code_version", columnList = "code,version", unique = true)
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class MemorySeedTemplate extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    /** 模板编码，同 code 不同 version 表示迭代发布。 */
    @Column(name = "code", nullable = false, length = 128)
    private String code;

    /** 模板名称。 */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 模板描述。 */
    @Column(name = "description", length = 1000)
    private String description;

    /** 版本号，同 code 递增。 */
    @Column(name = "version", nullable = false)
    private Integer version;

    /** 模板状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MemorySeedTemplateStatus status;

    /** 是否全局默认模板；全局仅一个 ACTIVE+is_default=true。 */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    /** 来源类型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private MemorySeedTemplateSourceType sourceType;

    /** project-memory.md 模板正文（markdown）。 */
    @Column(name = "project_memory_template", columnDefinition = "TEXT")
    private String projectMemoryTemplate;

    /** memory-rules.md 模板正文（markdown）。 */
    @Column(name = "memory_rules_template", columnDefinition = "TEXT")
    private String memoryRulesTemplate;

    /** decisions.md 模板正文（markdown）。 */
    @Column(name = "decisions_template", columnDefinition = "TEXT")
    private String decisionsTemplate;

    /** modules.md 模板正文（markdown）。 */
    @Column(name = "modules_template", columnDefinition = "TEXT")
    private String modulesTemplate;

    /** 发布时间，进入 ACTIVE 时写入。 */
    @Column(name = "published_at")
    private Date publishedAt;

    /** 归档时间，进入 ARCHIVED 时写入。 */
    @Column(name = "archived_at")
    private Date archivedAt;

    @Override
    @Transient
    public String getDisplay() {
        return code + ":" + version;
    }
}