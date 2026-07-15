package com.changhong.onlinecode.service.validation;

import com.changhong.onlinecode.agent.AgentBriefWriter;
import com.changhong.onlinecode.agent.AgentInvocationContext;
import com.changhong.onlinecode.agent.CliRunResult;
import com.changhong.onlinecode.agent.AgentWorkspace;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.AgentService;
import com.changhong.onlinecode.service.RequirementCommentService;
import com.changhong.onlinecode.service.RunNumberService;
import com.changhong.onlinecode.service.agent.AgentRunCreateCommand;
import com.changhong.onlinecode.service.agent.AgentRunRecorder;
import com.changhong.sei.core.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs validation through test-agent in the project's bound workspace. */
@Service
@AllArgsConstructor
public class ValidationLoopService {

    private final RunDao runDao;
    private final ExecutionPlanDao executionPlanDao;
    private final RequirementCommentService commentService;
    private final AgentService agentService;
    private final CliRunnerRegistry runnerRegistry;
    private final AgentRunRecorder agentRunRecorder;

    public ValidationOutcome validateTask(CodingTask task) {
        ExecutionPlan plan = executionPlanDao.findOne(task.getExecutionPlanId());
        return validate(task.getRequirementId(), task.getProjectId(), task.getLoopId(), task.getId(),
                task.getPlanTaskKey(), task.getArea(), "task", plan);
    }

    public ValidationOutcome validatePlan(Requirement requirement, ExecutionPlan plan) {
        return validate(requirement.getId(), requirement.getProjectId(), requirement.getActiveLoopId(), null,
                null, "full-stack", "plan", plan);
    }

    private ValidationOutcome validate(String requirementId, String projectId, String loopId,
                                       String codingTaskId, String taskKey, String area, String scope,
                                       ExecutionPlan plan) {
        List<Map<String, Object>> facts = new ArrayList<>();
        TestAgentResult result = runTestAgent(requirementId, projectId, loopId, codingTaskId, taskKey, area, scope, plan);
        facts.addAll(result.facts());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scope", scope);
        metadata.put("taskId", codingTaskId);
        metadata.put("taskKey", taskKey);
        metadata.put("area", area);
        metadata.put("passed", result.passed());
        metadata.put("runId", result.runId());
        metadata.put("facts", facts);
        commentService.append(requirementId, loopId, RequirementCommentAuthorType.TEST_AGENT, "test-agent",
                RequirementCommentType.VALIDATION_RESULT,
                result.report() == null || result.report().isBlank()
                        ? (result.passed() ? "验证通过" : "验证失败")
                        : result.report(),
                toJson(metadata));
        return new ValidationOutcome(result.passed(), facts);
    }

    private Run newRun(String requirementId, String loopId, String codingTaskId,
                       String prompt, ExecutionPlan plan, Agent agent) {
        AgentRunCreateCommand command = new AgentRunCreateCommand();
        command.setRequirementId(requirementId);
        command.setLoopId(loopId);
        command.setCodingTaskId(codingTaskId);
        command.setTriggerSource(TriggerSource.AUTO);
        command.setUserPrompt(prompt);
        if (plan != null) {
            command.setMemoryContextId(plan.getMemoryContextId());
            command.setWorkspaceMemoryId(plan.getWorkspaceMemoryId());
        }
        if (agent != null) {
            command.setAgentId(agent.getId());
            command.setAgentName(agent.getName());
            command.setCliTool(agent.getCliTool());
            command.setModel(agent.getModel());
        }
        // REQUIRES_NEW 确保 Run 在 CLI 启动前提交，避免长事务持有连接等待 CLI。
        return agentRunRecorder.createAgentRun(command);
    }

