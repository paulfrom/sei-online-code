package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.SkillSourceType;
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

/**
 * Skill 实体。契约 Phase 3 §1.1 —— 可导入、hash 锁定的指令包（单文件 SKILL.md）。
 *
 * <p>{@code computedHash} 为锁：按 §6 length-prefixed sha256 从 (v1|source|name|description|content)
 * 计算；内容相同则 hash 相同，从而按 hash 幂等去重（重复导入不产生新行）。本阶段仅单文件，
 * 无 FileRef[]。{@code name} 必须匹配 {@code ^[a-z0-9][a-z0-9-]{0,63}$}（materialize 为目录名）。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_skill", indexes = {
        @Index(name = "idx_skill_hash", columnList = "computed_hash"),
        @Index(name = "uk_skill_name", columnList = "name", unique = true)
})
@Access(AccessType.FIELD)
public class Skill extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "source", length = 500)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SkillSourceType sourceType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "computed_hash", nullable = false, length = 80)
    private String computedHash;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public SkillSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SkillSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getComputedHash() {
        return computedHash;
    }

    public void setComputedHash(String computedHash) {
        this.computedHash = computedHash;
    }

    @Override
    @Transient
    public String getDisplay() {
        return name;
    }
}
