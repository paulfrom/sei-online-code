package com.changhong.onlinecode.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;
import java.util.List;

/**
 * 附加/替换 agent 绑定技能请求体。契约 Phase 3 §2 端点 24：POST /api/agent/skills。
 *
 * <p>两步式 multica 流程的第二步：create agent → attach skills。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "附加 agent 技能请求")
public class AttachSkillsRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "agentId 不能为空")
    @Schema(description = "agent id")
    private String agentId;

    @Schema(description = "待绑定的技能 id 列表（整体替换）", example = "[\"SKIL0001\"]")
    private List<String> skillIds;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public List<String> getSkillIds() {
        return skillIds;
    }

    public void setSkillIds(List<String> skillIds) {
        this.skillIds = skillIds;
    }
}
