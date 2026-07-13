package com.changhong.onlinecode.service.validation;

import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.RunType;
import com.changhong.onlinecode.dto.enums.WorkspaceSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.AgentService;
import com.changhong.onlinecode.service.RequirementCommentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidationLoopServiceTest {

    @Test
    void taskValidation_prefersExecutionPlanCommandAndRecordsValidationRun() {
        ValidationCommandExecutor executor = mock(ValidationCommandExecutor.class);
        RunDao runDao = mock(RunDao.class);
        ExecutionPlanDao planDao = mock(ExecutionPlanDao.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        RequirementCommentService comments = mock(RequirementCommentService.class);
        AgentService agents = mock(AgentService.class);
        CliRunnerRegistry runners = mock(CliRunnerRegistry.class);
        AtomicInteger ids = new AtomicInteger();
        when(runDao.save(any(Run.class))).thenAnswer(invocation -> {
            Run run = invocation.getArgument(0);
            if (run.getId() == null) run.setId("run-" + ids.incrementAndGet());
            return run;
        });
        when(workspaceManager.resolve("project-1"))
                .thenReturn(new WorkspaceResolveResult(".", true, WorkspaceSource.SCAFFOLD));
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-1");
        plan.setPlanJson("{\"validation\":{\"commands\":[{\"area\":\"backend\",\"command\":\"gradle check\"}]}}");
        when(planDao.findOne("plan-1")).thenReturn(plan);
        Project project = new Project();
        project.setId("project-1");
        project.setValidationConfig("{\"commands\":[\"fallback\"]}");
        when(projectDao.findOne("project-1")).thenReturn(project);
        when(executor.execute(any(), eq("gradle check")))
                .thenReturn(new ValidationCommandExecutor.ValidationResult(0, "ok", "", Duration.ofMillis(10)));

        ValidationLoopService service = new ValidationLoopService(executor, runDao, planDao, projectDao,
                workspaceManager, comments, agents, runners, new ObjectMapper());
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setRequirementId("requirement-1");
        task.setProjectId("project-1");
        task.setExecutionPlanId("plan-1");
        task.setLoopId("loop-1");
        task.setArea("backend");

        ValidationLoopService.ValidationOutcome outcome = service.validateTask(task);

        assertTrue(outcome.passed());
        verify(executor).execute(any(), eq("gradle check"));
        verify(runDao, atLeastOnce()).save(org.mockito.ArgumentMatchers.<Run>argThat(
                run -> run.getRunType() == RunType.VALIDATION_COMMAND));
        verify(comments).append(eq("requirement-1"), eq("loop-1"), any(), eq("test-agent"), any(), any(), any());
    }
}
