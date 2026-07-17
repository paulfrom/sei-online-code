package com.changhong.onlinecode.dto.progress;

import com.changhong.onlinecode.dto.enums.ExecutionCheckpointType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * ExecutionCheckpoint 查询 DTO（计划 §3 findCheckpoints）。payload/evidence 默认不展开（分页按需）。
 */
@Data
@Schema(description = "ExecutionCheckpoint 查询视图")
public class ExecutionCheckpointDto {

    private String checkpointId;
    private String executionId;
    private String stepId;
    private String runId;
    private Long sequenceNo;
    private ExecutionCheckpointType checkpointType;
    private String gitHead;
    private String parentGitHead;
    @Schema(description = "发生时间（createdDate）")
    private Date occurredAt;
}
