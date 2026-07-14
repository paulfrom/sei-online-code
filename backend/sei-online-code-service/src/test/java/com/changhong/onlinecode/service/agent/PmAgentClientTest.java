package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.agent.CliRunner;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.WorkspaceSource;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.AgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PmAgentClientTest {

    @TempDir
    Path workspace;

    @Test
    void generatePlan_passesPersistedRunIdToRunnerAndRejectsCancelledResult() {
        AgentService agentService = mock(AgentService.class);
        CliRunnerRegistry registry = mock(CliRunnerRegistry.class);
        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        RunDao runDao = mock(RunDao.class);
        com.changhong.onlinecode.agent.AgentWorkspace agentWorkspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        when(agentWorkspace.path()).thenReturn(workspace);
        when(agentWorkspace.pathString()).thenReturn(workspace.toString());
        AtomicReference<Run> savedRun = new AtomicReference<>();

        Agent agent = new Agent();
        agent.setName("pm-agent");
        agent.setCliTool("codex");
        when(agentService.findByName("pm-agent")).thenReturn(agent);
        when(registry.workspace("project-1")).thenReturn(agentWorkspace);
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            if (run.getId() == null) run.setId("run-1");
            savedRun.set(run);
            return run;
        });
        when(runDao.findOne("run-1")).thenAnswer(invocation -> savedRun.get());
        when(registry.execute(eq(agentWorkspace), eq("codex"), eq("project-1"), eq(null),
                eq("run-1"), any(), eq(null), eq(null))).thenAnswer(invocation -> {
            savedRun.get().setCancelRequested(Boolean.TRUE);
            return CompletableFuture.completedFuture("""
                    {"goal":"g","tasks":[{"taskKey":"BE-1","title":"t","description":"d",
                    "agent":"backend-dev-agent","area":"backend","dependsOn":[],"fileScope":["backend/"]}],
                    "risks":[],"validation":{"commands":[]}}
                    """);
        });

        PmAgentClient client = new PmAgentClient(agentService, registry, workspaceManager, runDao,
                new ObjectMapper());
        Requirement requirement = new Requirement();
        requirement.setId("requirement-1");
        requirement.setProjectId("project-1");

        PmAgentClient.PmPlanResult result = client.generatePlan(requirement, "loop-1",
                ExecutionPlanType.INITIAL, null, List.of(), null);

        assertNull(result);
        assertEquals(RunState.CANCELLED, savedRun.get().getState());
        verify(registry).execute(eq(agentWorkspace), eq("codex"), eq("project-1"),
                eq(null), eq("run-1"), any(), eq(null), eq(null));
    }

    @Test
    void parsePlan_rejectsInvalidAgentAreaDuplicateKeysAndInvalidDag() throws Exception {
        PmAgentClient client = new PmAgentClient(mock(AgentService.class), mock(CliRunnerRegistry.class),
                mock(WorkspaceManager.class), mock(RunDao.class), new ObjectMapper());

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
        PmAgentClient client = new PmAgentClient(mock(AgentService.class), mock(CliRunnerRegistry.class),
                mock(WorkspaceManager.class), mock(RunDao.class), new ObjectMapper());
        PmAgentClient.PmPlanResult result = parsePlan(client, """
                {"goal":"g","tasks":[{"taskKey":"BE-1","title":"a","agent":"backend-dev-agent",
                "area":"backend","dependsOn":[],"fileScope":["backend/"],
                "acceptanceCriteria":["接口测试通过"]}],"risks":[],"validation":{"commands":[]}}
                """);

        assertNotNull(result);
        assertEquals(List.of("接口测试通过"), result.tasks().get(0).acceptanceCriteria());
    }

    private PmAgentClient.PmPlanResult parsePlan(PmAgentClient client, String json) throws Exception {
        Method method = PmAgentClient.class.getDeclaredMethod("parsePlanJson",
                String.class, String.class, String.class);
        method.setAccessible(true);
        return (PmAgentClient.PmPlanResult) method.invoke(client, json, "requirement-1", "loop-1");
    }
}
