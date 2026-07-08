package com.changhong.onlinecode.entity;

import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能辅助文件实体（对齐 multica 维度 e，独立子表 oc_skill_file，兑现契约 Phase 3 §1.1
 * deferred 的 per-file {@code FileRef[]}）。每个行是 {@code .claude/skills/<skill.name>/}
 * 目录下一个相对路径文件，由 {@link com.changhong.onlinecode.agent.SkillMaterializer} 随
 * SKILL.md 一并写入 worktree。
 *
 * <p>{@code skill_id} 加 FK + ON DELETE CASCADE（与 oc_agent_skill.skill_id 不同——后者为
 * {@code builtin:<name>} synthetic id 预留而不加 FK；本表是真实 oc_skill 子行，FK 合理，
 * 删技能时辅助文件级联清除）。{@code (skill_id, path)} 唯一，避免同技能重复路径。</p>
 *
 * <p>辅助文件<b>不进</b> §6 hash recipe——{@code .lock} 仍只覆盖 SKILL.md 五元组
 * (v1|origin|name|description|content)；详见 {@link Skill#getComputedHash()}。import 以 name
 * 去重且无 update 端点 → 辅助文件导入后不可变 → hash 不覆盖 files 对幂等无影响。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_skill_file", indexes = {
        @Index(name = "uk_skill_file", columnList = "skill_id, path", unique = true),
        @Index(name = "idx_skill_file_skill", columnList = "skill_id")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillFile extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "skill_id", nullable = false, length = 36)
    private String skillId;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    }
