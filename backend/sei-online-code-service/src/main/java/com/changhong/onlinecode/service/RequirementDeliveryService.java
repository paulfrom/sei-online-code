package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.MemoryJobTriggerSource;
import com.changhong.onlinecode.dto.enums.MemoryJobType;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunType;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.MemoryJob;
import com.changhong.onlinecode.entity.PlatformConfig;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Requirement 交付服务：commit/push 并创建或更新 GitLab MR。
 */
@Service
public class RequirementDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequirementDeliveryService.class);

    private final RequirementDao requirementDao;
    private final ExecutionPlanDao executionPlanDao;
    private final RunDao runDao;
    private final ConfigService configService;
    private final WorkspaceManager workspaceManager;
    private final RequirementCommentService requirementCommentService;
    private final MemoryJobService memoryJobService;
    private final WorkspaceMemoryService workspaceMemoryService;

    public RequirementDeliveryService(RequirementDao requirementDao,
                                      ExecutionPlanDao executionPlanDao,
                                      RunDao runDao,
                                      ConfigService configService,
                                      WorkspaceManager workspaceManager,
                                      RequirementCommentService requirementCommentService,
                                      MemoryJobService memoryJobService,
                                      WorkspaceMemoryService workspaceMemoryService) {
        this.requirementDao = requirementDao;
        this.executionPlanDao = executionPlanDao;
        this.runDao = runDao;
        this.configService = configService;
        this.workspaceManager = workspaceManager;
        this.requirementCommentService = requirementCommentService;
        this.memoryJobService = memoryJobService;
        this.workspaceMemoryService = workspaceMemoryService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deliver(String requirementId, String executionPlanId) {
        Requirement requirement = requirementDao.findOne(requirementId);
        ExecutionPlan plan = executionPlanDao.findOne(executionPlanId);
        if (requirement == null || plan == null) {
            return;
        }

        Run run = new Run();
        run.setRequirementId(requirementId);
        run.setLoopId(requirement.getActiveLoopId());
        run.setRunType(RunType.DELIVERY);
        run.setTriggerSource(TriggerSource.AUTO);
        run.setState(RunState.RUNNING);
        run.setStartedDate(new java.util.Date());
        runDao.save(run);

        try {
            DeliveryResult result = doDeliver(requirement, plan);
            run.setState(RunState.SUCCEEDED);
            run.setFinishedDate(new java.util.Date());
            runDao.save(run);
            requirement.setDeliveryBranch(result.branch());
            requirement.setDeliveryCommitHash(result.commitHash());
            requirement.setDeliveryTargetBranch(result.targetBranch());
            requirement.setDeliveryMrUrl(result.mrUrl());
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
            LOGGER.warn("requirement delivery failed requirementId={}", requirementId, e);
            run.setState(RunState.FAILED);
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
        Requirement requirement = requirementDao.findOne(requirementId);
        if (requirement == null) {
            throw new IllegalArgumentException("需求不存在: " + requirementId);
        }
        ExecutionPlan plan = executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                requirementId, requirement.getActiveLoopId());
        if (plan == null || plan.getStatus() != com.changhong.onlinecode.dto.enums.ExecutionPlanStatus.ACCEPTED) {
            throw new IllegalStateException("没有可重试交付的已验收执行计划");
        }
        requirement.setAutomationStatus(RequirementAutomationStatus.DELIVERING);
        requirementDao.save(requirement);
        deliver(requirementId, plan.getId());
        return requirementDao.findOne(requirementId);
    }

    private DeliveryResult doDeliver(Requirement requirement, ExecutionPlan plan) throws Exception {
        PlatformConfig config = configService.get();
        if (isBlank(config.getGitlabApiBaseUrl()) || isBlank(config.getGitlabToken())
                || isBlank(config.getGitlabProjectId())) {
            throw new IllegalStateException("GitLab 交付配置不完整：apiBaseUrl/token/projectId 必填");
        }
        WorkspaceResolveResult resolved = workspaceManager.resolve(requirement.getProjectId());
        if (resolved == null || isBlank(resolved.getPath())) {
            throw new IllegalStateException("无法解析工作区");
        }
        Path workspace = Path.of(resolved.getPath());
        String branch = branchName(requirement);
        String targetBranch = isBlank(config.getGitlabTargetBranch()) ? "main" : config.getGitlabTargetBranch();

        runCommand(workspace, "git", "checkout", "-B", branch);
        runCommand(workspace, "git", "add", ".");
        if (!runCommand(workspace, "git", "diff", "--cached", "--quiet").success()) {
            runCommand(workspace, "git", "commit", "-m", "feat: deliver requirement " + shortId(requirement.getId()));
        }
        String commitHash = runCommand(workspace, "git", "rev-parse", "HEAD").stdout().trim();
        List<String> changedFiles = runCommand(workspace, "git", "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD")
                .stdout().lines().filter(line -> !line.isBlank()).toList();
        runCommand(workspace, "git", "push", "-u", "origin", branch);

        GitLabApi gitLabApi = new GitLabApi(config.getGitlabApiBaseUrl().trim(), config.getGitlabToken().trim());
        Object projectId = config.getGitlabProjectId().trim();
        String title = "Requirement " + shortId(requirement.getId()) + ": " + requirement.getTitle();
        String description = "Automated delivery for requirement " + requirement.getId()
                + "\n\nExecutionPlan: " + plan.getId()
                + "\nLoop: " + requirement.getActiveLoopId()
                + "\nCommit: " + commitHash;

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
            return new DeliveryResult(branch, commitHash, targetBranch, updated.getWebUrl(), false, changedFiles);
        }

        boolean closedOrMergedExists = gitLabApi.getMergeRequestApi()
                .getMergeRequests(projectId).stream()
                .anyMatch(mr -> branch.equals(mr.getSourceBranch()));
        if (closedOrMergedExists) {
            throw new IllegalStateException("该 source branch 已存在关闭或合并的 MR，首版交付不复用该分支");
        }

        MergeRequest created = gitLabApi.getMergeRequestApi()
                .createMergeRequest(projectId, branch, targetBranch, title, description, null);
        return new DeliveryResult(branch, commitHash, targetBranch, created.getWebUrl(), true, changedFiles);
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
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
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
        return "feature/req-" + shortId(requirement.getId()) + "-" + shortId(requirement.getActiveLoopId());
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

    private record DeliveryResult(String branch, String commitHash, String targetBranch, String mrUrl,
                                  boolean created, List<String> changedFiles) {
    }

    private record CommandResult(int exitCode, String stdout, Duration duration) {
        boolean success() {
            return exitCode == 0;
        }
    }
}
