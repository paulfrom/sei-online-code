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
        if (result.status() == ValidationStatus.DEFERRED) {
            return ValidationOutcome.deferred(result.failureReason());
        }
        facts.addAll(result.facts());
        boolean passed = result.status() == ValidationStatus.PASSED;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scope", scope);
        metadata.put("taskId", codingTaskId);
        metadata.put("taskKey", taskKey);
        metadata.put("area", area);
        metadata.put("passed", passed);
        metadata.put("runId", result.runId());
        metadata.put("facts", facts);
        commentService.append(requirementId, loopId, RequirementCommentAuthorType.TEST_AGENT, "test-agent",
                RequirementCommentType.VALIDATION_RESULT,
                result.report() == null || result.report().isBlank()
                        ? (passed ? "验证通过" : "验证失败")
                        : result.report(),
                toJson(metadata));
        return passed
                ? ValidationOutcome.passed(facts)
                : ValidationOutcome.failed(facts, result.failureReason());
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
        if (result == null) {
            return TestAgentResult.failed(null, null, "test-agent 执行无结果");
        }
        if (result.status() == AgentExecutionResult.Status.DEFERRED) {
            return TestAgentResult.deferred(result.runId(), result.failureReason());
        }
        String report = result.output();
        boolean executionSucceeded = result.status() == AgentExecutionResult.Status.SUCCEEDED;
        boolean passed = executionSucceeded && parsePassed(report);
        String failureReason = executionSucceeded
                ? validationFailureReason(report)
                : firstNonBlank(result.failureReason(), "test-agent 执行失败");
        if (result.runId() != null) {
            agentExecutionService.settleRun(result.runId(),
                    passed ? RunState.SUCCEEDED : RunState.FAILED,
                    passed ? null : failureReason);
        }
        if (!executionSucceeded) {
            return TestAgentResult.failed(result.runId(), report, failureReason);
        }
        List<Map<String, Object>> facts = extractFacts(report, result.runId());
        return passed
                ? TestAgentResult.passed(result.runId(), report, facts)
                : TestAgentResult.failed(result.runId(), report, failureReason, facts);
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
        sb.append("],\"findings\":[\"string\"],\"acceptanceCriteria\":[");
        sb.append("{\"criterion\":\"string\",\"status\":\"PASSED|FAILED|NOT_APPLICABLE\",");
        sb.append("\"evidence\":[\"specific command/log/diff reference\"]}]}\n");
        sb.append("Every actual acceptance criterion in the plan must have one result. "
                + "Never report a command or criterion as passed unless it was actually verified.\n");
        return sb.toString();
    }

    private boolean parsePassed(String report) {
        JsonNode root = readJsonReport(report);
        return root != null && root.path("passed").asBoolean(false);
    }

    private String validationFailureReason(String report) {
        JsonNode root = readJsonReport(report);
        if (root == null) {
            return report == null || report.isBlank()
                    ? "test-agent 未返回验证报告"
                    : "test-agent 返回的验证报告不是有效 JSON；原始输出已保存到 Run.summary";
        }
        List<String> details = new ArrayList<>();
        String summary = root.path("summary").asText("");
        if (!summary.isBlank()) {
            details.add(summary);
        }
        JsonNode commands = root.path("commands");
        if (commands.isArray()) {
            for (JsonNode command : commands) {
                if (command.path("exitCode").isNumber() && command.path("exitCode").asInt() != 0) {
                    details.add(command.path("command").asText("unknown command")
                            + " (exitCode=" + command.path("exitCode").asInt() + ")");
                }
            }
        }
        JsonNode findings = root.path("findings");
        if (findings.isArray() && !findings.isEmpty()) {
            String finding = findings.get(0).asText("");
            if (!finding.isBlank()) {
                details.add(finding);
            }
        }
        return details.isEmpty()
                ? "test-agent 报告 passed=false，但未提供 summary、失败命令或 findings"
                : "test-agent 验证未通过：" + String.join("; ", details);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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

    public enum ValidationStatus {
        PASSED,
        FAILED,
        DEFERRED
    }

    public record ValidationOutcome(ValidationStatus status, List<Map<String, Object>> facts,
                                    String failureReason) {
        public static ValidationOutcome passed(List<Map<String, Object>> facts) {
            return new ValidationOutcome(ValidationStatus.PASSED, facts, null);
        }

        public static ValidationOutcome failed(List<Map<String, Object>> facts, String reason) {
            return new ValidationOutcome(ValidationStatus.FAILED, facts, reason);
        }

        public static ValidationOutcome deferred(String reason) {
            return new ValidationOutcome(ValidationStatus.DEFERRED, List.of(), reason);
        }
    }

    private record TestAgentResult(ValidationStatus status, String report, String runId,
                                   List<Map<String, Object>> facts, String failureReason) {
        static TestAgentResult passed(String runId, String report, List<Map<String, Object>> facts) {
            return new TestAgentResult(ValidationStatus.PASSED, report, runId, facts, null);
        }

        static TestAgentResult failed(String runId, String report, String failureReason) {
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("runId", runId);
            fact.put("report", report == null || report.isBlank() ? failureReason : report);
            fact.put("failureReason", failureReason);
            String visibleReport = report == null || report.isBlank()
                    ? "test-agent 验证失败：" + failureReason
                    : report;
            return failed(runId, visibleReport, failureReason, List.of(fact));
        }

        static TestAgentResult failed(String runId, String report, String failureReason,
                                      List<Map<String, Object>> facts) {
            return new TestAgentResult(ValidationStatus.FAILED, report, runId, facts, failureReason);
        }

        static TestAgentResult deferred(String runId, String reason) {
            return new TestAgentResult(ValidationStatus.DEFERRED, null, runId, List.of(), reason);
        }
    }
}
