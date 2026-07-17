package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.ExecutionProgressApi;
import com.changhong.onlinecode.dto.enums.ExecutionEffectStatus;
import com.changhong.onlinecode.dto.progress.ExecutionCheckpointDto;
import com.changhong.onlinecode.dto.progress.ExecutionEffectDto;
import com.changhong.onlinecode.dto.progress.ExecutionStepDto;
import com.changhong.onlinecode.dto.progress.RequirementExecutionOverviewDto;
import com.changhong.onlinecode.service.progress.ExecutionProgressQueryService;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 执行进度聚合查询控制器。实现 {@link ExecutionProgressApi}，委托 {@link ExecutionProgressQueryService}。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "ExecutionProgressApi", description = "执行进度聚合查询")
@RequestMapping(path = ExecutionProgressApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class ExecutionProgressController implements ExecutionProgressApi {

    private final ExecutionProgressQueryService queryService;

    @Override
    public ResultData<RequirementExecutionOverviewDto> findOverview(String requirementId) {
        return ResultData.success(queryService.findOverview(requirementId));
    }

    @Override
    public ResultData<List<ExecutionStepDto>> findSteps(String executionId) {
        return ResultData.success(queryService.findSteps(executionId));
    }

    @Override
    public ResultData<PageResult<ExecutionCheckpointDto>> findCheckpoints(String stepId, int page, int rows) {
        return ResultData.success(queryService.findCheckpoints(stepId, page, rows));
    }

    @Override
    public ResultData<PageResult<ExecutionEffectDto>> findEffects(String executionId, ExecutionEffectStatus status,
                                                            int page, int rows) {
        return ResultData.success(queryService.findEffects(executionId, status, page, rows));
    }
}
