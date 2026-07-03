package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.FeatureDesignBuildResultDto;
import com.changhong.onlinecode.dto.IterationDto;
import com.changhong.onlinecode.dto.ProjectDto;
import com.changhong.onlinecode.dto.ProjectStateDto;
import com.changhong.onlinecode.dto.SpecDto;
import com.changhong.onlinecode.dto.request.OptimizeProjectRequest;
import com.changhong.onlinecode.dto.request.RefineSpecRequest;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 项目管理 API。契约 §3 端点 1/2/3/4/9。
 *
 * <ul>
 *   <li>#1 POST /project/save        —— BaseEntityApi.save</li>
 *   <li>#2 GET  /project/findOne     —— BaseEntityApi.findOne</li>
 *   <li>#3 POST /project/findByPage  —— FindByPageApi.findByPage</li>
 *   <li>#4 POST /project/refineSpec  —— 精炼 Spec</li>
 *   <li>#9 GET  /project/state       —— 轮询生命周期</li>
 *   <li>#26 POST /project/optimize   —— 反馈再入：增量更新 Spec → 新版本 + 新回合迭代</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = ProjectApi.PATH)
public interface ProjectApi extends BaseEntityApi<ProjectDto>, FindByPageApi<ProjectDto> {

    String PATH = "project";

    @PostMapping(path = "refineSpec", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "精炼 Spec", description = "Requirement Agent：将 Project Design 精炼为 Spec，状态流转至 SPEC_REVIEW")
    ResultData<SpecDto> refineSpec(@RequestBody @Valid RefineSpecRequest request);

    @PostMapping(path = "optimize", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "优化项目", description = "反馈再入：从 PREVIEW 携带 feedback，需求 Agent 增量更新 Spec → 新版本，开启新回合迭代（round+1），状态 → SPEC_REVIEW")
    ResultData<IterationDto> optimize(@RequestBody @Valid OptimizeProjectRequest request);

    @GetMapping(path = "state")
    @Operation(summary = "轮询项目生命周期", description = "返回项目当前生命周期状态与当前迭代 id")
    ResultData<ProjectStateDto> state(@RequestParam("id") String id);

    @PostMapping(path = "{projectId}/build", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "批量执行编码", description = "对该项目所有 CONFIRMED 且非 BUILDING 的功能设计抢占 build_status 并起编码")
    ResultData<java.util.List<FeatureDesignBuildResultDto>> build(@PathVariable("projectId") String projectId);
}
