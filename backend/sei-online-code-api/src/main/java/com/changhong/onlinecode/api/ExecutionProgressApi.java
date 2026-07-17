package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.enums.ExecutionEffectStatus;
import com.changhong.onlinecode.dto.progress.ExecutionCheckpointDto;
import com.changhong.onlinecode.dto.progress.ExecutionEffectDto;
import com.changhong.onlinecode.dto.progress.ExecutionStepDto;
import com.changhong.onlinecode.dto.progress.RequirementExecutionOverviewDto;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 执行进度聚合查询 API。契约 ADR-001 §11 / 计划 §3。
 *
 * <p>权威状态由后端账本聚合；前端不得从摘要/日志/退出码/MR 评论推断。</p>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = ExecutionProgressApi.PATH)
public interface ExecutionProgressApi {

    String PATH = "executionProgress";

    @GetMapping("findOverview")
    @Operation(summary = "需求执行进度聚合 overview",
            description = "服务端权威快照：自动化/MR 状态分离、workspace、步骤汇总、当前步骤、最新 checkpoint、最近 Run。")
    ResultData<RequirementExecutionOverviewDto> findOverview(@RequestParam("requirementId") String requirementId);

    @GetMapping("findSteps")
    @Operation(summary = "Execution 步骤列表")
    ResultData<List<ExecutionStepDto>> findSteps(@RequestParam("executionId") String executionId);

    @GetMapping("findCheckpoints")
    @Operation(summary = "步骤 checkpoint 分页（按序号倒序）")
    ResultData<PageResult<ExecutionCheckpointDto>> findCheckpoints(@RequestParam("stepId") String stepId,
                                                             @RequestParam(value = "page", defaultValue = "1") int page,
                                                             @RequestParam(value = "rows", defaultValue = "20") int rows);

    @GetMapping("findEffects")
    @Operation(summary = "Execution effect 分页（按 preparedAt 倒序）")
    ResultData<PageResult<ExecutionEffectDto>> findEffects(@RequestParam("executionId") String executionId,
                                                     @RequestParam(value = "status", required = false) ExecutionEffectStatus status,
                                                     @RequestParam(value = "page", defaultValue = "1") int page,
                                                     @RequestParam(value = "rows", defaultValue = "20") int rows);
}
