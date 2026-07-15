package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.UsageStatus;
import com.changhong.onlinecode.dto.enums.RunType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Run Token Usage 详情 DTO。
 *
 * <p>包含原始 usage JSON，用于追溯和重新解析。</p>
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "Run Token Usage DTO")
public class RunUsageDto {

    @Schema(description = "Run id")
    private String runId;

    @Schema(description = "Run 记录类型")
    private RunType runType;

    @Schema(description = "父 Run id")
    private String parentRunId;

    @Schema(description = "被本 Run 补偿的 Run id")
    private String compensatesRunId;

    @Schema(description = "尝试序号")
    private Integer attemptNo;

    @Schema(description = "调用时的 Agent id 快照")
    private String agentId;

    @Schema(description = "调用时的 Agent name 快照")
    private String agentName;

    @Schema(description = "调用时的实际 CLI 工具名快照")
    private String cliTool;

    @Schema(description = "调用时请求的模型名快照")
    private String model;

    @Schema(description = "输入 token 数")
    private Long inputTokens;

    @Schema(description = "输出 token 数")
    private Long outputTokens;

    @Schema(description = "缓存读取 token 数")
    private Long cacheReadTokens;

    @Schema(description = "缓存写入 token 数")
    private Long cacheWriteTokens;

    @Schema(description = "总 token 数")
    private Long totalTokens;

    @Schema(description = "token usage 可用性状态")
    private UsageStatus usageStatus;

    @Schema(description = "Provider 返回的原始 usage JSON")
    private String rawUsageJson;
}
