package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.PlatformConfigDto;
import com.changhong.onlinecode.dto.request.SavePlatformConfigRequest;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 平台配置 API（B33）。契约 Phase 5 §2 端点 31/32。
 *
 * <p>单例配置行，不走标准实体 CRUD：{@code get} 缺失时补建默认行、{@code save} 幂等 upsert。</p>
 *
 * <ul>
 *   <li>#31 GET  /config/get  —— 读取平台配置（缺失则创建默认单例）</li>
 *   <li>#32 POST /config/save —— upsert 平台配置</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = ConfigApi.PATH)
public interface ConfigApi {

    String PATH = "config";

    @GetMapping(path = "get")
    @Operation(summary = "读取平台配置", description = "读取单例平台配置行；缺失时创建默认单例（工作区根默认 OS 临时目录，模板地址空）")
    ResultData<PlatformConfigDto> get();

    @PostMapping(path = "save", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "保存平台配置", description = "upsert 单例平台配置行（Workspace Root + Template GitLab URL）")
    ResultData<PlatformConfigDto> save(@RequestBody @Valid SavePlatformConfigRequest request);
}
