package com.changhong.onlinecode.dto;

import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.List;

/**
 * Agent DTO。契约 Phase 3 §1.2 —— 用户自定义开发 agent（指令 + 绑定技能）。
 *
 * @author sei-online-code
 */
@Schema(description = "Agent DTO")
public class AgentDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "agent 名（唯一）", example = "suid-dev")
    private String name;

    @Schema(description = "agent 描述", example = "Builds SUID pages against the EADP contract")
    private String description;

    @Schema(description = "指令；派发时前置到 Task.description", example = "You implement one page...")
    private String instructions;

    @Schema(description = "模型；空串表示由 CLI 自行解析默认值", example = "")
    private String model;

    @Schema(description = "CLI 工具：claude/codex，空表示默认 claude", example = "claude")
    private String cliTool;

    @Schema(description = "是否内置（requirement/dispatch/deploy 为 true，不可删除）", example = "false")
    private Boolean builtin;

    @Schema(description = "绑定的技能 id 列表，spawn 前 materialize", example = "[\"SKIL0001\"]")
    private List<String> skillIds;

    @Schema(description = "创建时间")
    private Date createdDate;

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

    public String getCliTool() {
        return cliTool;
    }

    public void setCliTool(String cliTool) {
        this.cliTool = cliTool;
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

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }
}
