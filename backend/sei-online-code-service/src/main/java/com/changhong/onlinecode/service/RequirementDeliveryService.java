package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementWorkspaceDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.enums.ExecutionEffectStatus;
import com.changhong.onlinecode.dto.enums.DeliveryMrStatus;
import com.changhong.onlinecode.dto.enums.ExecutionEffectType;
import com.changhong.onlinecode.dto.enums.MemoryJobTriggerSource;
import com.changhong.onlinecode.dto.enums.MemoryJobType;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.dto.enums.RunType;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.ExecutionEffect;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.MemoryJob;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementWorkspace;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.service.progress.EffectService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import com.changhong.sei.core.util.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Requirement 交付服务：commit/push 并创建或更新 GitLab MR。
 */
@Service
@AllArgsConstructor
@Slf4j
public class RequirementDeliveryService {

    private final RequirementDao requirementDao;
    private final ExecutionPlanDao executionPlanDao;
    private final RunDao runDao;
    private final RunNumberService runNumberService;
    private final ConfigService configService;
    private final WorkspaceManager workspaceManager;
    private final RequirementCommentService requirementCommentService;
    private final MemoryJobService memoryJobService;
    private final WorkspaceMemoryService workspaceMemoryService;
    private final EffectService effectService;
    private final GitApi gitApi;
    private final ProjectDao projectDao;
    private final RequirementWorkspaceDao requirementWorkspaceDao;

    @Transactional(rollbackFor = Exception.class)
    public void deliver(String requirementId, String executionPlanId) {
        deliver(requirementId, executionPlanId, TriggerSource.AUTO, false);
    }

