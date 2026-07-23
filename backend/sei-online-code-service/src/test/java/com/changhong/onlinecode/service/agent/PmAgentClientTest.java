package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.dto.revision.PlanPatch;
import com.changhong.onlinecode.service.revision.contract.PlanRevisionInput;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PmAgentClientTest {

    @Test
    void generatePlanPatch_includesCompleteSnapshotAndAcceptsValidPatch() {
        RunDao runDao = mock(RunDao.class);
        AgentExecutionService agentExecutionService = mock(AgentExecutionService.class);
        Run run = new Run();
        run.setId("run-patch");
        run.setState(RunState.RUNNING);
        when(runDao.findOne("run-patch")).thenReturn(run);
        AtomicReference<AgentExecutionRequest> captured = new AtomicReference<>();
        when(agentExecutionService.execute(org.mockito.ArgumentMatchers.eq("pm-agent"),
                any(AgentExecutionRequest.class))).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(1));
            return new AgentExecutionResult("run-patch", """
                    ```json
                    {"requirementId":"req-1","loopId":"loop-1","revisionSeq":2,
                    "basePlanId":"plan-1","basePlanVersion":1,"summary":"保留后端",
                    "operations":[{"taskKey":"BE-1","action":"KEEP","sourceTaskId":"task-be",
                    "reason":"不受评论影响"}]}
                    ```
                    """, true, null);
        });
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setProjectId("project-1");
        PlanRevisionInput input = revisionInput();

        PlanPatch result = new PmAgentClient(runDao, agentExecutionService)
                .generatePlanPatch(requirement, input, null);

        assertNotNull(result);
        assertEquals("KEEP", result.getOperations().get(0).getAction().name());
        assertEquals(RunState.SUCCEEDED, run.getState());
        String prompt = captured.get().getPrompt();
        assertTrue(prompt.contains("完整评论内容"));
        assertTrue(prompt.contains("\\\"goal\\\":\\\"base\\\""));
        assertTrue(prompt.contains("已完成接口实现"));
        assertTrue(prompt.contains("backend/src/Api.java"));
    }

    @Test
    void generatePlanPatch_invalidDagMarksRunFailed() {
        RunDao runDao = mock(RunDao.class);
        AgentExecutionService agentExecutionService = mock(AgentExecutionService.class);
        Run run = new Run();
        run.setId("run-invalid-patch");
        run.setState(RunState.RUNNING);
        when(runDao.findOne("run-invalid-patch")).thenReturn(run);
        when(agentExecutionService.execute(org.mockito.ArgumentMatchers.eq("pm-agent"),
                any(AgentExecutionRequest.class))).thenReturn(new AgentExecutionResult("run-invalid-patch", """
                    {"requirementId":"req-1","loopId":"loop-1","revisionSeq":2,
                    "basePlanId":"plan-1","basePlanVersion":1,"summary":"bad",
                    "operations":[{"taskKey":"NEW","action":"ADD","reason":"change","title":"new",
                    "description":"new","area":"backend","fileScope":["backend/"],
                    "dependsOn":["MISSING"],"assignedAgent":"backend-dev-agent"}]}
                    """, true, null));
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setProjectId("project-1");

        PlanPatch result = new PmAgentClient(runDao, agentExecutionService)
                .generatePlanPatch(requirement, revisionInput(), null);

        assertNull(result);
        assertEquals(RunState.FAILED, run.getState());
        assertEquals("pm-agent 返回内容无法解析为有效 PlanPatch JSON", run.getFailureReason());
    }

    @Test
    void generatePlan_passesPersistedRunIdToRunnerAndRejectsCancelledResult() {
        RunDao runDao = mock(RunDao.class);
        AgentExecutionService agentExecutionService = mock(AgentExecutionService.class);
        AtomicReference<Run> savedRun = new AtomicReference<>();
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            savedRun.set(run);
            return run;
        });
        Run run = new Run();
        run.setId("run-1");
        run.setState(RunState.RUNNING);
        savedRun.set(run);
        when(runDao.findOne("run-1")).thenAnswer(invocation -> savedRun.get());
        when(agentExecutionService.execute(org.mockito.ArgumentMatchers.eq("pm-agent"),
                any(AgentExecutionRequest.class))).thenAnswer(invocation -> {
            savedRun.get().setCancelRequested(Boolean.TRUE);
            return new AgentExecutionResult("run-1", """
                    {"goal":"g","tasks":[{"taskKey":"BE-1","title":"t","description":"d",
                    "agent":"backend-dev-agent","area":"backend","dependsOn":[],"fileScope":["backend/"]}],
                    "risks":[],"validation":{"commands":[]}}
                    """, true, null);
        });

        PmAgentClient client = new PmAgentClient(runDao, agentExecutionService);
        Requirement requirement = new Requirement();
        requirement.setId("requirement-1");
        requirement.setProjectId("project-1");

        PmAgentClient.PmPlanResult result = client.generatePlan(requirement, "loop-1",
                ExecutionPlanType.INITIAL, null, List.of(), null);

        assertNull(result);
        assertEquals(RunState.CANCELLED, savedRun.get().getState());
    }

    @Test
    void generatePlan_cliFailureDoesNotParseOutputAsJsonAndStoresRealFailure() {
        RunDao runDao = mock(RunDao.class);
        AgentExecutionService agentExecutionService = mock(AgentExecutionService.class);
        AtomicReference<Run> savedRun = new AtomicReference<>();
        Run run = new Run();
        run.setId("run-failed");
        run.setState(RunState.RUNNING);
        savedRun.set(run);
        when(runDao.findOne("run-failed")).thenAnswer(invocation -> savedRun.get());
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run saved = invocation.getArgument(0);
            savedRun.set(saved);
            return saved;
        });
        when(agentExecutionService.execute(org.mockito.ArgumentMatchers.eq("pm-agent"),
                any(AgentExecutionRequest.class))).thenReturn(new AgentExecutionResult(
                "run-failed", "API Error: Unable to connect to API (ECONNRESET)",
                false, "claude exited with code 1"));

        PmAgentClient client = new PmAgentClient(runDao, agentExecutionService);
        Requirement requirement = new Requirement();
        requirement.setId("requirement-1");
        requirement.setProjectId("project-1");

        PmAgentClient.PmPlanResult result = client.generatePlan(requirement, "loop-1",
                ExecutionPlanType.INITIAL, null, List.of(), null);

        assertNull(result);
        assertEquals(RunState.FAILED, savedRun.get().getState());
        assertEquals("API Error: Unable to connect to API (ECONNRESET)", savedRun.get().getSummary());
        assertEquals("API Error: Unable to connect to API (ECONNRESET)", savedRun.get().getFailureReason());
    }

    @Test
    void generatePlan_nonJsonOutputMarksRunFailed() {
        RunDao runDao = mock(RunDao.class);
        AgentExecutionService agentExecutionService = mock(AgentExecutionService.class);
        AtomicReference<Run> savedRun = new AtomicReference<>();
        Run run = new Run();
        run.setId("run-invalid-json");
        run.setState(RunState.RUNNING);
        savedRun.set(run);
        when(runDao.findOne("run-invalid-json")).thenAnswer(invocation -> savedRun.get());
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run saved = invocation.getArgument(0);
            savedRun.set(saved);
            return saved;
        });
        when(agentExecutionService.execute(org.mockito.ArgumentMatchers.eq("pm-agent"),
                any(AgentExecutionRequest.class))).thenReturn(new AgentExecutionResult(
                "run-invalid-json", """
                ---

                **执行计划已输出。**
                """, true, null));

        PmAgentClient client = new PmAgentClient(runDao, agentExecutionService);
        Requirement requirement = new Requirement();
        requirement.setId("requirement-1");
        requirement.setProjectId("project-1");

        PmAgentClient.PmPlanResult result = client.generatePlan(requirement, "loop-1",
                ExecutionPlanType.INITIAL, null, List.of(), null);

        assertNull(result);
        assertEquals(RunState.FAILED, savedRun.get().getState());
        assertEquals("pm-agent 返回内容无法解析为有效计划 JSON", savedRun.get().getFailureReason());
    }

    @Test
    void parsePlan_rejectsInvalidAgentAreaDuplicateKeysAndInvalidDag() throws Exception {
        PmAgentClient client = new PmAgentClient(mock(RunDao.class), mock(AgentExecutionService.class));

        assertNull(parsePlan(client, """
                {"goal":"g","tasks":[{"taskKey":"T1","title":"t","agent":"frontend-dev-agent",
                "area":"backend","dependsOn":[],"fileScope":[],"acceptanceCriteria":["ok"]}]}
                """));
        assertNull(parsePlan(client, """
                {"goal":"g","tasks":[
                {"taskKey":"T1","title":"a","agent":"backend-dev-agent","area":"backend","dependsOn":[]},
                {"taskKey":"T1","title":"b","agent":"backend-dev-agent","area":"backend","dependsOn":[]}]}
                """));
        assertNull(parsePlan(client, """
                {"goal":"g","tasks":[
                {"taskKey":"T1","title":"a","agent":"backend-dev-agent","area":"backend","dependsOn":["T2"]},
                {"taskKey":"T2","title":"b","agent":"backend-dev-agent","area":"backend","dependsOn":["T1"]}]}
                """));
        assertNull(parsePlan(client, """
                {"goal":"g","tasks":[{"taskKey":"T1","title":"a","agent":"backend-dev-agent",
                "area":"backend","dependsOn":["MISSING"]}]}
                """));
    }

    @Test
    void parsePlan_preservesAcceptanceCriteriaForValidDag() throws Exception {
        PmAgentClient client = new PmAgentClient(mock(RunDao.class), mock(AgentExecutionService.class));
        PmAgentClient.PmPlanResult result = parsePlan(client, """
                {"goal":"g","tasks":[{"taskKey":"BE-1","title":"a","agent":"backend-dev-agent",
                "area":"backend","dependsOn":[],"fileScope":["backend/"],
                "acceptanceCriteria":["接口测试通过"]}],"risks":[],"validation":{"commands":[]}}
                """);

        assertNotNull(result);
        assertEquals(List.of("接口测试通过"), result.tasks().get(0).acceptanceCriteria());
    }

    @Test
    void parsePlan_extractsJsonFromMarkdownOutput() throws Exception {
        PmAgentClient client = new PmAgentClient(mock(RunDao.class), mock(AgentExecutionService.class));
        PmAgentClient.PmPlanResult result = parsePlan(client, """
                计划如下：
                ```json
                {"goal":"g","tasks":[{"taskKey":"BE-1","title":"a","agent":"backend-dev-agent",
                "area":"backend","dependsOn":[],"fileScope":["backend/"],
                "acceptanceCriteria":["接口测试通过"]}],"risks":[],"validation":{"commands":[]}}
                ```
                以上为执行计划。
                """);

        assertNotNull(result);
        assertEquals("g", result.goal());
        assertEquals("BE-1", result.tasks().get(0).taskKey());
    }

    @Test
    void parsePlan_acceptsExplicitTestAgentValidationTask() throws Exception {
        PmAgentClient client = new PmAgentClient(mock(RunDao.class), mock(AgentExecutionService.class));
        PmAgentClient.PmPlanResult result = parsePlan(client, """
                {"goal":"g","tasks":[
                {"taskKey":"BE-1","title":"a","agent":"backend-dev-agent",
                "area":"backend","dependsOn":[],"fileScope":["backend/"]},
                {"taskKey":"VAL-1","title":"验收","agent":"test-agent",
                "area":"full-stack","dependsOn":["BE-1"],"fileScope":[]}]}
                """);

        assertNotNull(result);
        assertEquals("test-agent", result.tasks().get(1).agent());
        assertEquals(List.of("BE-1"), result.tasks().get(1).dependsOn());
    }

    @Test
    void parseAcceptance_extractsJsonFromMarkdownOutput() throws Exception {
        PmAgentClient client = new PmAgentClient(mock(RunDao.class), mock(AgentExecutionService.class));
        PmAgentClient.PmAcceptanceResult result = parseAcceptance(client, """
                ```json
                {"accepted":true,"summary":"验收通过","findings":[],"remediationTasks":[]}
                ```
                """);

        assertNotNull(result);
        assertTrue(result.accepted());
        assertEquals("验收通过", result.summary());
    }

    @Test
    void parseDelivery_approveDecisionForValidJson() throws Exception {
        PmAgentClient client = new PmAgentClient(mock(RunDao.class), mock(AgentExecutionService.class));
        PmDeliveryDecision decision = parseDelivery(client, """
                {"decision":"APPROVE","summary":"ok","failureCategory":"NONE","findings":["evidence"],"retryReason":null,"remediationTasks":[]}
                """);
        assertNotNull(decision);
        assertEquals(com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision.APPROVE, decision.decision());
        assertEquals(com.changhong.onlinecode.dto.enums.DeliveryFailureCategory.NONE, decision.failureCategory());
        assertEquals(List.of("evidence"), decision.findings());
    }

    @Test
    void parseDelivery_retryWithoutReasonIsRejected() throws Exception {
        PmAgentClient client = new PmAgentClient(mock(RunDao.class), mock(AgentExecutionService.class));
        // RETRY 缺少 retryReason 应被拒绝（返回 null），由服务端转 WAIT_HUMAN。
        assertNull(parseDelivery(client, """
                {"decision":"RETRY","summary":"retry","failureCategory":"TRANSIENT_INFRA","retryReason":null,"remediationTasks":[]}
                """));
    }

    @Test
    void parseDelivery_replanWithoutRemediationTasksIsRejected() throws Exception {
        PmAgentClient client = new PmAgentClient(mock(RunDao.class), mock(AgentExecutionService.class));
        assertNull(parseDelivery(client, """
                {"decision":"REPLAN","summary":"replan","failureCategory":"PLAN_DEFECT","remediationTasks":[]}
                """));
    }

    @Test
    void parseDelivery_replanWithValidRemediationTasksIsAccepted() throws Exception {
        PmAgentClient client = new PmAgentClient(mock(RunDao.class), mock(AgentExecutionService.class));
        PmDeliveryDecision decision = parseDelivery(client, """
                {"decision":"REPLAN","summary":"fix","failureCategory":"PLAN_DEFECT","remediationTasks":[
                {"taskKey":"R1","title":"fix","description":"d","agent":"backend-dev-agent","area":"backend","dependsOn":[],"fileScope":["backend/"],"acceptanceCriteria":["ok"]},
                {"taskKey":"V1","title":"verify","description":"v","agent":"test-agent","area":"validation","dependsOn":["R1"],"fileScope":["backend/"],"acceptanceCriteria":["all tests pass"]}
                ]}
                """);
        assertNotNull(decision);
        assertEquals(2, decision.remediationTasks().size());
    }

    @Test
    void parseDelivery_replanWithoutIndependentTestAgentIsRejected() throws Exception {
        PmAgentClient client = new PmAgentClient(mock(RunDao.class), mock(AgentExecutionService.class));

        assertNull(parseDelivery(client, """
                {"decision":"REPLAN","summary":"fix","failureCategory":"PLAN_DEFECT","remediationTasks":[
                {"taskKey":"R1","title":"fix","description":"d","agent":"backend-dev-agent","area":"backend","dependsOn":[],"fileScope":["backend/"],"acceptanceCriteria":["ok"]}
                ]}
                """));
    }

    @Test
    void parseDelivery_invalidDecisionStringReturnsNull() throws Exception {
        PmAgentClient client = new PmAgentClient(mock(RunDao.class), mock(AgentExecutionService.class));
        assertNull(parseDelivery(client, """
                {"decision":"MAYBE","summary":"x"}
                """));
    }

    private PmAgentClient.PmPlanResult parsePlan(PmAgentClient client, String json) throws Exception {
        Method method = PmAgentClient.class.getDeclaredMethod("parsePlanJson",
                String.class, String.class, String.class);
        method.setAccessible(true);
        return (PmAgentClient.PmPlanResult) method.invoke(client, json, "requirement-1", "loop-1");
    }

    private PmDeliveryDecision parseDelivery(PmAgentClient client, String json) throws Exception {
        Method method = PmAgentClient.class.getDeclaredMethod("parseDeliveryDecisionJson",
                String.class, PmAgentClient.DeliveryReviewInput.class);
        method.setAccessible(true);
        PmAgentClient.DeliveryReviewInput input =
                new PmAgentClient.DeliveryReviewInput(
                        "req-1", "loop-1", "task-1", "run-1", "T1", "t", "d", "backend",
                        "backend-dev-agent", "coding-task", true, List.of(), "{}", List.of());
        return (PmDeliveryDecision) method.invoke(client, json, input);
    }

    private PmAgentClient.PmAcceptanceResult parseAcceptance(PmAgentClient client, String json) throws Exception {
        Method method = PmAgentClient.class.getDeclaredMethod("parseAcceptanceJson", String.class);
        method.setAccessible(true);
        return (PmAgentClient.PmAcceptanceResult) method.invoke(client, json);
    }

    private PlanRevisionInput revisionInput() {
        return new PlanRevisionInput("req-1", "loop-1", 2, "plan-1", 1,
                "title", "description", "prd", "{\"goal\":\"base\"}",
                List.of(new PlanRevisionInput.CommentSnapshot(
                        "HUMAN", "HUMAN_FEEDBACK", "human", "完整评论内容")),
                List.of(new PlanRevisionInput.TaskSnapshot("task-be", "BE-1", "后端接口", "实现接口",
                        "backend-dev-agent", "backend", List.of(), List.of("backend/"), List.of("通过"),
                        "SUCCEEDED", "已完成接口实现")),
                List.of(new PlanRevisionInput.HandoffSnapshot("task-be", "run-be", "SUCCEEDED", "完成",
                        List.of("backend/src/Api.java"), "+ api", "全部步骤完成")));
    }
}
