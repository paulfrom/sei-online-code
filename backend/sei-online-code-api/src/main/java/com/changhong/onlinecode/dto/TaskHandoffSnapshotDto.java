package com.changhong.onlinecode.dto;

import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 编码任务在计划修订时的成果交接快照 DTO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务成果交接快照 DTO")
public class TaskHandoffSnapshotDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "需求 ID")
    private String requirementId;

    @Schema(description = "编码任务 ID")
    private String codingTaskId;

    @Schema(description = "运行 ID")
    private String runId;

    @Schema(description = "修订序号")
    private Long revisionSeq;

    @Schema(description = "触发评论 ID")
    private String triggerCommentId;

    @Schema(description = "快照时的 HEAD commit")
    private String headCommit;

    @Schema(description = "运行基线 commit")
    private String baseCommit;

    @Schema(description = "变更文件列表 JSON")
    private String changedFilesJson;

    @Schema(description = "Git diff 统计")
    private String diffStat;

    @Schema(description = "截断并脱敏后的 diff 摘要")
    private String diffSummary;

    @Schema(description = "进度账本快照 JSON")
    private String progressSnapshotJson;

    @Schema(description = "Run 摘要")
    private String runSummary;

    @Schema(description = "创建时间")
    private Date createdDate;
}
