package com.changhong.onlinecode.service.progress;

import com.changhong.onlinecode.dao.ExecutionCheckpointDao;
import com.changhong.onlinecode.dao.ExecutionEffectDao;
import com.changhong.onlinecode.dao.ExecutionStepDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementWorkspaceDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.RunObservationDao;
import com.changhong.onlinecode.dao.TaskExecutionDao;
import com.changhong.onlinecode.dto.enums.ExecutionEffectStatus;
import com.changhong.onlinecode.dto.progress.ExecutionCheckpointDto;
import com.changhong.onlinecode.dto.progress.ExecutionEffectDto;
import com.changhong.onlinecode.dto.progress.ExecutionProgressSnapshot;
import com.changhong.onlinecode.dto.progress.ExecutionStepDto;
import com.changhong.onlinecode.dto.progress.RecentRunDto;
import com.changhong.onlinecode.dto.progress.RequirementExecutionOverviewDto;
import com.changhong.onlinecode.dto.progress.RunObservationDto;
import com.changhong.onlinecode.dto.progress.WorkspaceStateDto;
import com.changhong.onlinecode.entity.ExecutionCheckpoint;
import com.changhong.onlinecode.entity.ExecutionEffect;
import com.changhong.onlinecode.entity.ExecutionStep;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementWorkspace;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.RunObservation;
import com.changhong.onlinecode.entity.TaskExecution;
import com.changhong.sei.core.dto.serach.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Execution 进度只读聚合查询（EXE-003）。
 *
 * <p>提供 overview 聚合与 step/checkpoint/effect/observation 分页查询；实体→DTO 映射时对敏感字段
 * （claimToken、payload、request/result snapshot、evidence 等）默认脱敏/不返回（ADR-001 §11）。
 * 权威状态来自后端账本聚合，前端不得从摘要/日志/MR 评论推断。</p>
 *
 * @author sei-online-code
 */
@Service
public class ExecutionProgressQueryService {

    /** overview.recentRuns 默认返回条数。 */
    private static final int RECENT_RUN_LIMIT = 10;

    private final RequirementWorkspaceDao requirementWorkspaceDao;
    private final TaskExecutionDao taskExecutionDao;
    private final ExecutionStepDao executionStepDao;
    private final ExecutionCheckpointDao executionCheckpointDao;
    private final ExecutionEffectDao executionEffectDao;
    private final RunObservationDao runObservationDao;
    private final RunDao runDao;
    private final ProgressService progressService;
    private final RequirementDao requirementDao;

    public ExecutionProgressQueryService(RequirementWorkspaceDao requirementWorkspaceDao,
                                         TaskExecutionDao taskExecutionDao,
                                         ExecutionStepDao executionStepDao,
                                         ExecutionCheckpointDao executionCheckpointDao,
                                         ExecutionEffectDao executionEffectDao,
                                         RunObservationDao runObservationDao,
                                         RunDao runDao,
                                         ProgressService progressService,
                                         RequirementDao requirementDao) {
        this.requirementWorkspaceDao = requirementWorkspaceDao;
        this.taskExecutionDao = taskExecutionDao;
        this.executionStepDao = executionStepDao;
        this.executionCheckpointDao = executionCheckpointDao;
        this.executionEffectDao = executionEffectDao;
        this.runObservationDao = runObservationDao;
        this.runDao = runDao;
        this.progressService = progressService;
        this.requirementDao = requirementDao;
    }

