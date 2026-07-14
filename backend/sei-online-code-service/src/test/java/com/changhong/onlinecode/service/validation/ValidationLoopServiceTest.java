package com.changhong.onlinecode.service.validation;

import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.RunType;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.AgentService;
import com.changhong.onlinecode.service.RequirementCommentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidationLoopServiceTest {

    @TempDir
    Path workspace;

    @Test
    void taskValidation_runsTestAgentInProjectWorkspaceAndRecordsReviewRun() {
        RunDao runDao = mock(RunDao.class);
        ExecutionPlanDao planDao = mock(ExecutionPlanDao.class);
        RequirementCommentService comments = mock(RequirementCommentService.class);
        AgentService agents = mock(AgentService.class);
        CliRunnerRegistry runners = mock(CliRunnerRegistry.class);
        com.changhong.onlinecode.service.RunNumberService runNumberService =
                mock(com.changhong.onlinecode.service.RunNumberService.class);
        com.changhong.onlinecode.agent.AgentWorkspace agentWorkspace =
                mock(com.changhong.onlinecode.agent.AgentWorkspace.class);
        AtomicInteger ids = new AtomicInteger();
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            if (run.getId() == null) run.setId("run-" + ids.incrementAndGet());
            return run;
        });
        when(runNumberService.assign(any(Run.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentWorkspace.path()).thenReturn(workspace);
        when(agentWorkspace.pathString()).thenReturn(workspace.toString());
        when(runners.workspace("project-1")).thenReturn(agentWorkspace);
        when(runners.execute(eq(agentWorkspace), eq("codex"), eq("requirement-1"), eq("task-1"),
                eq("run-1"), any(), eq(null), eq(null)))
                .thenReturn(CompletableFuture.completedFuture("""
                        {"passed":true,"summary":"ok","commands":[{"command":"workspace-selected validation","exitCode":0,"result":"ok"}],"findings":[]}
                        """));
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-1");
        plan.setPlanJson("{\"validation\":{\"mode\":\"test-agent\"}}");
        when(planDao.findOne("plan-1")).thenReturn(plan);
        Agent agent = new Agent();
        agent.setName("test-agent");
        agent.setCliTool("codex");
        when(agents.findByName("test-agent")).thenReturn(agent);

        ValidationLoopService service = new ValidationLoopService(runDao, planDao, comments, agents, runners,
                runNumberService, new ObjectMapper());
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setRequirementId("requirement-1");
        task.setProjectId("project-1");
        task.setExecutionPlanId("plan-1");
        task.setLoopId("loop-1");
        task.setArea("backend");

        ValidationLoopService.ValidationOutcome outcome = service.validateTask(task);

        assertTrue(outcome.passed());
        verify(runners).execute(eq(agentWorkspace), eq("codex"), eq("requirement-1"), eq("task-1"),
                eq("run-1"), any(), eq(null), eq(null));
        verify(runDao, atLeastOnce()).save(org.mockito.ArgumentMatchers.<Run>argThat(
                run -> run.getRunType() == RunType.TEST_REVIEW));
        verify(comments).append(eq("requirement-1"), eq("loop-1"), any(), eq("test-agent"), any(), any(), any());
    }
}
