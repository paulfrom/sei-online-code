package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.IterationDto;
import com.changhong.onlinecode.dto.SpecDto;
import com.changhong.onlinecode.dto.request.ConfirmSpecRequest;
import com.changhong.onlinecode.dto.request.RegenerateSpecRequest;
import com.changhong.sei.core.api.BaseEntityApi;
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

import java.util.List;

/**
 * Spec 管理 API。契约 §3 端点 5/6 + Phase 4 §2 端点 30。
 *
 * <ul>
 *   <li>#5  GET  /spec/findOne       —— BaseEntityApi.findOne</li>
 *   <li>#6  POST /spec/confirm       —— 确认 Spec 并启动迭代</li>
 *   <li>#30 GET  /spec/findByProject —— 项目的 Spec 版本历史（version 升序）</li>
 *   <li>#R  POST /spec/{projectId}/regenerate —— 重新生成 Spec</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = SpecApi.PATH)
public interface SpecApi extends BaseEntityApi<SpecDto> {

    String PATH = "spec";

    @PostMapping(path = "confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "确认 Spec", description = "确认 Spec 后启动迭代，项目状态流转至 DISPATCHING")
    ResultData<IterationDto> confirm(@RequestBody @Valid ConfirmSpecRequest request);

    @GetMapping(path = "findByProject")
    @Operation(summary = "Spec 版本历史", description = "返回项目的 Spec 版本历史，按 version 升序（可 diff 的不可变历史）")
    ResultData<List<SpecDto>> findByProject(@RequestParam("projectId") String projectId);

    @PostMapping(path = "{projectId}/regenerate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "重新生成 Spec", description = "从 SPEC_REVIEW 产出新版本 Spec（version+1，不可变历史）；仅 SPEC_REVIEW 状态可重生")
    ResultData<SpecDto> regenerate(@PathVariable("projectId") String projectId,
                                   @RequestBody @Valid RegenerateSpecRequest request);
}
