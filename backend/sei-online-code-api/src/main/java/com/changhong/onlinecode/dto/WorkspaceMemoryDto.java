package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.WorkspaceMemoryFreshness;
import com.changhong.onlinecode.dto.enums.WorkspaceMemoryStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * WorkspaceMemory DTO。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.2。
 *
 * @author sei-online-code
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "工作区记忆 DTO")
public class WorkspaceMemoryDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "所属项目 id")
    private String projectId;

    @Schema(description = "记忆版本号")
    private Integer version;

    @Schema(description = "版本状态", example = "CURRENT")
    private WorkspaceMemoryStatus status;

    @Schema(description = "新鲜度", example = "FRESH")
    private WorkspaceMemoryFreshness freshness;

    @Schema(description = "记忆规范版本")
    private Integer memorySpecVersion;

    @Schema(description = "项目绑定的 seed 模板 id")
    private String memorySeedTemplateId;

    @Schema(description = "写入 agent-memory 时使用的 seed 版本号")
    private Integer agentMemorySeedVersion;

    @Schema(description = "工作区根目录路径")
    private String workspacePath;

    @Schema(description = "agent-memory 内容指纹")
    private String agentMemoryFingerprint;

    @Schema(description = "agent-memory 四文件聚合 markdown 快照")
    private String agentMemoryMarkdown;

    @Schema(description = "项目规范文件指纹")
    private String projectRuleFingerprint;

    @Schema(description = "项目规范文件聚合 markdown 快照")
    private String projectRuleMarkdown;

    @Schema(description = "源文件指纹集合（JSON 数组字符串）")
    private String sourceFingerprintsJson;

    @Schema(description = "NormClaim 列表（JSON 字符串）")
    private String normClaimsJson;

    @Schema(description = "RealityClaim 列表（JSON 字符串）")
    private String realityClaimsJson;

    @Schema(description = "ConflictFinding 列表（JSON 字符串）")
    private String conflictFindingsJson;

    @Schema(description = "WorkspaceNorms（JSON 字符串）")
    private String workspaceNormsJson;

    @Schema(description = "WorkspaceSnapshot（JSON 字符串）")
    private String workspaceSnapshotJson;

    @Schema(description = "失败摘要")
    private String failureSummary;

    @Schema(description = "失败详情")
    private String failureDetail;

    @Schema(description = "生成时间")
    private Date generatedAt;
}