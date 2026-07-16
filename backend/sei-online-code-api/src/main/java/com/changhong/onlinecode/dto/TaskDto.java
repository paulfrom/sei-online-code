package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.TaskState;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Task DTO。契约 Phase 2 §1.1 —— Dispatch Agent 切分出的一个非重叠工作单元。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "Task DTO")
public class TaskDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "任务标题", example = "库存列表页")
    private String title;

    @Schema(description = "任务描述（agent prompt seed）")
    private String description;

    @Schema(description = "文件边界声明（跨任务不重叠）",
            example = "[\"src/pages/stock/list.tsx\", \"src/mocks/stock.ts\"]")
    private List<String> fileScope;

    @Schema(description = "分配的 agent；Phase 2 为单一内建 dev-agent", example = "dev-agent")
    private String assignedAgent;

    @Schema(description = "任务状态", example = "PENDING")
    private TaskState state;

    @Schema(description = "worktree 分支；RUNNING 前为 null", example = "task/ITER0001-0001")
    private String worktreeBranch;

    @Schema(description = "分派/合并顺序", example = "1")
    private Integer seq;

    @Schema(description = "创建时间")
    private Date createdDate;
}