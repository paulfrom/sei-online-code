package com.changhong.onlinecode.entity;

import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.util.List;

/**
 * Agent 实体。契约 Phase 3 §1.2 —— 用户自定义开发 agent（指令 + 绑定的技能）。
 *
 * <p><b>Agent↔Skill 关联模型：</b>独立 join 表 {@code oc_agent_skill}（对齐 multica 维度 a），
 * 取代原 {@code skill_ids} JSON 列。{@code skillIds} 字段为 {@code @Transient}，由
 * {@link com.changhong.onlinecode.service.AgentService} 从 join 表 populate；持久化经
 * {@code AgentService.attachSkills}。{@code skill_id} 不加 FK（为 Phase 6 内置技能
 * synthetic id {@code builtin:<name>} 预留）。</p>
 *
 * <p>三个内置 agent（{@code builtin=true}，不可删除）：requirement-agent / dispatch-agent /
 * deploy-agent。自定义 agent 为 {@code builtin=false}。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_agent", indexes = {
        @Index(name = "uk_agent_name", columnList = "name", unique = true)
})
@Access(AccessType.FIELD)
public class Agent extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "builtin", nullable = false)
    private Boolean builtin = Boolean.FALSE;

    /**
     * 绑定的技能 id 列表（@Transient 派生，非持久化）。由 AgentService 从 oc_agent_skill
     * join 表 populate；setSkillIds 仅作 DTO 映射回填，持久化经 AgentService.attachSkills。
     */
    @Transient
    private List<String> skillIds;

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

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Boolean getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Boolean builtin) {
        this.builtin = builtin;
    }

    public List<String> getSkillIds() {
        return skillIds;
    }

    public void setSkillIds(List<String> skillIds) {
        this.skillIds = skillIds;
    }

    @Override
    @Transient
    public String getDisplay() {
        return name;
    }
}
