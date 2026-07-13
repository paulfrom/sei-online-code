package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.ExecutionPlanDto;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * ExecutionPlan API。
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = ExecutionPlanApi.PATH)
public interface ExecutionPlanApi extends BaseEntityApi<ExecutionPlanDto>, FindByPageApi<ExecutionPlanDto> {

    String PATH = "executionPlan";

    @GetMapping(path = "requirement/{requirementId}")
    @Operation(summary = "按 Requirement 查询执行计划")
    ResultData<List<ExecutionPlanDto>> findByRequirement(@PathVariable("requirementId") String requirementId);

    @GetMapping(path = "requirement/{requirementId}/latest")
    @Operation(summary = "查询 Requirement 最新执行计划")
    ResultData<ExecutionPlanDto> findLatestByRequirement(@PathVariable("requirementId") String requirementId);
}
