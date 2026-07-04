package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.skill.SkillConfig;
import com.changhong.onlinecode.entity.converter.SkillConfigConverter;
import com.changhong.onlinecode.service.support.SkillHasher;
import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 实体。契约 Phase 3 §1.1 —— 可导入、hash 锁定的指令包（单文件 SKILL.md）。
 *
 * <p>Phase 3 起弃持久化 hash：{@code computedHash} 不再落库，改为 {@link Transient} 运行时按 §6
 * length-prefixed sha256 从 (v1|config.origin|name|description|content) 计算。导入去重以 {@code name}
 * 为键（DB 层 {@code uk_skill_name} 唯一约束 + service 层 ConflictException 409），不再按 hash
 * 幂等。{@code computedHash} 仍由 materializer 写入 worktree {@code .lock} 作复现标记。辅助文件经子表
 * {@code oc_skill_file} 承载（Phase 5 / multica 维度 e，兑现 §1.1 deferred 的 per-file {@code FileRef[]}），
 * 由 {@link #getFiles()} @Transient 派生——<b>不进</b> §6 hash recipe（lock 仍只覆盖 SKILL.md 五元组）；
 * import 以 name 去重且无 update 端点 → 辅助文件导入后不可变 → 不影响幂等。{@code name} 必须匹配
 * {@code ^[a-z0-9][a-z0-9-]{0,63}$}（materialize 为目录名）。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_skill", indexes = {
        @Index(name = "uk_skill_name", columnList = "name", unique = true)
})
@Access(AccessType.FIELD)
public class Skill extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Convert(converter = SkillConfigConverter.class)
    @Column(name = "config", columnDefinition = "TEXT")
    private SkillConfig config;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 辅助文件（@Transient 派生，从 oc_skill_file populate）。由 materializer 随 SKILL.md
     * 一并写入 worktree。不参与持久化 hash（见 {@link #getComputedHash()}）。
     */
    @Transient
    private List<SkillFile> files = new ArrayList<>();

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

    public SkillConfig getConfig() {
        return config;
    }

    public void setConfig(SkillConfig config) {
        this.config = config;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<SkillFile> getFiles() {
        return files;
    }

    public void setFiles(List<SkillFile> files) {
        this.files = files;
    }

    /**
     * 内容锁（运行时计算，不落库）。按 §6 recipe 从 (v1|config.origin|name|description|content) 计算，
     * 供 materializer {@code .lock} 复现标记与 DTO 返回使用。Phase 3 起导入去重改以 {@code name}
     * 为键，hash 不再参与持久化去重。
     *
     * @return 形如 {@code sha256:<hex>} 的内容锁
     */
    @Transient
    public String getComputedHash() {
        return SkillHasher.compute(config == null ? null : config.getOrigin(), name, description, content);
    }

    @Override
    @Transient
    public String getDisplay() {
        return name;
    }
}
