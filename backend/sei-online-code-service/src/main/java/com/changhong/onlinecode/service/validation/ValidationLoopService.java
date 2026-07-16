package com.changhong.onlinecode.service.validation;

import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.service.RequirementCommentService;
import com.changhong.onlinecode.service.agent.AgentExecutionRequest;
import com.changhong.onlinecode.service.agent.AgentExecutionResult;
import com.changhong.onlinecode.service.agent.AgentExecutionService;
import com.changhong.sei.core.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs validation through test-agent in the project's bound workspace. */
@Service
@AllArgsConstructor
public class ValidationLoopService {

    private final ExecutionPlanDao executionPlanDao;
    private final RequirementCommentService commentService;
    private final AgentExecutionService agentExecutionService;

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

    private TestAgentResult runTestAgent(String requirementId, String projectId, String loopId, String codingTaskId,
                                         String taskKey, String area, String scope, ExecutionPlan plan) {
        String prompt = buildTestAgentPrompt(scope, area, taskKey, codingTaskId, plan);
        AgentExecutionRequest request = new AgentExecutionRequest();
        request.setProjectId(projectId);
        request.setRequirementId(requirementId);
        request.setLogStreamKey(requirementId);
        request.setLoopId(loopId);
        request.setCodingTaskId(codingTaskId);
        request.setTriggerSource(TriggerSource.AUTO);
        request.setPrompt(prompt);
        request.setTimeoutSeconds(1_800L);
        if (plan != null) {
            request.setMemoryContextId(plan.getMemoryContextId());
            request.setWorkspaceMemoryId(plan.getWorkspaceMemoryId());
        }
        AgentExecutionResult result = agentExecutionService.execute("test-agent", request);
        String report = result.output();
        boolean passed = result.succeeded() && parsePassed(report);
        String failureReason = result.succeeded() ? "test-agent validation did not pass" : result.failureReason();
        if (result.runId() != null) {
            agentExecutionService.settleRun(result.runId(),
                    passed ? RunState.SUCCEEDED : RunState.FAILED,
                    passed ? null : failureReason);
        }
        if (!result.succeeded()) {
            return TestAgentResult.failed(result.runId(), "test-agent 验证失败：" + failureReason);
        }
        return new TestAgentResult(passed, report, result.runId(), extractFacts(report, result.runId()));
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
