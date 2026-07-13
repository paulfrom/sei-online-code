package com.changhong.onlinecode.service.validation;

import com.changhong.onlinecode.agent.AgentBriefWriter;
import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunType;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.AgentService;
import com.changhong.onlinecode.service.RequirementCommentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Executes validation commands, records Runs, and asks test-agent to review facts. */
@Service
public class ValidationLoopService {

    private final ValidationCommandExecutor executor;
    private final RunDao runDao;
    private final ExecutionPlanDao executionPlanDao;
    private final ProjectDao projectDao;
    private final WorkspaceManager workspaceManager;
    private final RequirementCommentService commentService;
    private final AgentService agentService;
    private final CliRunnerRegistry runnerRegistry;
    private final ObjectMapper objectMapper;

    public ValidationLoopService(ValidationCommandExecutor executor, RunDao runDao,
                                 ExecutionPlanDao executionPlanDao, ProjectDao projectDao,
                                 WorkspaceManager workspaceManager, RequirementCommentService commentService,
                                 AgentService agentService, CliRunnerRegistry runnerRegistry,
                                 ObjectMapper objectMapper) {
        this.executor = executor;
        this.runDao = runDao;
        this.executionPlanDao = executionPlanDao;
        this.projectDao = projectDao;
        this.workspaceManager = workspaceManager;
        this.commentService = commentService;
        this.agentService = agentService;
        this.runnerRegistry = runnerRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ValidationOutcome validateTask(CodingTask task) {
        ExecutionPlan plan = executionPlanDao.findOne(task.getExecutionPlanId());
        return validate(task.getRequirementId(), task.getProjectId(), task.getLoopId(), task.getId(),
                task.getArea(), "task", plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public ValidationOutcome validatePlan(Requirement requirement, ExecutionPlan plan) {
        return validate(requirement.getId(), requirement.getProjectId(), requirement.getActiveLoopId(), null,
                "full-stack", "plan", plan);
    }

    private ValidationOutcome validate(String requirementId, String projectId, String loopId,
                                       String codingTaskId, String area, String scope, ExecutionPlan plan) {
        WorkspaceResolveResult workspace = workspaceManager.resolve(projectId);
        Path cwd = Path.of(workspace.getPath());
        List<String> commands = resolveCommands(plan, projectDao.findOne(projectId), area);
        List<Map<String, Object>> facts = new ArrayList<>();
        boolean passed = true;
        for (String command : commands) {
            Run run = newRun(requirementId, loopId, codingTaskId, RunType.VALIDATION_COMMAND, command, plan);
            ValidationCommandExecutor.ValidationResult result = executor.execute(cwd, command);
            run.setExitCode(result.getExitCode());
            run.setState(result.isSuccess() ? RunState.SUCCEEDED : RunState.FAILED);
            run.setFailureReason(result.isSuccess() ? null : result.getStderr());
            run.setFinishedDate(new Date());
            runDao.save(run);
            passed &= result.isSuccess();
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("command", command);
            fact.put("exitCode", result.getExitCode());
            fact.put("durationMs", result.getDuration().toMillis());
            fact.put("stdout", result.getStdout());
            fact.put("stderr", result.getStderr());
            fact.put("runId", run.getId());
            facts.add(fact);
        }
        String report = askTestAgent(requirementId, projectId, loopId, codingTaskId, area, scope, facts, plan);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scope", scope);
        metadata.put("area", area);
        metadata.put("passed", passed);
        metadata.put("commands", facts);
        commentService.append(requirementId, loopId, RequirementCommentAuthorType.TEST_AGENT, "test-agent",
                RequirementCommentType.VALIDATION_RESULT,
                report == null || report.isBlank() ? (passed ? "验证通过" : "验证失败") : report,
                toJson(metadata));
        return new ValidationOutcome(passed, facts);
    }

    private Run newRun(String requirementId, String loopId, String codingTaskId, RunType type,
                       String prompt, ExecutionPlan plan) {
        Run run = new Run();
        run.setRequirementId(requirementId);
        run.setLoopId(loopId);
        run.setCodingTaskId(codingTaskId);
        run.setRunType(type);
        run.setTriggerSource(TriggerSource.AUTO);
        run.setUserPrompt(prompt);
        run.setState(RunState.RUNNING);
        run.setStartedDate(new Date());
        if (plan != null) {
            run.setMemoryContextId(plan.getMemoryContextId());
            run.setWorkspaceMemoryId(plan.getWorkspaceMemoryId());
        }
        return runDao.save(run);
    }

    private String askTestAgent(String requirementId, String projectId, String loopId, String codingTaskId, String area,
                                String scope, List<Map<String, Object>> facts, ExecutionPlan plan) {
        Agent agent = agentService.findByName("test-agent");
        if (agent == null) {
            return "test-agent 不存在；系统仅按命令退出码判定。";
        }
        Run review = newRun(requirementId, loopId, codingTaskId, RunType.TEST_REVIEW,
                "Review validation facts", plan);
        WorkspaceResolveResult workspace = workspaceManager.resolve(projectId);
        String prompt = "Interpret these immutable validation facts. Do not invent executions. scope=" + scope
                + ", area=" + area + "\n" + toJson(facts);
        try {
            AgentBriefWriter.writeBrief(workspace.getPath(), agent.getCliTool(), agent.getName(),
                    agent.getInstructions(), agent.getModel(), agent.getMcpConfig() != null, null);
            CliRunner runner = runnerRegistry.resolve(agent.getCliTool());
            String report = runner.execute(requirementId, codingTaskId, review.getId(), prompt,
                    workspace.getPath(), agent.getModel(), agent.getMcpConfig()).get(30, TimeUnit.MINUTES);
            review.setState(report == null ? RunState.FAILED : RunState.SUCCEEDED);
            review.setFinishedDate(new Date());
            runDao.save(review);
            return report;
        } catch (Exception e) {
            review.setState(RunState.FAILED);
            review.setFailureReason(e.getMessage());
            review.setFinishedDate(new Date());
            runDao.save(review);
            return "test-agent 评审失败：" + e.getMessage();
        }
    }

    private List<String> resolveCommands(ExecutionPlan plan, Project project, String area) {
        List<String> commands = readCommands(plan == null ? null : plan.getPlanJson(), area);
        if (commands.isEmpty()) {
            commands = readCommands(project == null ? null : project.getValidationConfig(), area);
        }
        if (!commands.isEmpty()) {
            return commands;
        }
        if ("frontend".equals(area)) {
            return List.of("pnpm -C frontend build");
        }
        if ("backend".equals(area)) {
            return List.of("./gradlew :sei-online-code-service:compileJava");
        }
        return List.of("pnpm -C frontend build", "./gradlew :sei-online-code-service:compileJava");
    }

    private List<String> readCommands(String json, String area) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode node = root.path("validation").path("commands");
            if (node.isMissingNode()) node = root.path("commands");
            if (!node.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isTextual()) result.add(item.asText());
                else if ((Objects.equals(area, item.path("area").asText()) || "full-stack".equals(area))
                        && item.path("command").isTextual()) result.add(item.path("command").asText());
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { return "{}"; }
    }

    public record ValidationOutcome(boolean passed, List<Map<String, Object>> facts) { }
}
