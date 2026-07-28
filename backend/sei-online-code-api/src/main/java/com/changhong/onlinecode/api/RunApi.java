package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.RunDto;
import com.changhong.onlinecode.dto.RunUsageDto;
import com.changhong.onlinecode.dto.evidence.RunArtifactDto;
import com.changhong.onlinecode.dto.evidence.RunEvidenceDto;
import com.changhong.onlinecode.dto.evidence.RunLogFramePageDto;
import com.changhong.onlinecode.dto.evidence.RunFeedbackDto;
import com.changhong.onlinecode.dto.evidence.AgentBehaviorMemoryDto;
import com.changhong.onlinecode.dto.enums.AgentBehaviorMemoryStatus;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Run 管理 API。契约 Phase 2 §2 端点 13/14。
 *
 * <ul>
 *   <li>#13 POST /run/findByPage —— FindByPageApi.findByPage（按 logStreamKey / taskId 过滤）</li>
 *   <li>#14 GET  /run/findOne    —— BaseEntityApi.findOne（轮询状态/exitCode）</li>
 *   <li>GET /run/findUsage —— 查询单次 Run 的 token usage 详情（含原始 usage JSON）</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = RunApi.PATH)
public interface RunApi extends BaseEntityApi<RunDto>, FindByPageApi<RunDto> {

    String PATH = "run";

    @GetMapping(path = "findByCodingTask")
    @Operation(summary = "按编码任务查询 Run 历史")
    ResultData<List<RunDto>> findByCodingTask(@RequestParam("codingTaskId") String codingTaskId);

    @GetMapping(path = "findByRequirement")
    @Operation(summary = "按需求查询全部 Run 历史")
    ResultData<List<RunDto>> findByRequirement(@RequestParam("requirementId") String requirementId);

    @GetMapping(path = "findUsage")
    @Operation(summary = "查询 Run 的 token usage 详情")
    ResultData<RunUsageDto> findUsage(@RequestParam("runId") String runId);

    @GetMapping(path = "findEvidence")
    @Operation(summary = "查询 Run 最新结构化证据")
    ResultData<RunEvidenceDto> findEvidence(@RequestParam("runId") String runId);

    @GetMapping(path = "findArtifacts")
    @Operation(summary = "查询 Run 原始材料元数据")
    ResultData<List<RunArtifactDto>> findArtifacts(@RequestParam("runId") String runId);

    @GetMapping(path = "findLogFrames")
    @Operation(summary = "按持久化序号回放 Run 日志")
    ResultData<RunLogFramePageDto> findLogFrames(
            @RequestParam("runId") String runId,
            @RequestParam(value = "afterSequence", defaultValue = "0") Long afterSequence,
            @RequestParam(value = "limit", defaultValue = "500") Integer limit);

    @GetMapping(path = "findArtifactContent")
    @Operation(summary = "读取脱敏后的 Run 原始材料")
    ResultData<String> findArtifactContent(@RequestParam("artifactId") String artifactId);

    @GetMapping(path = "findFeedback")
    @Operation(summary = "查询 PM 针对交付 Run 的权威反馈")
    ResultData<RunFeedbackDto> findFeedback(@RequestParam("runId") String runId);

    @GetMapping(path = "findAppliedBehaviorMemories")
    @Operation(summary = "查询本 Run 实际使用的长期行为记忆")
    ResultData<List<AgentBehaviorMemoryDto>> findAppliedBehaviorMemories(
            @RequestParam("runId") String runId);

    @GetMapping(path = "findBehaviorMemories")
    @Operation(summary = "按项目和状态查询 Agent 长期行为记忆")
    ResultData<List<AgentBehaviorMemoryDto>> findBehaviorMemories(
            @RequestParam("projectId") String projectId,
            @RequestParam(value = "status", defaultValue = "ACTIVE")
            AgentBehaviorMemoryStatus status);

    @PostMapping(path = "updateBehaviorMemoryStatus")
    @Operation(summary = "人工激活、禁用或拒绝行为记忆")
    ResultData<AgentBehaviorMemoryDto> updateBehaviorMemoryStatus(
            @RequestParam("memoryId") String memoryId,
            @RequestParam("status") AgentBehaviorMemoryStatus status);
}
