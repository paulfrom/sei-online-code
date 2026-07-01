package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.entity.converter.StringListConverter;
import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.util.List;

/**
 * Agent 实体。契约 Phase 3 §1.2 —— 用户自定义开发 agent（指令 + 绑定的技能）。
 *
 * <p><b>Agent↔Skill 关联模型：</b>采用 {@code skill_ids} JSON（TEXT）列，复用
 * {@link StringListConverter}，而非独立 join 表。理由：本阶段绑定关系简单（一个 agent 持有
 * 一组 skillId），无关联属性、无反向大批量查询需求；JSON 列避免多一张表与额外 DAO，
 * 前端也只感知 {@code skillIds[]}（契约 §1.2）。若未来出现关联元数据或跨 agent 反查，再迁移
 * 为 join 表。</p>
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

    /** 绑定的技能 id 列表，以 JSON（TEXT）列持久化（见类注释关联模型说明）。 */
    @Convert(converter = StringListConverter.class)
    @Column(name = "skill_ids", columnDefinition = "TEXT")
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
