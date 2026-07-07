package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.FeatureDesignBuildResultDto;
import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.ProjectDto;
import com.changhong.onlinecode.dto.ProjectStateDto;
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
 *   <li>#4 POST /project/refineSpec  —— 生成概要设计（兼容旧入口名）</li>
 *   <li>#9 GET  /project/state       —— 轮询生命周期</li>
 *   <li>#26 POST /project/{projectId}/build —— 批量执行功能设计编码</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = ProjectApi.PATH)
public interface ProjectApi extends BaseEntityApi<ProjectDto>, FindByPageApi<ProjectDto> {

    String PATH = "project";

    @PostMapping(path = "refineSpec", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "生成概要设计", description = "兼容旧 refineSpec 入口：发起概要设计生成，创建 GENERATING 状态 Plan")
    ResultData<PlanDto> refineSpec(@RequestBody @Valid RefineSpecRequest request);

    @GetMapping(path = "state")
    @Operation(summary = "轮询项目生命周期", description = "返回项目当前生命周期状态与当前迭代 id")
    ResultData<ProjectStateDto> state(@RequestParam("id") String id);

    @PostMapping(path = "{projectId}/build", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "批量执行编码", description = "对该项目所有 CONFIRMED 且非 BUILDING 的功能设计抢占 build_status 并起编码")
    ResultData<java.util.List<FeatureDesignBuildResultDto>> build(@PathVariable("projectId") String projectId);
}
