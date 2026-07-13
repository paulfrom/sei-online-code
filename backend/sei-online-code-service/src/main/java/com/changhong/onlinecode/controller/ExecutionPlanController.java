package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.ExecutionPlanApi;
import com.changhong.onlinecode.dto.ExecutionPlanDto;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.service.ExecutionPlanService;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.service.BaseEntityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ExecutionPlan 控制器。
 */
@RestController
@Tag(name = "ExecutionPlanApi", description = "PM 执行计划服务")
@RequestMapping(path = ExecutionPlanApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class ExecutionPlanController extends BaseEntityController<ExecutionPlan, ExecutionPlanDto>
        implements ExecutionPlanApi {

    private final ExecutionPlanService service;

    public ExecutionPlanController(ExecutionPlanService service) {
        this.service = service;
    }

    @Override
    public BaseEntityService<ExecutionPlan> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<ExecutionPlanDto>> findByPage(Search search) {
        PageResult<ExecutionPlan> page = service.findByPage(search);
        PageResult<ExecutionPlanDto> dtoPage = new PageResult<>(page);
        dtoPage.setRows(page.getRows().stream().map(service::convertToDto).collect(Collectors.toList()));
        return ResultData.success(dtoPage);
    }

    @Override
    public ResultData<List<ExecutionPlanDto>> findByRequirement(String requirementId) {
        return ResultData.success(service.findByRequirementId(requirementId).stream()
                .map(service::convertToDto)
                .collect(Collectors.toList()));
    }

    @Override
    public ResultData<ExecutionPlanDto> findLatestByRequirement(String requirementId) {
        return ResultData.success(service.convertToDto(service.findLatestByRequirementId(requirementId)));
    }
}
