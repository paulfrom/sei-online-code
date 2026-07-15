package com.changhong.onlinecode.dto;

import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Agent DTO。契约 Phase 3 §1.2 —— 用户自定义开发 agent（指令 + 绑定技能）。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "Agent DTO")
public class AgentDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "agent 名（唯一）", example = "suid-dev")
    private String name;

    @Schema(description = "agent 描述", example = "Builds SUID pages against the EADP contract")
    private String description;

    @Schema(description = "指令；派发时前置到 Task.description", example = "You implement one page...")
    private String instructions;

    @Schema(description = "Agent prompt 模板；为空时使用业务上下文 prompt")
    private String promptTemplate;

    @Schema(description = "执行策略，例如是否允许全量验证、是否优先 targeted check")
    private String executionPolicy;

    @Schema(description = "范围策略，例如 task 级只验证交互物、plan 级做集成验证")
    private String scopePolicy;

    @Schema(description = "期望输出 JSON schema 或结构说明")
    private String outputSchema;

    @Schema(description = "模型；空串表示由 CLI 自行解析默认值", example = "")
    private String model;

    @Schema(description = "CLI 工具：claude/codex，空表示默认 claude", example = "claude")
    private String cliTool;

    @Schema(description = "MCP server 配置 JSON（Claude 风格 {\"mcpServers\":{...}}）；空表示不托管，沿用 CLI 默认；\"{}\" 表示托管空集（strict）", example = "{\"mcpServers\":{}}")
    private String mcpConfig;

    @Schema(description = "是否内置（requirement/dispatch/deploy 为 true，不可删除）", example = "false")
    private Boolean builtin;

    @Schema(description = "绑定的技能 id 列表，spawn 前 materialize", example = "[\"SKIL0001\"]")
    private List<String> skillIds;

    @Schema(description = "创建时间")
    private Date createdDate;
}