    private TestAgentResult runTestAgent(String requirementId, String projectId, String loopId, String codingTaskId,
                                         String taskKey, String area, String scope, ExecutionPlan plan) {
        Agent agent = agentService.findByName("test-agent");
        if (agent == null) {
            return TestAgentResult.failed(null, "test-agent 不存在，无法执行验证。");
        }
        Run review = newRun(requirementId, loopId, codingTaskId,
                "Run workspace validation", plan, agent);
        AgentWorkspace workspace = runnerRegistry.workspace(projectId);
        String prompt = buildTestAgentPrompt(scope, area, taskKey, codingTaskId, plan);
        try {
            AgentBriefWriter.writeBrief(workspace.pathString(), agent.getCliTool(), agent.getName(),
                    agent.getInstructions(), agent.getModel(), agent.getMcpConfig() != null, null);
            String report = runnerRegistry.executeDetailed(workspace,
                    new AgentInvocationContext(review.getId(), requirementId, codingTaskId,
                            agent.getId(), agent.getName(), agent.getCliTool(), agent.getModel()),
                    prompt, agent.getMcpConfig())
                    .thenApply(CliRunResult::getOutput).get(30, TimeUnit.MINUTES);
            boolean passed = parsePassed(report);
            settleRun(review.getId(),
                    passed ? RunState.SUCCEEDED : RunState.FAILED,
                    passed ? null : "test-agent validation did not pass");
            return new TestAgentResult(passed, report, review.getId(), extractFacts(report, review.getId()));
        } catch (Exception e) {
            settleRun(review.getId(), RunState.FAILED, e.getMessage());
            return TestAgentResult.failed(review.getId(), "test-agent 验证失败：" + e.getMessage());
        }
    }

    /**
     * 更新 Run 终态。重新加载 Run 实体避免覆盖 usage 列。
     */
    private void settleRun(String runId, RunState state, String reason) {
        Run current = runDao.findOne(runId);
        if (current == null || current.getState() != RunState.RUNNING) {
            return;
        }
        current.setState(state);
        current.setFinishedDate(new Date());
        if (state == RunState.FAILED && reason != null) {
            current.setFailureReason(reason);
        }
        runDao.save(current);
    }

    private String buildTestAgentPrompt(String scope, String area, String taskKey, String codingTaskId,
                                        ExecutionPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are test-agent. Execute validation inside the already-bound project workspace.\n");
        sb.append("Do not rely on service-side fixed commands. Inspect the workspace instructions, build files, ")
                .append("package scripts, Gradle files, and task acceptance criteria, then choose the appropriate ")
                .append("test/build/package commands for this workspace.\n");
        sb.append("scope=").append(scope).append("\n");
        sb.append("area=").append(area).append("\n");
        sb.append("taskKey=").append(taskKey == null ? "" : taskKey).append("\n");
        sb.append("codingTaskId=").append(codingTaskId == null ? "" : codingTaskId).append("\n");
        if (plan != null && plan.getPlanJson() != null) {
            sb.append("\nExecution plan JSON:\n").append(plan.getPlanJson()).append("\n");
        }
        sb.append("\nReturn only valid JSON with this shape:\n");
        sb.append("{\"passed\":true or false,\"summary\":\"string\",\"commands\":[");
        sb.append("{\"command\":\"string\",\"exitCode\":0,\"result\":\"string\"}");
        sb.append("],\"findings\":[\"string\"]}\n");
        return sb.toString();
    }

    private boolean parsePassed(String report) {
        JsonNode root = readJsonReport(report);
        return root != null && root.path("passed").asBoolean(false);
    }

    private List<Map<String, Object>> extractFacts(String report, String runId) {
        List<Map<String, Object>> facts = new ArrayList<>();
        JsonNode root = readJsonReport(report);
        if (root != null && root.path("commands").isArray()) {
            for (JsonNode command : root.path("commands")) {
                Map<String, Object> fact = new LinkedHashMap<>();
                fact.put("command", command.path("command").asText(""));
                fact.put("exitCode", command.path("exitCode").isNumber()
                        ? command.path("exitCode").asInt() : null);
                fact.put("result", command.path("result").asText(""));
                fact.put("runId", runId);
                facts.add(fact);
            }
        }
        if (facts.isEmpty()) {
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("runId", runId);
            fact.put("report", report);
            facts.add(fact);
        }
        return facts;
    }

    private JsonNode readJsonReport(String report) {
        if (report == null || report.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.mapper().readTree(report);
        } catch (Exception ignored) {
            int start = report.indexOf('{');
            int end = report.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return JsonUtils.mapper().readTree(report.substring(start, end + 1));
                } catch (Exception nestedIgnored) {
                    return null;
                }
            }
            return null;
        }
    }

    private String toJson(Object value) {
        try { return JsonUtils.mapper().writeValueAsString(value); }
        catch (Exception e) { return "{}"; }
    }

    public record ValidationOutcome(boolean passed, List<Map<String, Object>> facts) { }

    private record TestAgentResult(boolean passed, String report, String runId, List<Map<String, Object>> facts) {
        static TestAgentResult failed(String runId, String report) {
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("runId", runId);
            fact.put("report", report);
            return new TestAgentResult(false, report, runId, List.of(fact));
        }
    }
}
