package com.changhong.onlinecode.dto.progress;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Safe, observational snapshot returned by the manual workspace refresh endpoint. */
@Data
@Schema(description = "需求工作区 Git 状态")
public class RequirementWorkspaceStatusDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "工作区路径")
    private String workspacePath;

    @Schema(description = "当前需求分支")
    private String branchName;

    @Schema(description = "项目配置的工作区更新基线分支")
    private String baseBranch;

    @Schema(description = "项目配置或平台回退的 MR 目标分支")
    private String deliveryTargetBranch;

    @Schema(description = "工作区创建时的基线提交")
    private String baseCommit;

    @Schema(description = "当前物理 HEAD")
    private String currentHead;

    @Schema(description = "是否存在未提交修改")
    private boolean dirty;

    @Schema(description = "未提交修改涉及的文件")
    private List<String> changedFiles = new ArrayList<>();

    @Schema(description = "刷新时间")
    private Date refreshedAt;
}
