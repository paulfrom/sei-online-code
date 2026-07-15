package com.changhong.onlinecode.service.validation;

import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.service.RequirementCommentService;
import com.changhong.onlinecode.service.agent.AgentExecutionRequest;
import com.changhong.onlinecode.service.agent.AgentExecutionResult;
import com.changhong.onlinecode.service.agent.AgentExecutionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidationLoopServiceTest {

    @Test
    void taskValidation_runsTestAgentInProjectWorkspaceAndRecordsReviewRun() {
        ExecutionPlanDao planDao = mock(ExecutionPlanDao.class);
        RequirementCommentService comments = mock(RequirementCommentService.class);
        AgentExecutionService agentExecutionService = mock(AgentExecutionService.class);
        when(agentExecutionService.execute(eq("test-agent"), any(AgentExecutionRequest.class)))
                .thenReturn(new AgentExecutionResult("run-1", """
                {"passed":true,"summary":"ok","commands":[{"command":"workspace-selected validation","exitCode":0,"result":"ok"}],"findings":[]}
                """, true, null));
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-1");
        plan.setPlanJson("{\"validation\":{\"mode\":\"test-agent\"}}");
        when(planDao.findOne("plan-1")).thenReturn(plan);

        ValidationLoopService service = new ValidationLoopService(planDao, comments, agentExecutionService);
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setRequirementId("requirement-1");
        task.setProjectId("project-1");
        task.setExecutionPlanId("plan-1");
        task.setLoopId("loop-1");
        task.setArea("backend");

        ValidationLoopService.ValidationOutcome outcome = service.validateTask(task);

        assertTrue(outcome.passed());
        verify(agentExecutionService).execute(eq("test-agent"),
                org.mockito.ArgumentMatchers.<AgentExecutionRequest>argThat(request ->
                        "project-1".equals(request.getProjectId())
                                && "requirement-1".equals(request.getRequirementId())
                                && "task-1".equals(request.getCodingTaskId())
                                && request.getPrompt().contains("scope=task")));
        verify(agentExecutionService).settleRun("run-1", RunState.SUCCEEDED, null);
        verify(comments).append(eq("requirement-1"), eq("loop-1"), any(), eq("test-agent"), any(), any(), any());
    }
}
