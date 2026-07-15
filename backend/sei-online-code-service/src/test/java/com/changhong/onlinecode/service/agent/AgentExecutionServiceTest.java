package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.agent.BuiltInSkillRegistry;
import com.changhong.onlinecode.agent.AgentWorkspace;
import com.changhong.onlinecode.agent.CliRunResult;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.AgentService;
import com.changhong.onlinecode.service.SkillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExecutionServiceTest {

    @TempDir
    Path workspace;

    @Test
    void execute_resolvesAgentConfigCreatesRunAndCallsRegistry() {
        AgentService agentService = mock(AgentService.class);
        CliRunnerRegistry registry = mock(CliRunnerRegistry.class);
        RunDao runDao = mock(RunDao.class);
        AgentRunRecorder recorder = mock(AgentRunRecorder.class);
        AgentExecutionService service = new AgentExecutionService(agentService, registry, runDao, recorder,
                mock(SkillService.class), mock(BuiltInSkillRegistry.class), mock(SkillMaterializer.class));

        Agent agent = new Agent();
        agent.setId("agent-1");
        agent.setName("test-agent");
        agent.setCliTool("codex");
        agent.setModel("gpt-5-codex");
        when(agentService.findByName("test-agent")).thenReturn(agent);

        AgentWorkspace agentWorkspace = mock(AgentWorkspace.class);
        when(agentWorkspace.pathString()).thenReturn(workspace.toString());
        when(registry.workspace("project-1")).thenReturn(agentWorkspace);

        Run run = new Run();
        run.setId("run-1");
        run.setState(RunState.RUNNING);
        when(recorder.createAgentRun(any())).thenReturn(run);

        CliRunResult cliResult = new CliRunResult();
        cliResult.setProcessSucceeded(true);
        cliResult.setOutput("{\"passed\":true}");
        when(registry.executeDetailed(eq(agentWorkspace), any(),
                org.mockito.ArgumentMatchers.contains("prompt"), any()))
                .thenReturn(CompletableFuture.completedFuture(cliResult));

        AgentExecutionRequest request = new AgentExecutionRequest();
        request.setProjectId("project-1");
        request.setRequirementId("requirement-1");
        request.setCodingTaskId("task-1");
        request.setPrompt("prompt");
        request.setTriggerSource(TriggerSource.AUTO);

        AgentExecutionResult result = service.execute("test-agent", request);

        assertTrue(result.succeeded());
        assertEquals("run-1", result.runId());
        assertEquals("{\"passed\":true}", result.output());

        ArgumentCaptor<AgentRunCreateCommand> commandCaptor =
                ArgumentCaptor.forClass(AgentRunCreateCommand.class);
        verify(recorder).createAgentRun(commandCaptor.capture());
        assertEquals("requirement-1", commandCaptor.getValue().getRequirementId());
        assertEquals("task-1", commandCaptor.getValue().getCodingTaskId());
        assertEquals("agent-1", commandCaptor.getValue().getAgentId());
        verify(registry).executeDetailed(eq(agentWorkspace), any(),
                org.mockito.ArgumentMatchers.contains("prompt"), any());
    }
}
