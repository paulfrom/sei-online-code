package com.changhong.onlinecode.dto.progress;

import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 最近 Run 视图（计划 §3 overview.recentRuns）。敏感字段不返回。
 */
@Data
@Schema(description = "最近 Run 视图")
public class RecentRunDto {

    @Schema(description = "Run ID")
    private String runId;

    @Schema(description = "Execution ID")
    private String executionId;

    @Schema(description = "运行序号")
    private Integer runNo;

    @Schema(description = "Run 状态")
    private RunState state;

    @Schema(description = "终止原因")
    private RunTerminalReason terminalReason;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "最新 observation 摘要")
    private String latestObservationSummary;

    @Schema(description = "开始时间")
    private Date startedDate;

    @Schema(description = "结束时间")
    private Date finishedDate;
}
