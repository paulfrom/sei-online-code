package com.changhong.onlinecode.entity;

import com.changhong.sei.core.entity.BaseAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Agent↔Skill 关联实体（对齐 multica 维度 a，独立 join 表，取代原 oc_agent.skill_ids JSON 列）。
 *
 * <p>{@code skill_id} 不加 FK 约束：为 Phase 6 内置技能 synthetic id（{@code builtin:<name>}）预留；
 * {@code agent_id} 保留 FK + ON DELETE CASCADE。绑定关系整体替换由
 * {@link com.changhong.onlinecode.service.AgentService#attachSkills} 负责（删旧+插新）。</p>
 *
 * @author sei-online-code
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "oc_agent_skill", indexes = {
        @Index(name = "uk_agent_skill", columnList = "agent_id, skill_id", unique = true),
        @Index(name = "idx_agent_skill_skill", columnList = "skill_id")
})
@Access(AccessType.FIELD)
public class AgentSkill extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "agent_id", nullable = false, length = 36)
    private String agentId;

    @Column(name = "skill_id", nullable = false, length = 36)
    private String skillId;
}