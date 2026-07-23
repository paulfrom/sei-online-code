package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.DeliveryFailureCategory;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.RequirementAutomationService;
import com.changhong.onlinecode.dto.revision.PlanPatch;
import com.changhong.onlinecode.service.revision.contract.PlanPatchValidationException;
import com.changhong.onlinecode.service.revision.contract.PlanPatchValidator;
import com.changhong.onlinecode.service.revision.contract.PlanRevisionInput;
import com.changhong.sei.core.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * PM agent 调用客户端。
 *
 * <p>负责按名称解析 {@code pm-agent}，构造 planning / acceptance prompt，
 * 调用 {@link CliRunner} 并解析结构化 JSON 结果。所有调用同步阻塞返回，
 * 调用方（{@link com.changhong.onlinecode.service.RequirementAutomationService}）
 * 在事务内决定是否回滚。</p>
 */
@Component
@AllArgsConstructor
@Slf4j
public class PmAgentClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PmAgentClient.class);
    private static final String AGENT_NAME = "pm-agent";
    private static final long PLAN_TIMEOUT_SECONDS = 600;
    private static final long ACCEPT_TIMEOUT_SECONDS = 600;
    private static final long DELIVERY_REVIEW_TIMEOUT_SECONDS = 600;
    private static final PlanPatchValidator PLAN_PATCH_VALIDATOR = new PlanPatchValidator();

    private final RunDao runDao;
    private final AgentExecutionService agentExecutionService;

    /**
     * 调用 pm-agent 生成执行计划。
     *
     * @param requirement      需求聚合根
     * @param loopId           当前 loop id
     * @param planType         计划类型
     * @param context          需求设计上下文（可为 null）
     * @param previousComments 人类/ agent 评论历史
     * @param previousPlan     上一版计划（remediation/change request 时可为 null）
     * @return 解析后的计划结果；agent 未找到、调用失败或 JSON 解析失败时返回 null
     */
    public PmPlanResult generatePlan(Requirement requirement,
                                     String loopId,
                                     ExecutionPlanType planType,
                                     RequirementDesignContext context,
                                     List<RequirementComment> previousComments,
                                     ExecutionPlan previousPlan) {
        String prompt = buildPlanningPrompt(requirement, planType, context, previousComments, previousPlan);
        log.info("pm agent prompt: {}", prompt);
        AgentExecutionResult execution = executeAgent(requirement.getProjectId(), requirement.getId(), loopId, prompt,
                context == null ? null : context.getId(),
                context == null ? null : context.getWorkspaceMemoryId(), PLAN_TIMEOUT_SECONDS);

        log.info("pm execution result: {}", execution);
        if (!execution.succeeded()) {
            settleFailedOrCancelled(execution.runId(), firstNonBlank(execution.output(),
                    execution.failureReason(), "pm-agent 调用失败"));
            return null;
        }
        if (execution.runId() == null || execution.output() == null) {
            settleFailedOrCancelled(execution.runId(), "pm-agent 调用失败、取消或无输出");
            return null;
        }

        if (isCancellationRequested(execution.runId())) {
            markRunCancelled(execution.runId());
            return null;
        }

        PmPlanResult result = parsePlanJson(execution.output(), requirement.getId(), loopId);
        if (result == null) {
            settleFailedOrCancelled(execution.runId(), "pm-agent 返回内容无法解析为有效计划 JSON");
            return null;
        }
        markRunSucceeded(execution.runId());
        return result;
    }

    /**
     * Generates a task-level incremental patch without changing the existing full-plan API.
     * The immutable input is a point-in-time snapshot, so the caller can reject this result
     * later if its revision token is no longer current.
     */
    public PlanPatch generatePlanPatch(Requirement requirement,
                                       PlanRevisionInput input,
                                       RequirementDesignContext context) {
        Objects.requireNonNull(requirement, "requirement is required");
        Objects.requireNonNull(input, "revision input is required");
        if (!Objects.equals(requirement.getId(), input.requirementId())) {
            throw new IllegalArgumentException("requirement does not match revision input");
        }

        String prompt = buildPlanPatchPrompt(input, context);
        log.info("pm agent incremental plan prompt: requirementId={}, loopId={}, revisionSeq={}",
                input.requirementId(), input.loopId(), input.revisionSeq());
        AgentExecutionResult execution = executeAgent(requirement.getProjectId(), input.requirementId(),
                input.loopId(), prompt, context == null ? null : context.getId(),
                context == null ? null : context.getWorkspaceMemoryId(), PLAN_TIMEOUT_SECONDS);

        if (!execution.succeeded()) {
            settleFailedOrCancelled(execution.runId(), firstNonBlank(execution.output(),
                    execution.failureReason(), "pm-agent 增量计划调用失败"));
            return null;
        }
        if (execution.runId() == null || execution.output() == null) {
            settleFailedOrCancelled(execution.runId(), "pm-agent 增量计划调用失败、取消或无输出");
            return null;
        }
        if (isCancellationRequested(execution.runId())) {
            markRunCancelled(execution.runId());
            return null;
        }

        PlanPatch patch = parsePlanPatchJson(execution.output(), input);
        if (patch == null) {
            settleFailedOrCancelled(execution.runId(), "pm-agent 返回内容无法解析为有效 PlanPatch JSON");
            return null;
        }
        markRunSucceeded(execution.runId());
        return patch;
    }

    /**
     * 调用 pm-agent 进行验收评审。
     *
     * @param requirement      需求聚合根
     * @param plan             当前执行计划
     * @param tasks            当前计划下的所有任务
     * @param previousComments 相关评论历史（含 DEV_RESULT、VALIDATION_RESULT）
     * @param context          需求设计上下文（可为 null）
     * @return 验收结果；失败时返回 null
     */
    public PmAcceptanceResult reviewAcceptance(Requirement requirement,
                                               ExecutionPlan plan,
                                               List<RequirementAutomationService.PlanTask> tasks,
                                               List<RequirementComment> previousComments,
                                               RequirementDesignContext context) {
        String prompt = buildAcceptancePrompt(requirement, plan, tasks, previousComments, context);
        AgentExecutionResult execution = executeAgent(requirement.getProjectId(), requirement.getId(),
                plan.getLoopId(), prompt, plan.getMemoryContextId(), plan.getWorkspaceMemoryId(),
                ACCEPT_TIMEOUT_SECONDS);
        if (!execution.succeeded()) {
            settleFailedOrCancelled(execution.runId(), firstNonBlank(execution.output(),
                    execution.failureReason(), "pm-agent 验收调用失败"));
            return null;
        }
        if (execution.runId() == null || execution.output() == null) {
            settleFailedOrCancelled(execution.runId(), "pm-agent 验收调用失败、取消或无输出");
            return null;
        }

        if (isCancellationRequested(execution.runId())) {
            markRunCancelled(execution.runId());
            return null;
        }

        PmAcceptanceResult result = parseAcceptanceJson(execution.output());
        if (result == null) {
            settleFailedOrCancelled(execution.runId(), "pm-agent 返回内容无法解析为有效验收 JSON");
            return null;
        }
        markRunSucceeded(execution.runId());
        return result;
    }

    /**
     * 调用 pm-agent 进行任务级交付审阅（方案 §4.3 / §5.1 / §5.2）。
     *
     * <p>每次任务（coding agent 或 test-agent）交付完成后调用一次。pm-agent 基于交付摘要、
     * 验收证据与必要上下文，返回结构化决策 JSON。调用失败或 JSON 解析失败返回 {@code null}，
     * 调用方据此将审阅置为 {@code WAIT_HUMAN}（方案 §11 PM 死锁控制）。</p>
     *
     * <p>本方法同步阻塞，但<strong>必须</strong>由调用方在数据库事务之外执行（见方案 §7 异步边界）。</p>
     *
     * @param requirement 需求聚合根
     * @param plan        当前执行计划（提供 memory context）
     * @param input       任务级交付审阅输入
     * @return 决策结果；agent 调用失败、取消或 JSON 解析失败时返回 null
     */
    public PmDeliveryDecision reviewDelivery(Requirement requirement,
                                             ExecutionPlan plan,
                                             DeliveryReviewInput input) {
        Objects.requireNonNull(input, "delivery review input is required");
        String prompt = buildDeliveryReviewPrompt(requirement, input);
        String projectId = requirement == null ? null : requirement.getProjectId();
        String requirementId = input.requirementId();
        String loopId = input.loopId();
        String memoryContextId = plan == null ? null : plan.getMemoryContextId();
        String workspaceMemoryId = plan == null ? null : plan.getWorkspaceMemoryId();
        AgentExecutionResult execution = executeAgent(projectId, requirementId, loopId, prompt,
                memoryContextId, workspaceMemoryId, DELIVERY_REVIEW_TIMEOUT_SECONDS);
        if (!execution.succeeded()) {
            settleFailedOrCancelled(execution.runId(), firstNonBlank(execution.output(),
                    execution.failureReason(), "pm-agent 交付审阅调用失败"));
            return null;
        }
        if (execution.runId() == null || execution.output() == null) {
            settleFailedOrCancelled(execution.runId(), "pm-agent 交付审阅调用失败、取消或无输出");
            return null;
        }
        if (isCancellationRequested(execution.runId())) {
            markRunCancelled(execution.runId());
            return null;
        }
        PmDeliveryDecision decision = parseDeliveryDecisionJson(execution.output(), input);
        if (decision == null) {
            settleFailedOrCancelled(execution.runId(), "pm-agent 交付审阅返回内容无法解析为有效决策 JSON");
            return null;
        }
        markRunSucceeded(execution.runId());
        return decision;
    }

    private AgentExecutionResult executeAgent(String projectId, String requirementId, String loopId, String prompt,
                                              String memoryContextId, String workspaceMemoryId,
                                              long timeoutSeconds) {
        AgentExecutionRequest request = new AgentExecutionRequest();
        request.setProjectId(projectId);
        request.setRequirementId(requirementId);
        request.setLogStreamKey(requirementId);
        request.setLoopId(loopId);
        request.setTriggerSource(TriggerSource.AUTO);
        request.setPrompt(prompt);
        request.setMemoryContextId(memoryContextId);
        request.setWorkspaceMemoryId(workspaceMemoryId);
        request.setTimeoutSeconds(timeoutSeconds);
        return agentExecutionService.execute(AGENT_NAME, request);
    }

    private void markRunSucceeded(String runId) {
        Run current = currentRun(runId);
        if (current.getState() == RunState.RUNNING && !Boolean.TRUE.equals(current.getCancelRequested())) {
            current.setState(RunState.SUCCEEDED);
            current.setTerminalReason(RunTerminalReason.SUCCEEDED);
            current.setFinishedDate(new Date());
            runDao.save(current);
        }
    }

    private void markRunFailed(String runId, String reason) {
        Run current = currentRun(runId);
        if (current == null) {
            LOGGER.error("pm-agent run not created: {}", reason);
            return;
        }
        if (current.getState() != RunState.RUNNING) {
            return;
        }
        current.setState(RunState.FAILED);
        current.setTerminalReason(RunTerminalReason.FAILED);
        current.setSummary(reason);
        current.setFailureReason(reason);
        current.setFinishedDate(new Date());
        runDao.save(current);
    }

    private void settleFailedOrCancelled(String runId, String reason) {
        if (isCancellationRequested(runId)) {
            markRunCancelled(runId);
        } else {
            markRunFailed(runId, reason);
        }
    }

    private boolean isCancellationRequested(String runId) {
        Run current = currentRun(runId);
        return current != null
                && (Boolean.TRUE.equals(current.getCancelRequested()) || current.getState() == RunState.CANCELLED);
    }

    private void markRunCancelled(String runId) {
        Run current = currentRun(runId);
        if (current == null) {
            return;
        }
        current.setCancelRequested(Boolean.TRUE);
        current.setState(RunState.CANCELLED);
        current.setTerminalReason(RunTerminalReason.CANCELLED);
        current.setFinishedDate(new Date());
        runDao.save(current);
    }

    private Run currentRun(String runId) {
        return runId == null ? null : runDao.findOne(runId);
    }

    private String buildPlanningPrompt(Requirement requirement,
                                       ExecutionPlanType planType,
                                       RequirementDesignContext context,
                                       List<RequirementComment> previousComments,
                                       ExecutionPlan previousPlan) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are pm-agent. Create a structured execution plan from the PRD and context below.\n\n");
        sb.append("## PRD\n").append(Objects.toString(requirement.getPrdContent(), "")).append("\n\n");
        sb.append("## Requirement\n");
        sb.append("Title: ").append(Objects.toString(requirement.getTitle(), "")).append("\n");
        sb.append("Description: ").append(Objects.toString(requirement.getDescription(), "")).append("\n\n");

        if (previousComments != null && !previousComments.isEmpty()) {
            sb.append("## Comment History\n");
            for (RequirementComment comment : previousComments) {
                sb.append("[").append(comment.getAuthorType()).append(" / ")
                        .append(comment.getCommentType()).append("] ")
                        .append(Objects.toString(comment.getAuthorName(), "")).append("\n");
                sb.append(Objects.toString(comment.getContent(), "")).append("\n\n");
            }
        }

        if (previousPlan != null && previousPlan.getPlanJson() != null) {
            sb.append("## Previous Plan (to be remediated / extended)\n")
                    .append(previousPlan.getPlanJson()).append("\n\n");
        }

        sb.append("## Context\n");
        sb.append("planType: ").append(planType.name()).append("\n");
        sb.append("memoryContextId: ").append(context == null ? "n/a" : context.getId()).append("\n");
        sb.append("workspaceMemoryId: ").append(context == null ? "n/a" : context.getWorkspaceMemoryId()).append("\n");
        sb.append("\nPlanning guidance:\n");
        sb.append("- Include acceptance / validation as explicit tasks assigned to test-agent.\n");
        sb.append("- Do not assume test-agent runs automatically after each coding task.\n");
        sb.append("- Decide validation task scope, count, and dependencies from the requirement, implementation risk, and frontend/backend coupling.\n");

        sb.append("\nReturn **only** valid JSON with this exact structure:\n");
        sb.append("{\n");
        sb.append("  \"goal\": \"string\",\n");
        sb.append("  \"tasks\": [\n");
        sb.append("    {\n");
        sb.append("      \"taskKey\": \"FE-001\",\n");
        sb.append("      \"title\": \"string\",\n");
        sb.append("      \"description\": \"string\",\n");
        sb.append("      \"agent\": \"frontend-dev-agent\" or \"backend-dev-agent\" or \"test-agent\",\n");
        sb.append("      \"area\": \"frontend\" or \"backend\" or \"full-stack\" or \"validation\",\n");
        sb.append("      \"dependsOn\": [],\n");
        sb.append("      \"fileScope\": [\"frontend/src/...\"],\n");
        sb.append("      \"acceptanceCriteria\": [\"string\"]\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"risks\": [\"string\"],\n");
        sb.append("  \"validation\": {\n");
        sb.append("    \"mode\": \"test-agent\",\n");
        sb.append("    \"guidance\": \"Validation is executed only by explicit test-agent tasks in tasks[].\"\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String buildAcceptancePrompt(Requirement requirement,
                                         ExecutionPlan plan,
                                         List<RequirementAutomationService.PlanTask> tasks,
                                         List<RequirementComment> previousComments,
                                         RequirementDesignContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are pm-agent. Review the requirement, execution plan, development results, validation reports, and decide whether to accept or request remediation.\n\n");
        sb.append("Treat VALIDATION_RESULT comments from planned test-agent tasks as primary acceptance evidence. "
                + "Development self-reports are auxiliary and must not override failed validation evidence.\n\n");
        sb.append("## PRD\n").append(Objects.toString(requirement.getPrdContent(), "")).append("\n\n");
        sb.append("## Execution Plan\n").append(Objects.toString(plan.getPlanJson(), "")).append("\n\n");

        if (tasks != null && !tasks.isEmpty()) {
            sb.append("## Tasks\n");
            for (RequirementAutomationService.PlanTask task : tasks) {
                sb.append("- ").append(task.taskKey()).append(" [").append(task.area()).append("] ")
                        .append(task.title()).append(" -> ").append(task.agent()).append("\n");
            }
            sb.append("\n");
        }

        if (previousComments != null && !previousComments.isEmpty()) {
            sb.append("## Comments (DEV_RESULT, VALIDATION_RESULT, etc.)\n");
            for (RequirementComment comment : previousComments) {
                if (comment.getCommentType() == RequirementCommentType.DEV_RESULT
                        || comment.getCommentType() == RequirementCommentType.VALIDATION_RESULT
                        || comment.getCommentType() == RequirementCommentType.HUMAN_FEEDBACK) {
                    sb.append("[").append(comment.getAuthorType()).append(" / ")
                            .append(comment.getCommentType()).append("] ")
                            .append(Objects.toString(comment.getAuthorName(), "")).append("\n");
                    sb.append(Objects.toString(comment.getContent(), "")).append("\n\n");
                }
            }
        }

        sb.append("## Context\n");
        sb.append("memoryContextId: ").append(context == null ? "n/a" : context.getId()).append("\n");
        sb.append("workspaceMemoryId: ").append(context == null ? "n/a" : context.getWorkspaceMemoryId()).append("\n");

        sb.append("\nReturn **only** valid JSON:\n");
        sb.append("{\n");
        sb.append("  \"accepted\": true or false,\n");
        sb.append("  \"summary\": \"string\",\n");
        sb.append("  \"findings\": [\"string\"],\n");
        sb.append("  \"remediationTasks\": [\n");
        sb.append("    {\n");
        sb.append("      \"taskKey\": \"FE-001\",\n");
        sb.append("      \"title\": \"string\",\n");
        sb.append("      \"description\": \"string\",\n");
        sb.append("      \"agent\": \"frontend-dev-agent\" or \"backend-dev-agent\" or \"test-agent\",\n");
        sb.append("      \"area\": \"frontend\" or \"backend\" or \"full-stack\" or \"validation\",\n");
        sb.append("      \"dependsOn\": [],\n");
        sb.append("      \"fileScope\": [\"frontend/src/...\"],\n");
        sb.append("      \"acceptanceCriteria\": [\"string\"]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String buildDeliveryReviewPrompt(Requirement requirement, DeliveryReviewInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are pm-agent. Review a single task delivery and decide what the orchestrator should do next.\n\n");
        sb.append("Decision contract:\n");
        sb.append("- APPROVE: only when the task succeeded and delivery evidence is complete.\n");
        sb.append("- RETRY: only for transient infrastructure issues or clearly correctable agent execution deviation; record retryReason.\n");
        sb.append("- REPLAN: for code defects, test failures, upstream delivery incomplete, task contract errors, or when new remediation tasks are needed.\n");
        sb.append("- WAIT_HUMAN: when you cannot decide safely, output is invalid, remediation cap reached, or a human decision is required.\n");
        sb.append("- For a FAILED or VALIDATION_FAILED delivery, APPROVE is forbidden.\n\n");
        sb.append("## Requirement\n");
        sb.append("Title: ").append(requirement == null ? "" : Objects.toString(requirement.getTitle(), "")).append("\n");
        sb.append("Description: ").append(requirement == null ? "" : Objects.toString(requirement.getDescription(), "")).append("\n\n");

        sb.append("## Delivery Under Review\n");
        sb.append("taskKey: ").append(Objects.toString(input.taskKey(), "")).append("\n");
        sb.append("taskTitle: ").append(Objects.toString(input.taskTitle(), "")).append("\n");
        sb.append("taskDescription: ").append(Objects.toString(input.taskDescription(), "")).append("\n");
        sb.append("area: ").append(Objects.toString(input.area(), "")).append("\n");
        sb.append("assignedAgent: ").append(Objects.toString(input.assignedAgent(), "")).append("\n");
        sb.append("deliverySucceeded: ").append(input.deliverySucceeded()).append("\n");
        sb.append("taskType: ").append(Objects.toString(input.taskType(), "")).append("\n");
        if (input.acceptanceCriteria() != null && !input.acceptanceCriteria().isEmpty()) {
            sb.append("acceptanceCriteria:\n");
            for (String criterion : input.acceptanceCriteria()) {
                sb.append("- ").append(criterion).append("\n");
            }
        }
        sb.append("\n## Delivery Evidence\n");
        sb.append(Objects.toString(input.deliveryEvidence(), "n/a")).append("\n\n");

        if (input.relatedComments() != null && !input.relatedComments().isEmpty()) {
            sb.append("## Related Comments (DEV_RESULT / VALIDATION_RESULT)\n");
            for (RequirementComment comment : input.relatedComments()) {
                if (comment.getCommentType() == RequirementCommentType.DEV_RESULT
                        || comment.getCommentType() == RequirementCommentType.VALIDATION_RESULT) {
                    sb.append("[").append(comment.getAuthorType()).append(" / ")
                            .append(comment.getCommentType()).append("] ")
                            .append(Objects.toString(comment.getAuthorName(), "")).append("\n");
                    sb.append(Objects.toString(comment.getContent(), "")).append("\n\n");
                }
            }
        }

        sb.append("\nReturn **only** valid JSON with this exact structure:\n");
        sb.append("{\n");
        sb.append("  \"decision\": \"APPROVE | RETRY | REPLAN | WAIT_HUMAN\",\n");
        sb.append("  \"summary\": \"string\",\n");
        sb.append("  \"failureCategory\": \"NONE | TRANSIENT_INFRA | DELIVERY_INCOMPLETE | VALIDATION_FAILED | UPSTREAM_INCOMPLETE | PLAN_DEFECT\",\n");
        sb.append("  \"findings\": [\"string\"],\n");
        sb.append("  \"retryReason\": \"required only when decision is RETRY\",\n");
        sb.append("  \"remediationTasks\": [\n");
        sb.append("    {\n");
        sb.append("      \"taskKey\": \"string\",\n");
        sb.append("      \"title\": \"string\",\n");
        sb.append("      \"description\": \"string\",\n");
        sb.append("      \"agent\": \"frontend-dev-agent\" or \"backend-dev-agent\" or \"test-agent\",\n");
        sb.append("      \"area\": \"frontend\" or \"backend\" or \"full-stack\" or \"validation\",\n");
        sb.append("      \"dependsOn\": [],\n");
        sb.append("      \"fileScope\": [\"frontend/src/...\"],\n");
        sb.append("      \"acceptanceCriteria\": [\"string\"]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private PmDeliveryDecision parseDeliveryDecisionJson(String json, DeliveryReviewInput input) {
        try {
            JsonNode root = JsonUtils.mapper().readTree(extractJsonObject(json));
            TaskDeliveryReviewDecision decision = parseDecision(root.path("decision").asText(""));
            if (decision == null) {
                LOGGER.warn("pm-agent delivery review has invalid decision: {}", json);
                return null;
            }
            String summary = root.path("summary").asText("");
            DeliveryFailureCategory category = parseFailureCategory(root.path("failureCategory").asText(""));
            List<String> findings = readStringList(root.path("findings"));
            String retryReason = root.path("retryReason").asText(null);
            if (decision == TaskDeliveryReviewDecision.RETRY
                    && (retryReason == null || retryReason.isBlank())) {
                LOGGER.warn("pm-agent delivery review RETRY missing retryReason: {}", json);
                return null;
            }

            List<RequirementAutomationService.PlanTask> remediationTasks = new ArrayList<>();
            JsonNode remediationNode = root.path("remediationTasks");
            if (remediationNode.isArray()) {
                for (JsonNode taskNode : remediationNode) {
                    String taskKey = taskNode.path("taskKey").asText(null);
                    String title = taskNode.path("title").asText(null);
                    String description = taskNode.path("description").asText("");
                    String agent = taskNode.path("agent").asText(null);
                    String area = taskNode.path("area").asText(null);
                    List<String> dependsOn = readStringList(taskNode.path("dependsOn"));
                    List<String> fileScope = readStringList(taskNode.path("fileScope"));
                    List<String> acceptanceCriteria = readStringList(taskNode.path("acceptanceCriteria"));
                    if (taskKey == null || title == null || agent == null || area == null) {
                        LOGGER.warn("pm-agent remediation task missing required fields: {}", taskNode);
                        return null;
                    }
                    remediationTasks.add(new RequirementAutomationService.PlanTask(
                            taskKey, title, description, agent, area, dependsOn, fileScope, acceptanceCriteria));
                }
            }

            if (decision == TaskDeliveryReviewDecision.REPLAN) {
                if (remediationTasks.isEmpty()) {
                    LOGGER.warn("pm-agent delivery review REPLAN without remediationTasks: {}", json);
                    return null;
                }
                if (!isValidTaskGraph(remediationTasks)) {
                    LOGGER.warn("pm-agent delivery review REPLAN has invalid agent assignment or DAG");
                    return null;
                }
                if (remediationTasks.stream().noneMatch(task -> "test-agent".equals(task.agent()))) {
                    LOGGER.warn("pm-agent delivery review REPLAN has no independent test-agent task");
                    return null;
                }
            }

            return new PmDeliveryDecision(decision, summary, category, findings, retryReason, remediationTasks);
        } catch (Exception e) {
            LOGGER.warn("pm-agent delivery review JSON parse failed: requirementId={}, codingTaskId={}",
                    input.requirementId(), input.codingTaskId(), e);
            return null;
        }
    }

    private TaskDeliveryReviewDecision parseDecision(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return TaskDeliveryReviewDecision.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private DeliveryFailureCategory parseFailureCategory(String text) {
        if (text == null || text.isBlank()) {
            return DeliveryFailureCategory.NONE;
        }
        try {
            return DeliveryFailureCategory.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DeliveryFailureCategory.NONE;
        }
    }

    private String buildPlanPatchPrompt(PlanRevisionInput input, RequirementDesignContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are pm-agent. Revise the active execution plan incrementally in the same loop.\n");
        sb.append("Preserve completed and running work whenever the user's comments do not affect it. ")
                .append("Return a task-level patch, not a replacement full plan.\n\n");
        sb.append("## Revision Snapshot\n");
        try {
            sb.append(JsonUtils.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(input));
        } catch (Exception e) {
            throw new IllegalArgumentException("revision input cannot be serialized", e);
        }
        sb.append("\n\n## Context\n");
        sb.append("memoryContextId: ").append(context == null ? "n/a" : context.getId()).append("\n");
        sb.append("workspaceMemoryId: ").append(context == null ? "n/a" : context.getWorkspaceMemoryId()).append("\n\n");
        sb.append("Rules:\n");
        sb.append("- Use only KEEP, AMEND, ADD, SUPERSEDE, or REVALIDATE.\n");
        sb.append("- KEEP preserves an existing task and has no replacement task fields.\n");
        sb.append("- SUPERSEDE retires an existing task and has no replacement task fields.\n");
        sb.append("- AMEND reuses an existing task's results but defines its replacement task.\n");
        sb.append("- ADD defines a new task and must not have sourceTaskId.\n");
        sb.append("- REVALIDATE defines a test-agent task for an existing source task.\n");
        sb.append("- Every non-ADD operation must use a sourceTaskId present in tasks.\n");
        sb.append("- Every dependency must refer to a taskKey produced by KEEP, AMEND, ADD, or REVALIDATE.\n");
        sb.append("- Dependencies must form a DAG.\n");
        sb.append("- frontend file scopes start with frontend/; backend scopes start with backend/.\n");
        sb.append("- AMEND and ADD use frontend-dev-agent/frontend or backend-dev-agent/backend.\n");
        sb.append("- Validation uses test-agent and an explicit non-empty frontend/ or backend/ file scope.\n\n");
        sb.append("Return only valid JSON with this exact structure:\n");
        sb.append("{\n");
        sb.append("  \"requirementId\": \"").append(input.requirementId()).append("\",\n");
        sb.append("  \"loopId\": \"").append(input.loopId()).append("\",\n");
        sb.append("  \"revisionSeq\": ").append(input.revisionSeq()).append(",\n");
        sb.append("  \"basePlanId\": \"").append(input.basePlanId()).append("\",\n");
        sb.append("  \"basePlanVersion\": ").append(input.basePlanVersion()).append(",\n");
        sb.append("  \"summary\": \"string\",\n");
        sb.append("  \"operations\": [{\n");
        sb.append("    \"taskKey\": \"string\",\n");
        sb.append("    \"action\": \"KEEP|AMEND|ADD|SUPERSEDE|REVALIDATE\",\n");
        sb.append("    \"sourceTaskId\": \"existing task id or null for ADD\",\n");
        sb.append("    \"title\": \"required for AMEND/ADD/REVALIDATE, otherwise null\",\n");
        sb.append("    \"description\": \"required for AMEND/ADD/REVALIDATE, otherwise null\",\n");
        sb.append("    \"area\": \"frontend|backend|full-stack|validation\",\n");
        sb.append("    \"fileScope\": [\"backend/...\"],\n");
        sb.append("    \"dependsOn\": [\"output taskKey\"],\n");
        sb.append("    \"assignedAgent\": \"frontend-dev-agent|backend-dev-agent|test-agent\",\n");
        sb.append("    \"reason\": \"string\"\n");
        sb.append("  }]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private PmPlanResult parsePlanJson(String json, String requirementId, String loopId) {
        try {
            JsonNode root = JsonUtils.mapper().readTree(extractJsonObject(json));
            String goal = root.path("goal").asText("");
            List<String> risks = readStringList(root.path("risks"));
            List<ValidationCommand> validationCommands = readValidationCommands(root.path("validation"));

            JsonNode tasksNode = root.path("tasks");
            if (!tasksNode.isArray() || tasksNode.isEmpty()) {
                LOGGER.warn("pm-agent plan has no tasks: requirementId={}", requirementId);
                return null;
            }

            List<RequirementAutomationService.PlanTask> tasks = new ArrayList<>();
            for (JsonNode taskNode : tasksNode) {
                String taskKey = taskNode.path("taskKey").asText(null);
                String title = taskNode.path("title").asText(null);
                String description = taskNode.path("description").asText("");
                String agent = taskNode.path("agent").asText(null);
                String area = taskNode.path("area").asText(null);
                List<String> dependsOn = readStringList(taskNode.path("dependsOn"));
                List<String> fileScope = readStringList(taskNode.path("fileScope"));
                List<String> acceptanceCriteria = readStringList(taskNode.path("acceptanceCriteria"));

                if (taskKey == null || title == null || agent == null || area == null) {
                    LOGGER.warn("pm-agent task missing required fields: {}", taskNode);
                    return null;
                }
                tasks.add(new RequirementAutomationService.PlanTask(
                        taskKey, title, description, agent, area, dependsOn, fileScope, acceptanceCriteria));
            }

            if (!isValidTaskGraph(tasks)) {
                LOGGER.warn("pm-agent plan contains invalid agent assignment or DAG: requirementId={}", requirementId);
                return null;
            }
            return new PmPlanResult(goal, tasks, risks, validationCommands);
        } catch (Exception e) {
            LOGGER.warn("pm-agent plan JSON parse failed: requirementId={}, loopId={}", requirementId, loopId, e);
            return null;
        }
    }

    private PmAcceptanceResult parseAcceptanceJson(String json) {
        try {
            JsonNode root = JsonUtils.mapper().readTree(extractJsonObject(json));
            boolean accepted = root.path("accepted").asBoolean(false);
            String summary = root.path("summary").asText("");
            List<String> findings = readStringList(root.path("findings"));

            List<RequirementAutomationService.PlanTask> remediationTasks = new ArrayList<>();
            JsonNode remediationNode = root.path("remediationTasks");
            if (remediationNode.isArray()) {
                for (JsonNode taskNode : remediationNode) {
                    String taskKey = taskNode.path("taskKey").asText(null);
                    String title = taskNode.path("title").asText(null);
                    String description = taskNode.path("description").asText("");
                    String agent = taskNode.path("agent").asText(null);
                    String area = taskNode.path("area").asText(null);
                    List<String> dependsOn = readStringList(taskNode.path("dependsOn"));
                    List<String> fileScope = readStringList(taskNode.path("fileScope"));
                    List<String> acceptanceCriteria = readStringList(taskNode.path("acceptanceCriteria"));
                    if (taskKey == null || title == null || agent == null || area == null) {
                        LOGGER.warn("pm-agent remediation task missing required fields: {}", taskNode);
                        return null;
                    }
                    remediationTasks.add(new RequirementAutomationService.PlanTask(
                            taskKey, title, description, agent, area, dependsOn, fileScope, acceptanceCriteria));
                }
            }

            if (!isValidTaskGraph(remediationTasks)) {
                LOGGER.warn("pm-agent remediation contains invalid agent assignment or DAG");
                return null;
            }

            return new PmAcceptanceResult(accepted, summary, findings, remediationTasks);
        } catch (Exception e) {
            LOGGER.warn("pm-agent acceptance JSON parse failed", e);
            return null;
        }
    }

    private PlanPatch parsePlanPatchJson(String json, PlanRevisionInput input) {
        try {
            JsonNode root = JsonUtils.mapper().readTree(extractJsonObject(json));
            PlanPatch patch = JsonUtils.mapper().treeToValue(root, PlanPatch.class);
            return PLAN_PATCH_VALIDATOR.validate(input, patch);
        } catch (PlanPatchValidationException e) {
            LOGGER.warn("pm-agent PlanPatch validation failed: requirementId={}, loopId={}, revisionSeq={}, reason={}",
                    input.requirementId(), input.loopId(), input.revisionSeq(), e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.warn("pm-agent PlanPatch JSON parse failed: requirementId={}, loopId={}, revisionSeq={}",
                    input.requirementId(), input.loopId(), input.revisionSeq(), e);
            return null;
        }
    }

    /**
     * 从 agent 文本输出中提取 JSON 对象，兼容 Markdown 围栏及前后说明文字。
     * 若输出不包含完整 JSON 对象则原样返回，由 Jackson 产生解析失败信号。
     */
    private String extractJsonObject(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private boolean isValidTaskGraph(List<RequirementAutomationService.PlanTask> tasks) {
        Map<String, RequirementAutomationService.PlanTask> byKey = new HashMap<>();
        for (RequirementAutomationService.PlanTask task : tasks) {
            boolean validAssignment = ("frontend".equals(task.area())
                    && "frontend-dev-agent".equals(task.agent()))
                    || ("backend".equals(task.area())
                    && "backend-dev-agent".equals(task.agent()))
                    || ("test-agent".equals(task.agent())
                    && ("frontend".equals(task.area()) || "backend".equals(task.area())
                    || "full-stack".equals(task.area()) || "validation".equals(task.area())));
            if (!validAssignment || task.taskKey().isBlank() || task.title() == null
                    || task.title().isBlank() || byKey.putIfAbsent(task.taskKey(), task) != null) {
                return false;
            }
        }
        for (RequirementAutomationService.PlanTask task : tasks) {
            if (task.dependsOn().stream().anyMatch(key -> !byKey.containsKey(key)
                    || key.equals(task.taskKey()))) {
                return false;
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String taskKey : byKey.keySet()) {
            if (hasCycle(taskKey, byKey, visiting, visited)) return false;
        }
        return true;
    }

    private boolean hasCycle(String taskKey,
                             Map<String, RequirementAutomationService.PlanTask> tasks,
                             Set<String> visiting,
                             Set<String> visited) {
        if (visited.contains(taskKey)) return false;
        if (!visiting.add(taskKey)) return true;
        for (String dependency : tasks.get(taskKey).dependsOn()) {
            if (hasCycle(dependency, tasks, visiting, visited)) return true;
        }
        visiting.remove(taskKey);
        visited.add(taskKey);
        return false;
    }

    private List<String> readStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    result.add(item.asText());
                }
            }
        }
        return result;
    }

    private List<ValidationCommand> readValidationCommands(JsonNode validationNode) {
        List<ValidationCommand> commands = new ArrayList<>();
        if (validationNode.isObject()) {
            JsonNode commandsNode = validationNode.path("commands");
            if (commandsNode.isArray()) {
                for (JsonNode cmdNode : commandsNode) {
                    String area = cmdNode.path("area").asText(null);
                    String command = cmdNode.path("command").asText(null);
                    if (command != null && !command.isBlank()) {
                        commands.add(new ValidationCommand(
                                area == null || area.isBlank() ? "full-stack" : area, command));
                    }
                }
            }
        } else if (validationNode.isArray()) {
            // 兼容 PRD 原始字符串列表：按 "area|command" 或纯命令解析
            for (JsonNode item : validationNode) {
                if (item.isTextual()) {
                    String text = item.asText();
                    String area = "full-stack";
                    String command = text;
                    int sep = text.indexOf('|');
                    if (sep > 0) {
                        area = text.substring(0, sep).trim();
                        command = text.substring(sep + 1).trim();
                    }
                    if (!command.isBlank()) {
                        commands.add(new ValidationCommand(area, command));
                    }
                }
            }
        }
        return commands;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * PM 计划解析结果。
     *
     * @param goal               计划目标
     * @param tasks              任务列表
     * @param risks              风险列表
     * @param validationCommands 验证命令列表
     */
    public record PmPlanResult(String goal,
                               List<RequirementAutomationService.PlanTask> tasks,
                               List<String> risks,
                               List<ValidationCommand> validationCommands) {
    }

    /**
     * PM 验收解析结果。
     *
     * @param accepted         是否验收通过
     * @param summary          总结
     * @param findings         发现项
     * @param remediationTasks 补救任务（accepted=false 时非空）
     */
    public record PmAcceptanceResult(boolean accepted,
                                     String summary,
                                     List<String> findings,
                                     List<RequirementAutomationService.PlanTask> remediationTasks) {
    }

    /**
     * 验证命令条目。
     *
     * @param area    适用区域：frontend / backend / full-stack
     * @param command 命令字符串
     */
    public record ValidationCommand(String area, String command) {
    }

    /**
     * 任务级交付审阅输入。{@code reviewDelivery} 的不可变快照。
     *
     * @param requirementId       需求 ID
     * @param loopId              当前 loop id
     * @param codingTaskId        交付完成的 CodingTask ID
     * @param deliveryRunId       被审阅的交付 Run ID
     * @param taskKey             计划任务 key
     * @param taskTitle           任务标题
     * @param taskDescription     任务描述
     * @param area                任务区域
     * @param assignedAgent       分配的 agent 名称
     * @param taskType            任务类型标识：coding-task / validation-task
     * @param deliverySucceeded   交付是否成功
     * @param acceptanceCriteria  验收标准
     * @param deliveryEvidence    交付证据文本（DEV_RESULT / VALIDATION_RESULT 摘要、改动文件、退出码等）
     * @param relatedComments     相关评论历史
     */
    public record DeliveryReviewInput(String requirementId,
                                      String loopId,
                                      String codingTaskId,
                                      String deliveryRunId,
                                      String taskKey,
                                      String taskTitle,
                                      String taskDescription,
                                      String area,
                                      String assignedAgent,
                                      String taskType,
                                      boolean deliverySucceeded,
                                      List<String> acceptanceCriteria,
                                      String deliveryEvidence,
                                      List<RequirementComment> relatedComments) {
    }
}
