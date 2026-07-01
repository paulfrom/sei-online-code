package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.IterationDto;
import com.changhong.onlinecode.dto.request.DeployIterationRequest;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 迭代管理 API。契约 §3 端点 7/8。
 *
 * <ul>
 *   <li>#7 POST /iteration/deploy   —— Deploy Agent：vite build → previewUrl</li>
 *   <li>#8 GET  /iteration/findOne  —— BaseEntityApi.findOne，轮询迭代状态/previewUrl</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = IterationApi.PATH)
public interface IterationApi extends BaseEntityApi<IterationDto> {

    String PATH = "iteration";

    @PostMapping(path = "deploy", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "部署迭代", description = "Deploy Agent：构建前端产物并生成 previewUrl，状态流转至 PREVIEW")
    ResultData<IterationDto> deploy(@RequestBody @Valid DeployIterationRequest request);
}
