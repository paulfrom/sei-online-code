package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.MemoryValidationStatus;
import com.changhong.onlinecode.dto.enums.DeliveryMrStatus;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * Requirement DTO。契约 §2.2。
 *
 * @author sei-online-code
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "需求 DTO")
public class RequirementDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "项目 ID 不能为空")
    @Schema(description = "所属项目 ID")
    private String projectId;

    @Schema(description = "需求编号")
    private String requirementNo;

    @NotBlank(message = "需求标题不能为空")
    @Schema(description = "需求标题")
    private String title;

    @Schema(description = "需求描述")
    private String description;

    @Schema(description = "PRD 状态")
    private RequirementStatus status;

    @Schema(description = "自动化状态")
    private RequirementAutomationStatus automationStatus;

    @Schema(description = "PRD 版本号")
    private Integer prdVersion;

    @Schema(description = "PRD 内容（JSON 字符串）")
    private String prdContent;

    @Schema(description = "设计上下文 ID")
    private String designContextId;

    @Schema(description = "记忆校验状态")
    private MemoryValidationStatus memoryValidationStatus;

    @Schema(description = "记忆校验结果（JSON 字符串）")
    private String memoryValidationResultJson;

    @Schema(description = "当前自动化循环 ID")
    private String activeLoopId;

    @Schema(description = "当前计划修订序号")
    private Long revisionSeq;

    @Schema(description = "已应用的计划修订序号")
    private Long appliedRevisionSeq;

    @Schema(description = "计划修订状态")
    private RequirementRevisionState revisionState;

    @Schema(description = "触发当前修订的评论 ID")
    private String revisionTriggerCommentId;

    @Schema(description = "计划修订失败原因")
    private String revisionFailureReason;

    @Schema(description = "验收时间")
    private Date acceptedAt;

    @Schema(description = "验收 agent")
    private String acceptedByAgent;

    @Schema(description = "交付分支")
    private String deliveryBranch;

    @Schema(description = "交付 commit hash")
    private String deliveryCommitHash;

    @Schema(description = "交付 MR URL")
    private String deliveryMrUrl;

    @Schema(description = "GitLab MR 项目内编号")
    private Long deliveryMrIid;

    @Schema(description = "GitLab MR 状态")
    private DeliveryMrStatus deliveryMrStatus;

    @Schema(description = "MR 合并时间")
    private Date deliveryMergedAt;

    @Schema(description = "MR 合并提交哈希")
    private String deliveryMergeCommitHash;

    @Schema(description = "交付目标分支")
    private String deliveryTargetBranch;

    @Schema(description = "失败摘要")
    private String failureSummary;

    @Schema(description = "创建时间")
    private Date createdDate;

    @Schema(description = "最后编辑时间")
    private Date lastEditedDate;
}
