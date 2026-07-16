package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.RequirementAutomationService;
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

        markRunSucceeded(execution.runId());
        return parsePlanJson(execution.output(), requirement.getId(), loopId);
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

        markRunSucceeded(execution.runId());
        return parseAcceptanceJson(execution.output());
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

    private PmPlanResult parsePlanJson(String json, String requirementId, String loopId) {
        try {
            JsonNode root = JsonUtils.mapper().readTree(json);
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
            JsonNode root = JsonUtils.mapper().readTree(json);
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
}
