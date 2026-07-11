package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.MemoryJobDto;
import com.changhong.onlinecode.dto.WorkspaceMemoryDto;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 项目级记忆管理 API。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §17.2。
 *
 * <ul>
 *   <li>GET  /api/memory/workspace/current     —— 查询当前 WorkspaceMemory</li>
 *   <li>GET  /api/memory/workspace/history      —— 查询历史版本</li>
 *   <li>POST /api/memory/workspace/initialize   —— 初始化工作区记忆</li>
 *   <li>POST /api/memory/workspace/rebuild      —— 重建工作区记忆</li>
 *   <li>GET  /api/memory/jobs                   —— 查询项目的记忆任务</li>
 *   <li>POST /api/memory/jobs/{jobId}/retry    —— 重试记忆任务</li>
 * </ul>
 *
 * @author sei-online-code
 */
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = MemoryWorkspaceApi.PATH)
public interface MemoryWorkspaceApi {

    String PATH = "api/memory";

    @GetMapping(path = "workspace/current")
    @Operation(summary = "查询当前工作区记忆", description = "返回项目当前 CURRENT WorkspaceMemory")
    ResultData<WorkspaceMemoryDto> current(@RequestParam("projectId") String projectId);

    @GetMapping(path = "workspace/history")
    @Operation(summary = "查询工作区记忆历史", description = "返回项目全部历史版本（版本倒序）")
    ResultData<List<WorkspaceMemoryDto>> history(@RequestParam("projectId") String projectId);

    @PostMapping(path = "workspace/initialize")
    @Operation(summary = "初始化工作区记忆", description = "投递 MEMORY_INITIALIZE job")
    ResultData<MemoryJobDto> initialize(@RequestParam("projectId") String projectId);

    @PostMapping(path = "workspace/rebuild")
    @Operation(summary = "重建工作区记忆", description = "投递 MEMORY_REBUILD job")
    ResultData<MemoryJobDto> rebuild(@RequestParam("projectId") String projectId);

    @GetMapping(path = "jobs")
    @Operation(summary = "查询记忆任务", description = "返回项目全部 MemoryJob（创建倒序）")
    ResultData<List<MemoryJobDto>> jobs(@RequestParam("projectId") String projectId);

    @PostMapping(path = "jobs/{jobId}/retry")
    @Operation(summary = "重试记忆任务", description = "创建新 job 重试 FAILED 任务")
    ResultData<MemoryJobDto> retry(@PathVariable("jobId") String jobId);
}