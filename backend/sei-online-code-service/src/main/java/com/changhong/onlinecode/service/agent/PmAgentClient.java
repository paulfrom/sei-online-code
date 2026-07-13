package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.agent.AgentBriefWriter;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunType;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.AgentService;
import com.changhong.onlinecode.service.RequirementAutomationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * PM agent 调用客户端。
 *
 * <p>负责按名称解析 {@code pm-agent}，构造 planning / acceptance prompt，
 * 调用 {@link CliRunner} 并解析结构化 JSON 结果。所有调用同步阻塞返回，
 * 调用方（{@link com.changhong.onlinecode.service.RequirementAutomationService}）
 * 在事务内决定是否回滚。</p>
 */
@Component
public class PmAgentClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PmAgentClient.class);
    private static final String AGENT_NAME = "pm-agent";
    private static final long PLAN_TIMEOUT_SECONDS = 600;
    private static final long ACCEPT_TIMEOUT_SECONDS = 600;

    private final AgentService agentService;
    private final CliRunnerRegistry cliRunnerRegistry;
    private final WorkspaceManager workspaceManager;
    private final RunDao runDao;
    private final ObjectMapper objectMapper;

    public PmAgentClient(AgentService agentService,
                         CliRunnerRegistry cliRunnerRegistry,
                         WorkspaceManager workspaceManager,
                         RunDao runDao,
                         ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.cliRunnerRegistry = cliRunnerRegistry;
        this.workspaceManager = workspaceManager;
        this.runDao = runDao;
        this.objectMapper = objectMapper;
    }

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
        Agent agent = agentService.findByName(AGENT_NAME);
        if (agent == null) {
            LOGGER.error("pm-agent not found");
            return null;
        }

        String prompt = buildPlanningPrompt(requirement, planType, context, previousComments, previousPlan);
        Run run = createRun(requirement.getId(), loopId, RunType.PM_PLANNING, prompt,
                context == null ? null : context.getId(),
                context == null ? null : context.getWorkspaceMemoryId());

        String output = executeAgent(agent, requirement.getProjectId(), prompt, PLAN_TIMEOUT_SECONDS);
        if (output == null) {
            markRunFailed(run, "pm-agent 调用失败或无输出");
            return null;
        }

        markRunSucceeded(run);
        return parsePlanJson(output, requirement.getId(), loopId);
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
        Agent agent = agentService.findByName(AGENT_NAME);
        if (agent == null) {
            LOGGER.error("pm-agent not found");
            return null;
        }

        String prompt = buildAcceptancePrompt(requirement, plan, tasks, previousComments, context);
        Run run = createRun(requirement.getId(), plan.getLoopId(), RunType.PM_ACCEPTANCE, prompt,
                plan.getMemoryContextId(), plan.getWorkspaceMemoryId());

        String output = executeAgent(agent, requirement.getProjectId(), prompt, ACCEPT_TIMEOUT_SECONDS);
        if (output == null) {
            markRunFailed(run, "pm-agent 验收调用失败或无输出");
            return null;
        }

        markRunSucceeded(run);
        return parseAcceptanceJson(output);
    }

    private String executeAgent(Agent agent, String projectId, String prompt, long timeoutSeconds) {
        CliRunner runner = cliRunnerRegistry.resolve(agent.getCliTool());
        WorkspaceResolveResult workspace = workspaceManager.resolve(projectId);
        String cwd = workspace == null ? null : workspace.getPath();

        AgentBriefWriter.writeBrief(cwd, agent.getCliTool(), agent.getName(),
                agent.getInstructions(), agent.getModel(),
                agent.getMcpConfig() != null && !agent.getMcpConfig().isBlank(), null);

        CompletableFuture<String> future = runner.execute(
                projectId, null, null, prompt, cwd, agent.getModel(), agent.getMcpConfig());
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("pm-agent execution failed or timed out", e);
            future.cancel(true);
            return null;
        }
    }

    private Run createRun(String requirementId, String loopId, RunType runType, String prompt,
                          String memoryContextId, String workspaceMemoryId) {
        Run run = new Run();
        run.setRequirementId(requirementId);
        run.setLoopId(loopId);
        run.setRunType(runType);
        run.setTriggerSource(TriggerSource.AUTO);
        run.setState(RunState.RUNNING);
        run.setUserPrompt(prompt);
        run.setMemoryContextId(memoryContextId);
        run.setWorkspaceMemoryId(workspaceMemoryId);
        run.setStartedDate(new Date());
        return runDao.save(run);
    }

    private void markRunSucceeded(Run run) {
        run.setState(RunState.SUCCEEDED);
        run.setFinishedDate(new Date());
        runDao.save(run);
    }

    private void markRunFailed(Run run, String reason) {
        run.setState(RunState.FAILED);
        run.setFailureSummary("PM agent 调用失败");
        run.setFailureReason(reason);
        run.setFinishedDate(new Date());
        runDao.save(run);
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

        sb.append("\nReturn **only** valid JSON with this exact structure:\n");
        sb.append("{\n");
        sb.append("  \"goal\": \"string\",\n");
        sb.append("  \"tasks\": [\n");
        sb.append("    {\n");
        sb.append("      \"taskKey\": \"FE-001\",\n");
        sb.append("      \"title\": \"string\",\n");
        sb.append("      \"description\": \"string\",\n");
        sb.append("      \"agent\": \"frontend-dev-agent\" or \"backend-dev-agent\",\n");
        sb.append("      \"area\": \"frontend\" or \"backend\",\n");
        sb.append("      \"dependsOn\": [],\n");
        sb.append("      \"fileScope\": [\"frontend/src/...\"],\n");
        sb.append("      \"acceptanceCriteria\": [\"string\"]\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"risks\": [\"string\"],\n");
        sb.append("  \"validation\": {\n");
        sb.append("    \"commands\": [\n");
        sb.append("      {\"area\": \"frontend\", \"command\": \"pnpm -C frontend build\"},\n");
        sb.append("      {\"area\": \"backend\", \"command\": \"./gradlew :sei-online-code-service:compileJava\"}\n");
        sb.append("    ]\n");
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
        sb.append("      \"agent\": \"frontend-dev-agent\" or \"backend-dev-agent\",\n");
        sb.append("      \"area\": \"frontend\" or \"backend\",\n");
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
            JsonNode root = objectMapper.readTree(json);
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

                if (taskKey == null || title == null || agent == null || area == null) {
                    LOGGER.warn("pm-agent task missing required fields: {}", taskNode);
                    return null;
                }
                tasks.add(new RequirementAutomationService.PlanTask(
                        taskKey, title, description, agent, area, dependsOn, fileScope));
            }

            return new PmPlanResult(goal, tasks, risks, validationCommands);
        } catch (Exception e) {
            LOGGER.warn("pm-agent plan JSON parse failed: requirementId={}, loopId={}", requirementId, loopId, e);
            return null;
        }
    }

    private PmAcceptanceResult parseAcceptanceJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
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
                    if (taskKey == null || title == null || agent == null || area == null) {
                        LOGGER.warn("pm-agent remediation task missing required fields: {}", taskNode);
                        continue;
                    }
                    remediationTasks.add(new RequirementAutomationService.PlanTask(
                            taskKey, title, description, agent, area, dependsOn, fileScope));
                }
            }

            return new PmAcceptanceResult(accepted, summary, findings, remediationTasks);
        } catch (Exception e) {
            LOGGER.warn("pm-agent acceptance JSON parse failed", e);
            return null;
        }
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