    private void deliver(String requirementId, String executionPlanId, TriggerSource triggerSource,
                         boolean useCurrentWorkspaceBranch) {
        Requirement requirement = requirementDao.findOne(requirementId);
        ExecutionPlan plan = executionPlanDao.findOne(executionPlanId);
        if (requirement == null || plan == null) {
            return;
        }

        Run run = new Run();
        run.setRunType(RunType.SYSTEM);
        run.setRequirementId(requirementId);
        run.setLoopId(requirement.getActiveLoopId());
        run.setTriggerSource(triggerSource);
        run.setState(RunState.RUNNING);
        run.setStartedDate(new java.util.Date());
        runNumberService.assign(run);
        runDao.save(run);

        try {
            DeliveryResult result = doDeliver(requirement, plan, useCurrentWorkspaceBranch);
            run.setState(RunState.SUCCEEDED);
            run.setTerminalReason(RunTerminalReason.SUCCEEDED);
            run.setFinishedDate(new java.util.Date());
            runDao.save(run);
            requirement.setDeliveryBranch(result.branch());
            requirement.setDeliveryCommitHash(result.commitHash());
            requirement.setDeliveryTargetBranch(result.targetBranch());
            requirement.setDeliveryMrUrl(result.mrUrl());
            requirement.setDeliveryMrIid(result.mrIid());
            requirement.setDeliveryMrStatus(DeliveryMrStatus.OPEN);
            requirement.setDeliveryMergedAt(null);
            requirement.setDeliveryMergeCommitHash(null);
            requirement.setAutomationStatus(RequirementAutomationStatus.COMPLETED);
            requirementDao.save(requirement);
            com.changhong.onlinecode.entity.RequirementComment validationComment =
                    latestComment(requirementId, RequirementCommentType.VALIDATION_RESULT);
            requirementCommentService.append(requirementId, requirement.getActiveLoopId(),
                    RequirementCommentAuthorType.SYSTEM, "delivery", result.created()
                            ? RequirementCommentType.MR_CREATED : RequirementCommentType.MR_UPDATED,
                    (result.created() ? "GitLab MR 已创建：" : "GitLab MR 已更新：") + result.mrUrl(),
                    buildSuccessMetadata(result.branch(), result.commitHash(), result.targetBranch(),
                            result.mrUrl(), run.getId(),
                            validationComment == null ? null : validationComment.getContent()));
            submitDeliveryMemoryJob(requirement, plan, result, run);
        } catch (Exception e) {
            log.warn("requirement delivery failed requirementId={}", requirementId, e);
            run.setState(RunState.FAILED);
            run.setTerminalReason(RunTerminalReason.FAILED);
            run.setFailureSummary("GitLab MR 交付失败");
            run.setFailureReason(e.getMessage());
            run.setFinishedDate(new java.util.Date());
            runDao.save(run);
            requirement.setAutomationStatus(RequirementAutomationStatus.WAITING_HUMAN);
            requirementDao.save(requirement);
            requirementCommentService.append(requirementId, requirement.getActiveLoopId(),
                    RequirementCommentAuthorType.SYSTEM, "delivery", RequirementCommentType.MR_FAILED,
                    "GitLab MR 交付失败：" + e.getMessage(),
                    toJson(Map.of("runId", run.getId(), "failureReason",
                            Objects.toString(e.getMessage(), ""))));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Requirement retry(String requirementId) {
        return submitLatest(requirementId, false, true);
    }

    /** Manually submit current uncommitted workspace changes without restricting the plan status. */
    @Transactional(rollbackFor = Exception.class)
    public Requirement submit(String requirementId) {
        return submitLatest(requirementId, true, false);
    }

    private Requirement submitLatest(String requirementId, boolean useCurrentWorkspaceBranch,
                                     boolean requireAcceptedPlan) {
        Requirement requirement = requirementDao.findOne(requirementId);
        if (requirement == null) {
            throw new IllegalArgumentException("需求不存在: " + requirementId);
        }
        ExecutionPlan plan = executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                requirementId, requirement.getActiveLoopId());
        if (plan == null) {
            throw new IllegalStateException("当前 Loop 没有可关联的执行计划");
        }
        if (requireAcceptedPlan
                && plan.getStatus() != com.changhong.onlinecode.dto.enums.ExecutionPlanStatus.ACCEPTED) {
            throw new IllegalStateException("没有可手动提交的已验收执行计划");
        }
        if (requirement.getAutomationStatus() == RequirementAutomationStatus.DELIVERING) {
            throw new IllegalStateException("交付正在进行中，请勿重复提交");
        }
        if (useCurrentWorkspaceBranch) {
            validateManualSubmission(requirement);
        }
        requirement.setAutomationStatus(RequirementAutomationStatus.DELIVERING);
        requirementDao.save(requirement);
        deliver(requirementId, plan.getId(), TriggerSource.USER_ACTION, useCurrentWorkspaceBranch);
        return requirementDao.findOne(requirementId);
    }

    private void validateManualSubmission(Requirement requirement) {
        if (requirement.getStatus() == com.changhong.onlinecode.dto.enums.RequirementStatus.COMPLETED) {
            throw new IllegalStateException("需求已完成，请先重新打开需求");
        }
        List<Run> runningRuns = runDao.findByRequirementIdAndState(requirement.getId(), RunState.RUNNING);
        if (runningRuns != null && !runningRuns.isEmpty()) {
            throw new IllegalStateException("当前仍有运行中的任务，暂不能手动提交");
        }
        RequirementWorkspace workspaceRecord = requirementWorkspaceDao
                .findByProjectIdAndRequirementId(requirement.getProjectId(), requirement.getId())
                .orElse(null);
        Date now = new Date();
        if (workspaceRecord != null
                && workspaceRecord.getOwnerRunId() != null
                && workspaceRecord.getLeaseExpiresAt() != null
                && workspaceRecord.getLeaseExpiresAt().after(now)) {
            throw new IllegalStateException("工作区正在被运行占用，暂不能手动提交");
        }
        Path workspace = resolveDeliveryWorkspace(requirement, true);
        if (workspaceManager.getChangedFiles(workspace).isEmpty()) {
            throw new IllegalStateException("当前工作区没有未提交修改");
        }
    }

    private DeliveryResult doDeliver(Requirement requirement, ExecutionPlan plan,
                                     boolean useCurrentWorkspaceBranch) throws Exception {
        String gitlabApiBaseUrl = configService.resolveGitlabApiBaseUrl(null);
        String gitlabToken = configService.resolveGitlabToken(null);
        String gitlabProjectId = configService.resolveGitlabProjectId(null);
        String gitlabTargetBranch = resolveDeliveryTargetBranch(requirement);
        if (isBlank(gitlabApiBaseUrl) || isBlank(gitlabToken) || isBlank(gitlabProjectId)) {
            throw new IllegalStateException(
                    "GitLab 交付配置不完整：apiBaseUrl/token/projectId 必填，" +
                    "请通过环境变量 oc.gitlab.api-base-url / oc.gitlab.token / oc.gitlab.project-id 或平台配置页面设置");
        }
        Path workspace = resolveDeliveryWorkspace(requirement, useCurrentWorkspaceBranch);
        String branch = resolveSourceBranch(requirement, workspace, useCurrentWorkspaceBranch);
        if (isBlank(branch)) {
            throw new IllegalStateException("当前工作区处于 detached HEAD，无法作为 MR source branch");
        }
        String targetBranch = gitlabTargetBranch;

        if (!useCurrentWorkspaceBranch) {
            runCommand(workspace, "git", "checkout", "-B", branch);
        }
        runCommand(workspace, "git", "add", ".");
        if (!runCommand(workspace, "git", "diff", "--cached", "--quiet").success()) {
            runCommand(workspace, "git", "commit", "-m", "feat: deliver requirement " + shortId(requirement.getId()));
        }
        String localCommitHash = runCommand(workspace, "git", "rev-parse", "HEAD").stdout().trim();

        // EXE-006: push effect ledger（ADR-001 §5 幂等）
        String projectKey = gitlabProjectId;
        String pushEffectKey = pushEffectKey(projectKey, branch, localCommitHash);
        String pushHash = sha256(localCommitHash);
        ExecutionEffect pushEffect = effectService.findOrPrepare(
                pushEffectKey, ExecutionEffectType.PUSH, pushHash, requirement.getId(), "deliver", 0L);
        String commitHash;
        List<String> changedFiles;
        if (pushEffect.getStatus() == ExecutionEffectStatus.PREPARED) {
            try {
                GitApi.UploadResult upload = gitApi.upload(workspace, projectKey, branch, targetBranch,
                        "feat: deliver requirement " + shortId(requirement.getId()));
                commitHash = upload.commitHash();
                changedFiles = upload.changedFiles();
                effectService.markApplied(pushEffect.getId(), toJson(Map.of("branch", branch, "commitHash", commitHash)),
                        branch + "@" + commitHash);
                effectService.markConfirmed(pushEffect.getId());
            } catch (Exception e) {
                effectService.markUnknown(pushEffect.getId());
                throw e;
            }
        } else if (pushEffect.getStatus() != ExecutionEffectStatus.APPLIED
                && pushEffect.getStatus() != ExecutionEffectStatus.CONFIRMED) {
            throw new IllegalStateException("push effect 状态异常: key=" + pushEffectKey + " status=" + pushEffect.getStatus());
        } else {
            commitHash = gitApi.getBranchHead(projectKey, branch);
            changedFiles = List.of();
        }

        GitLabApi gitLabApi = new GitLabApi(gitlabApiBaseUrl, gitlabToken);
        Object projectId = gitlabProjectId;
        String title = "Requirement " + shortId(requirement.getId()) + ": " + requirement.getTitle();
        String description = "Automated delivery for requirement " + requirement.getId()
                + "\n\nExecutionPlan: " + plan.getId()
                + "\nLoop: " + requirement.getActiveLoopId()
                + "\nCommit: " + commitHash;

        // EXE-006: MR effect ledger（ADR-001 §5 幂等：相同 key+hash 复用 MR；不同 hash 冲突）
        String mrEffectKey = mrEffectKey(projectKey, branch, commitHash);
        String mrHash = sha256(title + "|" + description + "|" + targetBranch);
        ExecutionEffect mrEffect = effectService.findOrPrepare(
                mrEffectKey, ExecutionEffectType.MR, mrHash, requirement.getId(), "deliver", 0L);
        if (mrEffect.getStatus() == ExecutionEffectStatus.APPLIED
                || mrEffect.getStatus() == ExecutionEffectStatus.CONFIRMED) {
            // 幂等复用已有 MR URL
            return new DeliveryResult(branch, commitHash, targetBranch, mrEffect.getResultSnapshot(),
                    parseMrIid(mrEffect.getResultSnapshot()), false, changedFiles);
        }

        List<MergeRequest> opened = gitLabApi.getMergeRequestApi()
                .getMergeRequests(projectId, Constants.MergeRequestState.OPENED).stream()
                .filter(mr -> branch.equals(mr.getSourceBranch()))
                .toList();
        if (!opened.isEmpty()) {
            MergeRequest mr = opened.get(0);
            MergeRequestParams params = new MergeRequestParams()
                    .withTitle(title)
                    .withDescription(description)
                    .withTargetBranch(targetBranch);
            MergeRequest updated = gitLabApi.getMergeRequestApi()
                    .updateMergeRequest(projectId, mr.getIid(), params);
            effectService.markApplied(mrEffect.getId(), updated.getWebUrl(), String.valueOf(mr.getIid()));
            effectService.markConfirmed(mrEffect.getId());
            return new DeliveryResult(branch, commitHash, targetBranch, updated.getWebUrl(),
                    updated.getIid(), false, changedFiles);
        }

        if (!useCurrentWorkspaceBranch) {
            boolean closedOrMergedExists = gitLabApi.getMergeRequestApi()
                    .getMergeRequests(projectId).stream()
                    .anyMatch(mr -> branch.equals(mr.getSourceBranch()));
            if (closedOrMergedExists) {
                throw new IllegalStateException("该 source branch 已存在关闭或合并的 MR，首版交付不复用该分支");
            }
        }

        MergeRequest created = gitLabApi.getMergeRequestApi()
                .createMergeRequest(projectId, branch, targetBranch, title, description, null);
        effectService.markApplied(mrEffect.getId(), created.getWebUrl(), String.valueOf(created.getIid()));
        effectService.markConfirmed(mrEffect.getId());
        return new DeliveryResult(branch, commitHash, targetBranch, created.getWebUrl(),
                created.getIid(), true, changedFiles);
    }

    /** Query GitLab for the authoritative state of the requirement's latest MR. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public DeliveryMrStatus refreshMrStatus(String requirementId) {
        Requirement requirement = requirementDao.findOne(requirementId);
        if (requirement == null) {
            throw new IllegalArgumentException("需求不存在: " + requirementId);
        }
        if (isBlank(requirement.getDeliveryMrUrl())) {
            requirement.setDeliveryMrStatus(DeliveryMrStatus.NOT_SUBMITTED);
            requirementDao.save(requirement);
            return DeliveryMrStatus.NOT_SUBMITTED;
        }
        Long iid = requirement.getDeliveryMrIid() == null
                ? parseMrIid(requirement.getDeliveryMrUrl()) : requirement.getDeliveryMrIid();
        if (iid == null) {
            throw new IllegalStateException("无法从 MR 地址解析 IID: " + requirement.getDeliveryMrUrl());
        }
        String projectId = configService.resolveGitlabProjectId(null);
        if (isBlank(projectId)) {
            throw new IllegalStateException("GitLab projectId 未配置");
        }
        try {
            MergeRequest mr = gitApi.client(null).getMergeRequestApi().getMergeRequest(projectId, iid);
            DeliveryMrStatus previous = requirement.getDeliveryMrStatus();
            DeliveryMrStatus current = mapMrStatus(mr.getState());
            requirement.setDeliveryMrIid(iid);
            requirement.setDeliveryMrStatus(current);
            if (current == DeliveryMrStatus.MERGED) {
                requirement.setDeliveryMergedAt(mr.getMergedAt() == null ? new Date() : mr.getMergedAt());
                requirement.setDeliveryMergeCommitHash(mr.getMergeCommitSha());
                if (requirement.getStatus() == RequirementStatus.PRD_CONFIRMED) {
                    requirement.setStatus(RequirementStatus.WAITING_FEEDBACK);
                }
            }
            requirementDao.save(requirement);
            if (current == DeliveryMrStatus.MERGED && previous != DeliveryMrStatus.MERGED) {
                requirementCommentService.append(requirementId, requirement.getActiveLoopId(),
                        RequirementCommentAuthorType.SYSTEM, "gitlab", RequirementCommentType.MR_MERGED,
                        "GitLab MR 已合并：" + requirement.getDeliveryMrUrl(),
                        toJson(Map.of("mrIid", iid, "mergeCommitHash",
                                Objects.toString(mr.getMergeCommitSha(), ""))));
            }
            return current;
        } catch (Exception e) {
            throw new IllegalStateException("查询 GitLab MR 状态失败: " + e.getMessage(), e);
        }
    }

    private DeliveryMrStatus mapMrStatus(String state) {
        if (state == null) {
            return DeliveryMrStatus.OPEN;
        }
        return switch (state.trim().toLowerCase()) {
            case "merged" -> DeliveryMrStatus.MERGED;
            case "closed" -> DeliveryMrStatus.CLOSED;
            default -> DeliveryMrStatus.OPEN;
        };
    }

    private Long parseMrIid(String mrUrl) {
        if (isBlank(mrUrl)) {
            return null;
        }
        String path = java.net.URI.create(mrUrl.trim()).getPath();
        String[] parts = path == null ? new String[0] : path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isBlank()) {
                try {
                    return Long.valueOf(parts[i]);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Path resolveDeliveryWorkspace(Requirement requirement, boolean useCurrentWorkspaceBranch) {
        if (!useCurrentWorkspaceBranch) {
            return workspaceManager.resolveRequirementWorkspace(requirement.getProjectId(), requirement.getId());
        }
        RequirementWorkspace workspace = requirementWorkspaceDao
                .findByProjectIdAndRequirementId(requirement.getProjectId(), requirement.getId())
                .orElseThrow(() -> new IllegalStateException("需求工作区不存在，请先刷新工作区"));
        if (isBlank(workspace.getWorkspacePath())) {
            throw new IllegalStateException("需求工作区路径为空，请先刷新工作区");
        }
        Path path = Path.of(workspace.getWorkspacePath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException("需求工作区不存在: " + path);
        }
        return path;
    }

    private String resolveSourceBranch(Requirement requirement, Path workspace,
                                       boolean useCurrentWorkspaceBranch) {
        return useCurrentWorkspaceBranch ? workspaceManager.getCurrentBranch(workspace) : branchName(requirement);
    }

    private String resolveDeliveryTargetBranch(Requirement requirement) {
        Project project = projectDao.findOne(requirement.getProjectId());
        if (project != null && project.getDeliveryTargetBranch() != null
                && !project.getDeliveryTargetBranch().isBlank()) {
            return project.getDeliveryTargetBranch().trim();
        }
        return configService.resolveGitlabTargetBranch(null);
    }

    private String pushEffectKey(String projectKey, String branch, String commitHash) {
        return "push:" + projectKey + ":" + branch + ":" + commitHash;
    }

    private String mrEffectKey(String projectKey, String branch, String commitHash) {
        return "mr:" + projectKey + ":" + branch + ":" + commitHash;
    }

    private void submitDeliveryMemoryJob(Requirement requirement,
                                         ExecutionPlan plan,
                                         DeliveryResult result,
                                         Run run) {
        try {
            WorkspaceMemory current = workspaceMemoryService.findCurrent(requirement.getProjectId());
            String baseMemoryId = current == null ? null : current.getId();
            String idempotencyKey = requirement.getProjectId() + ":" + requirement.getId()
                    + ":" + plan.getId() + ":" + result.commitHash();
            OperateResultWithData<MemoryJob> submitted = memoryJobService.submit(
                    requirement.getProjectId(),
                    MemoryJobType.MEMORY_UPDATE_AFTER_REQUIREMENT_DELIVERY,
                    MemoryJobTriggerSource.REQUIREMENT_DELIVERED,
                    idempotencyKey,
                    requirement.getId(),
                    plan.getId(),
                    run.getId(),
                    baseMemoryId);
            if (submitted.successful() && submitted.getData() != null) {
                MemoryJob job = submitted.getData();
                job.setLoopId(requirement.getActiveLoopId());
                com.changhong.onlinecode.entity.RequirementComment validationComment =
                        latestComment(requirement.getId(), RequirementCommentType.VALIDATION_RESULT);
                com.changhong.onlinecode.entity.RequirementComment acceptanceComment =
                        latestComment(requirement.getId(), RequirementCommentType.ACCEPTANCE);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("requirementId", requirement.getId());
                payload.put("loopId", requirement.getActiveLoopId());
                payload.put("executionPlanId", plan.getId());
                payload.put("mrUrl", result.mrUrl());
                payload.put("branch", result.branch());
                payload.put("commitHash", result.commitHash());
                payload.put("changedFiles", result.changedFiles());
                payload.put("finalValidationResult", validationComment == null ? null : validationComment.getContent());
                payload.put("validationCommentId", validationComment == null ? null : validationComment.getId());
                payload.put("acceptanceCommentId", acceptanceComment == null ? null : acceptanceComment.getId());
                job.setPayloadJson(toJson(payload));
                memoryJobService.save(job);
            }
            if (submitted.notSuccessful()) {
                requirementCommentService.append(requirement.getId(), requirement.getActiveLoopId(),
                        RequirementCommentAuthorType.SYSTEM, "memory",
                        RequirementCommentType.MEMORY_UPDATE_FAILED,
                        "Requirement 交付后记忆更新任务提交失败：" + submitted.getMessage(),
                        toJson(Map.of("mrUrl", result.mrUrl())));
            }
        } catch (Exception e) {
            requirementCommentService.append(requirement.getId(), requirement.getActiveLoopId(),
                    RequirementCommentAuthorType.SYSTEM, "memory",
                    RequirementCommentType.MEMORY_UPDATE_FAILED,
                    "Requirement 交付后记忆更新任务提交异常：" + e.getMessage(), null);
        }
    }

    private com.changhong.onlinecode.entity.RequirementComment latestComment(
            String requirementId, RequirementCommentType type) {
        return requirementCommentService.findByRequirementId(requirementId).stream()
                .filter(comment -> comment.getCommentType() == type)
                .reduce((left, right) -> right)
                .orElse(null);
    }

    private String toJson(Object value) {
        try {
            return JsonUtils.mapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("交付记忆载荷序列化失败", e);
        }
    }

    private String buildSuccessMetadata(String branch, String commitHash, String targetBranch,
                                        String mrUrl, String runId, String validationSummary) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("branch", branch);
        metadata.put("commitHash", commitHash);
        metadata.put("targetBranch", targetBranch);
        metadata.put("mrUrl", mrUrl);
        metadata.put("runId", runId);
        metadata.put("validationSummary", validationSummary);
        return toJson(metadata);
    }

    private CommandResult runCommand(Path cwd, String... command) throws Exception {
        Instant start = Instant.now();
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        CommandResult result = new CommandResult(code, out, Duration.between(start, Instant.now()));
        if (code != 0 && !("git".equals(command[0]) && "diff".equals(command[1]))) {
            throw new IllegalStateException(String.join(" ", command) + " failed: " + out);
        }
        return result;
    }

    private String branchName(Requirement requirement) {
        if (!isBlank(requirement.getDeliveryBranch())) {
            return requirement.getDeliveryBranch();
        }
        if (!isBlank(requirement.getRequirementNo())) {
            return "feature/" + safeBranchSegment(requirement.getRequirementNo());
        }
        return "feature/req-" + shortId(requirement.getId()) + "-" + shortId(requirement.getActiveLoopId());
    }

    private String safeBranchSegment(String value) {
        String safe = value == null ? "" : value.trim().replaceAll("[^a-zA-Z0-9._-]", "-");
        return safe.isBlank() ? "unknown" : safe;
    }

    private String shortId(String id) {
        if (id == null || id.isBlank()) {
            return "unknown";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** EXE-006: SHA-256 哈希，用于 effect requestHash。 */
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record DeliveryResult(String branch, String commitHash, String targetBranch, String mrUrl,
                                  Long mrIid, boolean created, List<String> changedFiles) {
    }

    private record CommandResult(int exitCode, String stdout, Duration duration) {
        boolean success() {
            return exitCode == 0;
        }
    }
}