    /**
     * 需求进度聚合 overview（计划 §3 findOverview）。一致性读：workspace + 当前 Execution 快照 + 最新 checkpoint +
     * 最近 Run。MR 真实 OPEN/MERGED/CLOSED 后续由 GitLab webhook/轮询补强；当前只返回提交边界。
     */
    @Transactional(readOnly = true)
    public RequirementExecutionOverviewDto findOverview(String requirementId) {
        Date serverTime = new Date();
        Requirement requirement = requirementDao.findOne(requirementId);
        RequirementWorkspace workspace = requirementWorkspaceDao.findByRequirementId(requirementId).orElse(null);
        String executionId = resolveActiveExecutionId(requirementId, workspace);
        ExecutionProgressSnapshot snapshot = executionId == null ? null : progressService.generateSnapshot(executionId);
        ExecutionCheckpoint latestCheckpoint = executionId == null ? null
                : executionCheckpointDao.findTopByExecutionIdOrderBySequenceNoDesc(executionId).orElse(null);

        RequirementExecutionOverviewDto overview = new RequirementExecutionOverviewDto();
        TaskExecution execution = executionId == null ? null : taskExecutionDao.findOne(executionId);
        overview.setRequirementId(requirementId);
        if (requirement != null) {
            overview.setAutomationStatus(requirement.getAutomationStatus());
            overview.setActiveLoopId(requirement.getActiveLoopId());
            overview.setRevisionSeq(requirement.getRevisionSeq());
            overview.setAppliedRevisionSeq(requirement.getAppliedRevisionSeq());
            overview.setRevisionState(requirement.getRevisionState());
            overview.setRevisionFailureReason(requirement.getRevisionFailureReason());
            overview.setMrStatus(requirement.getDeliveryMrStatus() == null
                    ? (requirement.getDeliveryMrUrl() == null || requirement.getDeliveryMrUrl().isBlank()
                    ? "NOT_SUBMITTED" : "OPEN")
                    : requirement.getDeliveryMrStatus().name());
        }
        if (requirement == null) {
            overview.setActiveLoopId(workspace == null ? null : workspace.getActiveLoopId());
        }
        overview.setPlanVersion(execution == null ? null : execution.getPlanVersion());
        overview.setSnapshotVersion(workspace == null ? null : workspace.getSnapshotVersion());
        overview.setWorkspace(toWorkspaceDto(workspace, serverTime));
        if (snapshot != null) {
            overview.setStepSummary(snapshot.getStepSummary());
            overview.setCurrentStep(snapshot.getCurrentStep());
            overview.setNextAction(snapshot.getNextAction());
        }
        overview.setLatestCheckpoint(toCheckpointDto(latestCheckpoint));
        overview.setRecentRuns(toRecentRunDtos(requirementId));
        overview.setServerTime(serverTime);
        overview.setUpdatedAt(serverTime);
        overview.setStaleAfter(workspace == null ? null : workspace.getLeaseExpiresAt());
        return overview;
    }

    /** 某 Execution 当前步骤列表（计划 §3 findSteps）。 */
    @Transactional(readOnly = true)
    public List<ExecutionStepDto> findSteps(String executionId) {
        TaskExecution execution = taskExecutionDao.findOne(executionId);
        Integer planVersion = execution == null ? null : execution.getPlanVersion();
        List<ExecutionStep> steps = planVersion == null ? java.util.Collections.emptyList()
                : executionStepDao.findByExecutionIdAndPlanVersion(executionId, planVersion);
        return steps.stream().map(this::toStepDto).collect(Collectors.toList());
    }

