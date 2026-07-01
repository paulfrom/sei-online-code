package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工作区解析 API（B33）。契约 Phase 5 §2 端点 33 / §3。
 *
 * <ul>
 *   <li>#33 GET /workspace/resolve?projectId= —— 解析项目工作区目录；
 *       source=CLONE|SCAFFOLD，provisioned=目录是否已存在（clone-once 复用）</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = WorkspaceApi.PATH)
public interface WorkspaceApi {

    String PATH = "workspace";

    @GetMapping(path = "resolve")
    @Operation(summary = "解析工作区", description = "解析项目工作区目录（root/projectId）；clone-once 复用已存在目录，否则按模板地址决定 CLONE/SCAFFOLD")
    ResultData<WorkspaceResolveResult> resolve(@RequestParam("projectId") String projectId);
}
