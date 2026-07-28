package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.RunApi;
import com.changhong.onlinecode.dto.RunDto;
import com.changhong.onlinecode.dto.RunUsageDto;
import com.changhong.onlinecode.dto.evidence.RunArtifactDto;
import com.changhong.onlinecode.dto.evidence.RunEvidenceDto;
import com.changhong.onlinecode.dto.evidence.RunLogFramePageDto;
import com.changhong.onlinecode.dto.evidence.RunFeedbackDto;
import com.changhong.onlinecode.dto.evidence.AgentBehaviorMemoryDto;
import com.changhong.onlinecode.dto.enums.AgentBehaviorMemoryStatus;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.RunService;
import com.changhong.onlinecode.service.evidence.RunArtifactService;
import com.changhong.onlinecode.service.evidence.RunEvidenceService;
import com.changhong.onlinecode.service.evidence.AgentBehaviorMemoryService;
import com.changhong.onlinecode.service.review.TaskDeliveryReviewService;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.service.BaseEntityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Run 管理控制器。实现 {@link RunApi}，契约 Phase 2 §2 端点 13/14。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "RunApi", description = "运行记录服务")
@RequestMapping(path = RunApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class RunController extends BaseEntityController<Run, RunDto>
        implements RunApi {

    private final RunService service;
    private final RunEvidenceService runEvidenceService;
    private final RunArtifactService runArtifactService;
    private final TaskDeliveryReviewService taskDeliveryReviewService;
    private final AgentBehaviorMemoryService behaviorMemoryService;

    @Override
    public BaseEntityService<Run> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<RunDto>> findByPage(Search search) {
        return convertToDtoPageResult(service.findByPage(search));
    }

    @Override
    public ResultData<List<RunDto>> findByCodingTask(String codingTaskId) {
        List<Run> runs = service.findByCodingTaskId(codingTaskId);
        return ResultData.success(convertToDtos(runs));
    }

    @Override
    public ResultData<List<RunDto>> findByRequirement(String requirementId) {
        return ResultData.success(convertToDtos(service.findByRequirementId(requirementId)));
    }

    @Override
    public ResultData<RunUsageDto> findUsage(String runId) {
        RunUsageDto dto = service.findUsage(runId);
        if (dto == null) {
            return ResultData.fail("运行记录不存在: " + runId);
        }
        return ResultData.success(dto);
    }

    @Override
    public ResultData<RunEvidenceDto> findEvidence(String runId) {
        RunEvidenceDto dto = runEvidenceService.findLatestDto(runId);
        return dto == null
                ? ResultData.fail("Run 证据不存在: " + runId)
                : ResultData.success(dto);
    }

    @Override
    public ResultData<List<RunArtifactDto>> findArtifacts(String runId) {
        return ResultData.success(runArtifactService.findByRunId(runId).stream()
                .map(runArtifactService::toDto)
                .toList());
    }

    @Override
    public ResultData<RunLogFramePageDto> findLogFrames(String runId, Long afterSequence, Integer limit) {
        return ResultData.success(runArtifactService.readLogFrames(
                runId,
                afterSequence == null ? 0L : Math.max(0L, afterSequence),
                limit == null ? 500 : limit));
    }

    @Override
    public ResultData<String> findArtifactContent(String artifactId) {
        return ResultData.success(runArtifactService.readText(artifactId));
    }

    @Override
    public ResultData<RunFeedbackDto> findFeedback(String runId) {
        return taskDeliveryReviewService.findFirstByDeliveryRunId(runId)
                .map(review -> {
                    RunFeedbackDto dto = new RunFeedbackDto();
                    dto.setReviewId(review.getId());
                    dto.setDeliveryRunId(review.getDeliveryRunId());
                    dto.setEvidenceId(review.getEvidenceId());
                    dto.setDecision(review.getDecision());
                    dto.setSummary(review.getSummary());
                    dto.setRemediationBriefJson(review.getRemediationBriefJson());
                    dto.setFeedbackAppliedRunId(review.getFeedbackAppliedRunId());
                    return ResultData.success(dto);
                })
                .orElseGet(() -> ResultData.fail("该 Run 暂无 PM 权威反馈: " + runId));
    }

    @Override
    public ResultData<List<AgentBehaviorMemoryDto>> findAppliedBehaviorMemories(String runId) {
        return ResultData.success(behaviorMemoryService.findApplied(runId));
    }

    @Override
    public ResultData<List<AgentBehaviorMemoryDto>> findBehaviorMemories(
            String projectId, AgentBehaviorMemoryStatus status) {
        return ResultData.success(behaviorMemoryService.findByProject(projectId, status));
    }

    @Override
    public ResultData<AgentBehaviorMemoryDto> updateBehaviorMemoryStatus(
            String memoryId, AgentBehaviorMemoryStatus status) {
        AgentBehaviorMemoryDto dto = behaviorMemoryService.updateStatus(memoryId, status);
        return dto == null
                ? ResultData.fail("行为记忆不存在: " + memoryId)
                : ResultData.success(dto);
    }
}
