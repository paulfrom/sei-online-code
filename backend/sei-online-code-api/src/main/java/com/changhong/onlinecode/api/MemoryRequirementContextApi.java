package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.RequirementDesignContextDto;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 需求级设计上下文 API。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §17.3。
 *
 * <ul>
 *   <li>GET  /api/memory/requirement-context/current?requirementId=xxx  —— 查询当前上下文</li>
 *   <li>POST /api/memory/requirement-context/prepare?requirementId=xxx —— 按需准备上下文</li>
 * </ul>
 *
 * @author sei-online-code
 */
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = MemoryRequirementContextApi.PATH)
public interface MemoryRequirementContextApi {

    String PATH = "api/memory/requirement-context";

    @GetMapping(path = "current")
    @Operation(summary = "查询需求设计上下文", description = "返回 Requirement 当前 CURRENT RequirementDesignContext")
    ResultData<RequirementDesignContextDto> current(@RequestParam("requirementId") String requirementId);

    @PostMapping(path = "prepare")
    @Operation(summary = "准备需求设计上下文", description = "确保 WorkspaceMemory CURRENT 后生成 RequirementDesignContext")
    ResultData<RequirementDesignContextDto> prepare(@RequestParam("requirementId") String requirementId);
}
