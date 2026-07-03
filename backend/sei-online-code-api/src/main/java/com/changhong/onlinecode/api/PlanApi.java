package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.request.EditPlanRequest;
import com.changhong.onlinecode.dto.request.RegeneratePlanRequest;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 规划书 API。契约 §5 端点 P2–P5、P13。
 *
 * <ul>
 *   <li>P2  GET  /plan/{projectId}             —— 取最新 Plan</li>
 *   <li>P3  PUT  /plan/{projectId}             —— 编辑 content（Plan→DRAFT，关联 FD→STALE）</li>
 *   <li>P4  POST /plan/{projectId}/regenerate   —— 重生（version+1，Plan→GENERATING）</li>
 *   <li>P5  POST /plan/{projectId}/confirm      —— 确认规划书（批量起 FD 智能体）</li>
 *   <li>P13 GET  /plan/{projectId}/history      —— 历史版本列表</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = PlanApi.PATH)
public interface PlanApi {

    String PATH = "plan";

    @GetMapping(path = "{projectId}")
    @Operation(summary = "取最新规划书", description = "按 projectId 取最新版本 Plan（含 status/version/content）")
    ResultData<PlanDto> getLatest(@PathVariable("projectId") String projectId);

    @PutMapping(path = "{projectId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "编辑规划书", description = "编辑 content；Plan→DRAFT，关联 FeatureDesign→STALE，Project→PLANNING")
    ResultData<PlanDto> edit(@PathVariable("projectId") String projectId,
                             @RequestBody @Valid EditPlanRequest request);

    @PostMapping(path = "{projectId}/regenerate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "重新生成规划书", description = "version+1；Plan→GENERATING 起运行；关联 FeatureDesign→STALE")
    ResultData<PlanDto> regenerate(@PathVariable("projectId") String projectId,
                                   @RequestBody @Valid RegeneratePlanRequest request);

    @PostMapping(path = "{projectId}/confirm")
    @Operation(summary = "确认规划书", description = "Plan→CONFIRMED；批量起 FeatureDesign 智能体；Project→DESIGNING")
    ResultData<Void> confirm(@PathVariable("projectId") String projectId);

    @GetMapping(path = "{projectId}/history")
    @Operation(summary = "规划书历史版本", description = "按 projectId 返回全部历史版本")
    ResultData<List<PlanDto>> history(@PathVariable("projectId") String projectId);
}
