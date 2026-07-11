package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.MemoryWorkspaceApi;
import com.changhong.onlinecode.dto.MemoryJobDto;
import com.changhong.onlinecode.dto.WorkspaceMemoryDto;
import com.changhong.onlinecode.dto.enums.MemoryJobTriggerSource;
import com.changhong.onlinecode.dto.enums.MemoryJobType;
import com.changhong.onlinecode.entity.MemoryJob;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.MemoryJobService;
import com.changhong.onlinecode.service.WorkspaceMemoryService;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 项目级记忆管理控制器。实现 {@link MemoryWorkspaceApi}，
 * 契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §17.2。
 *
 * <p>plain 控制器、手工 DTO 映射。投递 initialize/rebuild 创建对应 MemoryJob，由后续阶段执行器消费。</p>
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "MemoryWorkspaceApi", description = "项目级记忆管理服务")
@RequestMapping(path = MemoryWorkspaceApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class MemoryWorkspaceController implements MemoryWorkspaceApi {

    private final WorkspaceMemoryService workspaceMemoryService;
    private final MemoryJobService memoryJobService;

    public MemoryWorkspaceController(WorkspaceMemoryService workspaceMemoryService,
                                     MemoryJobService memoryJobService) {
        this.workspaceMemoryService = workspaceMemoryService;
        this.memoryJobService = memoryJobService;
    }

    @Override
    public ResultData<WorkspaceMemoryDto> current(String projectId) {
        WorkspaceMemory memory = workspaceMemoryService.recheckFreshness(projectId);
        return ResultData.success(toMemoryDto(memory));
    }

    @Override
    public ResultData<List<WorkspaceMemoryDto>> history(String projectId) {
        return ResultData.success(workspaceMemoryService.findHistory(projectId).stream()
                .map(this::toMemoryDto)
                .collect(Collectors.toList()));
    }

    @Override
    public ResultData<MemoryJobDto> initialize(String projectId) {
        return submitJob(projectId, MemoryJobType.MEMORY_INITIALIZE, MemoryJobTriggerSource.MANUAL);
    }

    @Override
    public ResultData<MemoryJobDto> rebuild(String projectId) {
        return submitJob(projectId, MemoryJobType.MEMORY_REBUILD, MemoryJobTriggerSource.MANUAL);
    }

    @Override
    public ResultData<List<MemoryJobDto>> jobs(String projectId) {
        return ResultData.success(memoryJobService.findByProject(projectId).stream()
                .map(this::toJobDto)
                .collect(Collectors.toList()));
    }

    @Override
    public ResultData<MemoryJobDto> retry(String jobId) {
        OperateResultWithData<MemoryJob> result = memoryJobService.retry(jobId);
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(toJobDto(result.getData()));
    }

    /**
     * 投递 memory job 的公共逻辑，幂等键按 §12.4 构造。
     */
    private ResultData<MemoryJobDto> submitJob(String projectId,
                                              MemoryJobType jobType,
                                              MemoryJobTriggerSource triggerSource) {
        String idempotencyKey = projectId + ":" + jobType.name() + ":" + System.currentTimeMillis();
        OperateResultWithData<MemoryJob> result = memoryJobService.submit(
                projectId, jobType, triggerSource, idempotencyKey, null, null, null, null);
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(toJobDto(result.getData()));
    }

    private WorkspaceMemoryDto toMemoryDto(WorkspaceMemory memory) {
        if (memory == null) {
            return null;
        }
        WorkspaceMemoryDto dto = new WorkspaceMemoryDto();
        dto.setId(memory.getId());
        dto.setProjectId(memory.getProjectId());
        dto.setVersion(memory.getVersion());
        dto.setStatus(memory.getStatus());
        dto.setFreshness(memory.getFreshness());
        dto.setMemorySpecVersion(memory.getMemorySpecVersion());
        dto.setMemorySeedTemplateId(memory.getMemorySeedTemplateId());
        dto.setAgentMemorySeedVersion(memory.getAgentMemorySeedVersion());
        dto.setWorkspacePath(memory.getWorkspacePath());
        dto.setAgentMemoryFingerprint(memory.getAgentMemoryFingerprint());
        dto.setProjectRuleFingerprint(memory.getProjectRuleFingerprint());
        dto.setSourceFingerprintsJson(memory.getSourceFingerprintsJson());
        dto.setNormClaimsJson(memory.getNormClaimsJson());
        dto.setRealityClaimsJson(memory.getRealityClaimsJson());
        dto.setConflictFindingsJson(memory.getConflictFindingsJson());
        dto.setWorkspaceNormsJson(memory.getWorkspaceNormsJson());
        dto.setWorkspaceSnapshotJson(memory.getWorkspaceSnapshotJson());
        dto.setFailureSummary(memory.getFailureSummary());
        dto.setFailureDetail(memory.getFailureDetail());
        dto.setGeneratedAt(memory.getGeneratedAt());
        return dto;
    }

    private MemoryJobDto toJobDto(MemoryJob job) {
        if (Objects.isNull(job)) {
            return null;
        }
        MemoryJobDto dto = new MemoryJobDto();
        dto.setId(job.getId());
        dto.setProjectId(job.getProjectId());
        dto.setRequirementId(job.getRequirementId());
        dto.setCodingTaskId(job.getCodingTaskId());
        dto.setRunId(job.getRunId());
        dto.setJobType(job.getJobType());
        dto.setStatus(job.getStatus());
        dto.setTriggerSource(job.getTriggerSource());
        dto.setPreviousWorkspaceMemoryId(job.getPreviousWorkspaceMemoryId());
        dto.setNewWorkspaceMemoryId(job.getNewWorkspaceMemoryId());
        dto.setBaseWorkspaceMemoryId(job.getBaseWorkspaceMemoryId());
        dto.setIdempotencyKey(job.getIdempotencyKey());
        dto.setPriority(job.getPriority());
        dto.setRetryCount(job.getRetryCount());
        dto.setMaxRetryCount(job.getMaxRetryCount());
        dto.setNextRetryAt(job.getNextRetryAt());
        dto.setStartedAt(job.getStartedAt());
        dto.setFinishedAt(job.getFinishedAt());
        dto.setFailureSummary(job.getFailureSummary());
        dto.setFailureDetail(job.getFailureDetail());
        return dto;
    }
}
