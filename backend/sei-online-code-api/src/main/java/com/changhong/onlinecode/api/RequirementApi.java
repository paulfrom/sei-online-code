package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.RequirementDto;
import com.changhong.onlinecode.dto.RequirementCommentDto;
import com.changhong.onlinecode.dto.progress.RequirementWorkspaceStatusDto;
import com.changhong.onlinecode.dto.request.CreateRequirementCommentRequest;
import com.changhong.onlinecode.dto.request.EditPrdRequest;
import com.changhong.onlinecode.dto.request.RegeneratePrdRequest;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Requirement API。契约 §3.2。
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = RequirementApi.PATH)
public interface RequirementApi extends BaseEntityApi<RequirementDto>, FindByPageApi<RequirementDto> {

    String PATH = "requirement";

    @PostMapping(path = "{id}/regeneratePrd", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "重生成 PRD", description = "PRD_REVIEW 或 FAILED 状态下可重生成")
    ResultData<RequirementDto> regeneratePrd(@PathVariable("id") String id,
                                                   @RequestBody @Valid RegeneratePrdRequest request);

    @PostMapping(path = "{id}/editPrd", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "编辑 PRD", description = "PRD_REVIEW 状态下可编辑")
    ResultData<RequirementDto> editPrd(@PathVariable("id") String id,
                                          @RequestBody @Valid EditPrdRequest request);

    @PostMapping(path = "{id}/confirmPrd")
    @Operation(summary = "确认 PRD", description = "确认后冻结 PRD 并启动 PM 自动化执行循环")
    ResultData<RequirementDto> confirmPrd(@PathVariable("id") String id);

    @PostMapping(path = "{id}/confirmCompletion")
    @Operation(summary = "确认需求完成", description = "用户确认需求完成后清理该需求工作区")
    ResultData<RequirementDto> confirmCompletion(@PathVariable("id") String id);

    @PostMapping(path = "{id}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "追加 Requirement 评论", description = "活跃需求在当前 loop 内增量修订计划；已完成需求会启动 CHANGE_REQUEST loop")
    ResultData<RequirementCommentDto> addComment(@PathVariable("id") String id,
                                                 @RequestBody @Valid CreateRequirementCommentRequest request);

    @PostMapping(path = "{id}/revision/retry")
    @Operation(summary = "重试计划修订", description = "仅允许重试当前 loop 中处于 FAILED 的最新修订，不递增 revisionSeq")
    ResultData<RequirementDto> retryRevision(@PathVariable("id") String id);

    @PostMapping(path = "{id}/mr/retry")
    @Operation(summary = "重试 GitLab MR 交付", description = "修复 GitLab 配置后重试交付，不重新执行开发或验收")
    ResultData<RequirementDto> retryMr(@PathVariable("id") String id);

    @PostMapping(path = "{id}/mr/submit")
    @Operation(summary = "手动提交当前交付物", description = "提交当前 loop 最新已验收计划的工作区成果并创建或更新 GitLab MR")
    ResultData<RequirementDto> submitMr(@PathVariable("id") String id);

    @PostMapping(path = "{id}/workspace/refresh")
    @Operation(summary = "刷新需求工作区状态", description = "读取并记录物理工作区的分支、HEAD 与未提交文件，不执行 reset/rebase")
    ResultData<RequirementWorkspaceStatusDto> refreshWorkspace(@PathVariable("id") String id);

    @PostMapping(path = "{id}/resume")
    @Operation(summary = "恢复当前执行计划",
            description = "幂等重建当前 loop 的任务边界并重新触发调度，不生成新计划或新 loop")
    ResultData<RequirementDto> resumeAutomation(@PathVariable("id") String id);

    @PostMapping(path = "{id}/stop")
    @Operation(summary = "停止需求自动化", description = "中断当前计划、取消活跃 Run，并旋转 loop 使旧结果失效")
    ResultData<RequirementDto> stopAutomation(@PathVariable("id") String id);
}
