package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PmAgentClientTest {

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

    private PmAgentClient.PmPlanResult parsePlan(PmAgentClient client, String json) throws Exception {
        Method method = PmAgentClient.class.getDeclaredMethod("parsePlanJson",
                String.class, String.class, String.class);
        method.setAccessible(true);
        return (PmAgentClient.PmPlanResult) method.invoke(client, json, "requirement-1", "loop-1");
    }
}
