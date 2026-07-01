package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.IterationDto;
import com.changhong.onlinecode.dto.SpecDto;
import com.changhong.onlinecode.dto.request.ConfirmSpecRequest;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Spec 管理 API。契约 §3 端点 5/6。
 *
 * <ul>
 *   <li>#5 GET  /spec/findOne  —— BaseEntityApi.findOne</li>
 *   <li>#6 POST /spec/confirm  —— 确认 Spec 并启动迭代</li>
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
}