    /** 某步骤 checkpoint 分页（计划 §3 findCheckpoints）。page 为 1 基。 */
    @Transactional(readOnly = true)
    public PageResult<ExecutionCheckpointDto> findCheckpoints(String stepId, int page, int rows) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), rows);
        return toPageResult(executionCheckpointDao.findByStepIdOrderBySequenceNoDesc(stepId, pageable).map(this::toCheckpointDto));
    }

    /** 某 Execution effect 分页（计划 §3 findEffects），status 可空。 */
    @Transactional(readOnly = true)
    public PageResult<ExecutionEffectDto> findEffects(String executionId, ExecutionEffectStatus status, int page, int rows) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), rows);
        Page<ExecutionEffect> result = status == null
                ? executionEffectDao.findByExecutionIdOrderByPreparedAtDesc(executionId, pageable)
                : executionEffectDao.findByExecutionIdAndStatusOrderByPreparedAtDesc(executionId, status, pageable);
        return toPageResult(result.map(this::toEffectDto));
    }

    /** 某 Run observation 分页（计划 §3 runObservation/findByRun）。 */
    @Transactional(readOnly = true)
    public PageResult<RunObservationDto> findObservationsByRun(String runId, int page, int rows) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), rows);
        return toPageResult(runObservationDao.findByRunIdOrderByObservedAtDesc(runId, pageable).map(this::toObservationDto));
    }

    /** Spring {@link Page} → sei-core {@link PageResult}（page 0 基→1 基）。 */
    private <T extends java.io.Serializable> PageResult<T> toPageResult(Page<T> springPage) {
        PageResult<T> result = new PageResult<>();
        result.setRows(springPage.getContent());
        result.setRecords(springPage.getTotalElements());
        result.setTotal(springPage.getTotalPages());
        result.setPage(springPage.getNumber() + 1);
        return result;
    }

    // ============================ helpers ============================

    private String resolveActiveExecutionId(String requirementId, RequirementWorkspace workspace) {
        if (workspace != null && workspace.getOwnerExecutionId() != null) {
            return workspace.getOwnerExecutionId();
        }
        return taskExecutionDao.findFirstByRequirementIdOrderByCreatedDateDesc(requirementId)
                .map(TaskExecution::getId)
                .orElse(null);
    }

    private List<RecentRunDto> toRecentRunDtos(String requirementId) {
        List<Run> runs = runDao.findByRequirementIdOrderByCreatedDateDesc(requirementId);
        return runs.stream().limit(RECENT_RUN_LIMIT).map(this::toRecentRunDto).collect(Collectors.toList());
    }

    private WorkspaceStateDto toWorkspaceDto(RequirementWorkspace workspace, Date now) {
        if (workspace == null) {
            return null;
        }
        WorkspaceStateDto dto = new WorkspaceStateDto();
        dto.setBranch(workspace.getBranchName());
        dto.setCurrentHead(workspace.getCurrentHead());
        dto.setOwnerRunId(workspace.getOwnerRunId());
        dto.setOwnerExecutionId(workspace.getOwnerExecutionId());
        dto.setLeaseExpiresAt(workspace.getLeaseExpiresAt());
        dto.setStale(workspace.getLeaseExpiresAt() != null && workspace.getLeaseExpiresAt().before(now));
        return dto;
    }

    private RecentRunDto toRecentRunDto(Run run) {
        RecentRunDto dto = new RecentRunDto();
        dto.setRunId(run.getId());
        dto.setExecutionId(run.getExecutionId());
        dto.setRunNo(run.getRunNo());
        dto.setState(run.getState());
        dto.setTerminalReason(run.getTerminalReason());
        dto.setSummary(run.getSummary());
        dto.setStartedDate(run.getStartedDate());
        dto.setFinishedDate(run.getFinishedDate());
        return dto;
    }

    private ExecutionStepDto toStepDto(ExecutionStep step) {
        if (step == null) {
            return null;
        }
        ExecutionStepDto dto = new ExecutionStepDto();
        dto.setStepId(step.getId());
        dto.setExecutionId(step.getExecutionId());
        dto.setStepKey(step.getStepKey());
        dto.setPhase(step.getPhase());
        dto.setPlanVersion(step.getPlanVersion());
        dto.setTitle(step.getTitle());
        dto.setStatus(step.getStatus());
        dto.setOwnerRunId(step.getOwnerRunId());
        dto.setLeaseExpiresAt(step.getLeaseExpiresAt());
        dto.setAttemptCount(step.getAttemptCount());
        dto.setProgressPercent(step.getProgressPercent());
        dto.setStartedAt(step.getStartedAt());
        dto.setAppliedAt(step.getAppliedAt());
        dto.setCompletedAt(step.getCompletedAt());
        // claimToken 等敏感字段不返回
        return dto;
    }

    private ExecutionCheckpointDto toCheckpointDto(ExecutionCheckpoint checkpoint) {
        if (checkpoint == null) {
            return null;
        }
        ExecutionCheckpointDto dto = new ExecutionCheckpointDto();
        dto.setCheckpointId(checkpoint.getId());
        dto.setExecutionId(checkpoint.getExecutionId());
        dto.setStepId(checkpoint.getStepId());
        dto.setRunId(checkpoint.getRunId());
        dto.setSequenceNo(checkpoint.getSequenceNo());
        dto.setCheckpointType(checkpoint.getCheckpointType());
        dto.setGitHead(checkpoint.getGitHead());
        dto.setParentGitHead(checkpoint.getParentGitHead());
        dto.setOccurredAt(checkpoint.getCreatedDate());
        // payload/evidenceDigest 默认不展开（分页按需）
        return dto;
    }

    private ExecutionEffectDto toEffectDto(ExecutionEffect effect) {
        if (effect == null) {
            return null;
        }
        ExecutionEffectDto dto = new ExecutionEffectDto();
        dto.setEffectId(effect.getId());
        dto.setEffectKey(effect.getEffectKey());
        dto.setExecutionId(effect.getExecutionId());
        dto.setStepId(effect.getStepId());
        dto.setEffectType(effect.getEffectType());
        dto.setStatus(effect.getStatus());
        dto.setExternalReference(effect.getExternalReference());
        dto.setPreparedAt(effect.getPreparedAt());
        dto.setAppliedAt(effect.getAppliedAt());
        dto.setConfirmedAt(effect.getConfirmedAt());
        dto.setLastReconciledAt(effect.getLastReconciledAt());
        // request_snapshot/result_snapshot 默认脱敏
        return dto;
    }

    private RunObservationDto toObservationDto(RunObservation observation) {
        if (observation == null) {
            return null;
        }
        RunObservationDto dto = new RunObservationDto();
        dto.setObservationId(observation.getId());
        dto.setRunId(observation.getRunId());
        dto.setSequenceNo(observation.getSequenceNo());
        dto.setObservationType(observation.getObservationType());
        dto.setVerificationStatus(observation.getVerificationStatus());
        dto.setSourceType(observation.getSourceType());
        dto.setSourceId(observation.getSourceId());
        dto.setSummary(observation.getSummary());
        dto.setStepId(observation.getStepId());
        dto.setCheckpointId(observation.getCheckpointId());
        dto.setObservedAt(observation.getObservedAt());
        // detail/evidence_data 需授权才返回（默认不返回）
        return dto;
    }
}
