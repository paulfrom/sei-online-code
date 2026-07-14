package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.agent.CliRunnerRegistry;
import com.changhong.onlinecode.service.agent.PmAgentClient;
import com.changhong.onlinecode.service.validation.ValidationLoopService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RequirementAutomationService 单元测试。
 *
 * <p>验证 PRD 确认后真实调用 pm-agent 生成 ExecutionPlan，并在失败时写入 FAILURE 评论。</p>
 */
class RequirementAutomationServiceTest {

    private RequirementDao requirementDao;
    private CodingTaskDao codingTaskDao;
    private ExecutionPlanDao executionPlanDao;
    private CodingTaskScheduler codingTaskScheduler;
    private RequirementCommentService requirementCommentService;
    private RequirementDesignContextService requirementDesignContextService;
    private RunDao runDao;
    private RequirementDeliveryService requirementDeliveryService;
    private PmAgentClient pmAgentClient;
    private RequirementAutomationService service;
    private List<ExecutionPlanStatus> capturedPlanStatuses;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        codingTaskDao = mock(CodingTaskDao.class);
        executionPlanDao = mock(ExecutionPlanDao.class);
        codingTaskScheduler = mock(CodingTaskScheduler.class);
        requirementCommentService = mock(RequirementCommentService.class);
        requirementDesignContextService = mock(RequirementDesignContextService.class);
        runDao = mock(RunDao.class);
        requirementDeliveryService = mock(RequirementDeliveryService.class);
        pmAgentClient = mock(PmAgentClient.class);
        capturedPlanStatuses = new ArrayList<>();

        service = new RequirementAutomationService(requirementDao, codingTaskDao, codingTaskScheduler);
        service.setOptionalDependencies(executionPlanDao, requirementCommentService,
                requirementDesignContextService, runDao, requirementDeliveryService,
                pmAgentClient, new ObjectMapper());

        when(requirementDao.save(any(Requirement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(codingTaskDao.save(any(CodingTask.class))).thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> {
            ExecutionPlan plan = inv.getArgument(0);
            capturedPlanStatuses.add(plan.getStatus());
            return plan;
        }).when(executionPlanDao).save(any(ExecutionPlan.class));
        when(requirementCommentService.append(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    RequirementComment comment = new RequirementComment();
                    comment.setId("comment-" + System.nanoTime());
                    comment.setRequirementId(inv.getArgument(0));
                    comment.setLoopId(inv.getArgument(1));
                    comment.setAuthorType(inv.getArgument(2));
                    comment.setAuthorName(inv.getArgument(3));
                    comment.setCommentType(inv.getArgument(4));
                    comment.setContent(inv.getArgument(5));
                    comment.setMetadataJson(inv.getArgument(6));
                    return comment;
                });
    }

    @Test
    void startInitialLoop_pmAgentReturnsPlan_createsExecutionPlanAndTasks() {
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setProjectId("proj-1");
        requirement.setTitle("登录功能");
        requirement.setPrdContent("实现登录页面和接口");
        requirement.setAutomationStatus(RequirementAutomationStatus.IDLE);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);
        when(requirementCommentService.findByRequirementId("req-1")).thenReturn(List.of());
        when(executionPlanDao.findTopByRequirementIdOrderByVersionDesc("req-1")).thenReturn(null);

        RequirementDesignContext context = new RequirementDesignContext();
        context.setId("ctx-1");
        context.setWorkspaceMemoryId("wm-1");
        context.setContextStatus(com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus.READY);
        when(requirementDesignContextService.findCurrentByRequirement("req-1")).thenReturn(context);

        List<RequirementAutomationService.PlanTask> planTasks = List.of(
                new RequirementAutomationService.PlanTask(
                        "BE-001", "后端接口", "实现登录接口", "backend-dev-agent", "backend",
                        List.of(), List.of("backend/"), List.of("接口测试通过")),
                new RequirementAutomationService.PlanTask(
                        "FE-001", "前端页面", "实现登录页", "frontend-dev-agent", "frontend",
                        List.of("BE-001"), List.of("frontend/"))
        );
        PmAgentClient.PmPlanResult planResult = new PmAgentClient.PmPlanResult(
                "实现登录功能", planTasks, List.of("session 安全"), List.of());
        when(pmAgentClient.generatePlan(any(), any(), any(), any(), any(), any())).thenReturn(planResult);

        service.startInitialLoop("req-1");

        assertEquals(RequirementAutomationStatus.DEVELOPING, requirement.getAutomationStatus());
        assertNotNull(requirement.getActiveLoopId());

        verify(executionPlanDao, times(2)).save(any(ExecutionPlan.class));
        assertTrue(capturedPlanStatuses.contains(ExecutionPlanStatus.READY));
        assertTrue(capturedPlanStatuses.contains(ExecutionPlanStatus.DEVELOPING));

        ArgumentCaptor<RequirementComment> commentCaptor = ArgumentCaptor.forClass(RequirementComment.class);
        verify(requirementCommentService, times(1)).append(any(), any(),
                eq(RequirementCommentAuthorType.PM_AGENT), eq("pm-agent"),
                eq(RequirementCommentType.EXECUTION_PLAN), any(), any());

        ArgumentCaptor<CodingTask> taskCaptor = ArgumentCaptor.forClass(CodingTask.class);
        verify(codingTaskDao, times(2)).save(taskCaptor.capture());
        List<CodingTask> saved = taskCaptor.getAllValues();
        CodingTask be = saved.stream().filter(t -> "BE-001".equals(t.getPlanTaskKey())).findFirst().orElseThrow();
        assertEquals("backend", be.getArea());
        assertEquals("backend-dev-agent", be.getAssignedAgent());
        assertEquals(CodingTaskStatus.PENDING, be.getStatus());
        assertEquals(requirement.getActiveLoopId(), be.getLoopId());
        ArgumentCaptor<ExecutionPlan> planCaptor = ArgumentCaptor.forClass(ExecutionPlan.class);
        verify(executionPlanDao, times(2)).save(planCaptor.capture());
        assertTrue(planCaptor.getValue().getPlanJson().contains("接口测试通过"));
    }

    @Test
    void startInitialLoop_pmAgentFails_writesFailureAndSetsFailed() {
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setProjectId("proj-1");
        requirement.setTitle("登录功能");
        requirement.setAutomationStatus(RequirementAutomationStatus.IDLE);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);
        when(requirementCommentService.findByRequirementId("req-1")).thenReturn(List.of());
        when(pmAgentClient.generatePlan(any(), any(), any(), any(), any(), any())).thenReturn(null);

        service.startInitialLoop("req-1");

        assertEquals(RequirementAutomationStatus.FAILED, requirement.getAutomationStatus());
        verify(requirementCommentService).append(eq("req-1"), any(),
                eq(RequirementCommentAuthorType.SYSTEM), eq("system"),
                eq(RequirementCommentType.FAILURE), any(), eq(null));
        verify(codingTaskDao, never()).save(any(CodingTask.class));
        verify(codingTaskScheduler, never()).schedule(any());
    }

    @Test
    void onPlanTasksSettled_accepted_movesToDelivering() {
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setProjectId("proj-1");
        requirement.setActiveLoopId("loop-1");
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);

        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-1");
        plan.setRequirementId("req-1");
        plan.setLoopId("loop-1");
        plan.setVersion(1);
        plan.setStatus(ExecutionPlanStatus.DEVELOPING);
        plan.setPlanJson(buildPlanJson());
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc("req-1", "loop-1"))
                .thenReturn(plan);
        when(executionPlanDao.countByRequirementIdAndLoopId("req-1", "loop-1")).thenReturn(1L);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setRequirementId("req-1");
        task.setExecutionPlanId("plan-1");
        task.setLoopId("loop-1");
        task.setPlanTaskKey("BE-001");
        task.setStatus(CodingTaskStatus.SUCCEEDED);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));
        when(requirementCommentService.findByRequirementId("req-1")).thenReturn(List.of());

        PmAgentClient.PmAcceptanceResult acceptance = new PmAgentClient.PmAcceptanceResult(
                true, "验收通过", List.of("实现完整"), List.of());
        when(pmAgentClient.reviewAcceptance(any(), any(), any(), any(), any())).thenReturn(acceptance);

        service.onPlanTasksSettled("req-1");

        assertEquals(RequirementAutomationStatus.DELIVERING, requirement.getAutomationStatus());
        assertEquals(ExecutionPlanStatus.ACCEPTED, plan.getStatus());
        assertEquals("pm-agent", requirement.getAcceptedByAgent());
        verify(requirementCommentService).append(eq("req-1"), eq("loop-1"),
                eq(RequirementCommentAuthorType.PM_AGENT), eq("pm-agent"),
                eq(RequirementCommentType.ACCEPTANCE), any(), any());
    }

    @Test
    void startInitialLoop_withEventPublisher_dispatchesPlanningAsynchronously() {
        Requirement requirement = new Requirement();
        requirement.setId("req-async");
        requirement.setProjectId("proj-1");
        requirement.setAutomationStatus(RequirementAutomationStatus.PLANNING);
        when(requirementDao.findOne("req-async")).thenReturn(requirement);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        service.setEventPublisher(publisher);

        service.startInitialLoop("req-async");

        assertNotNull(requirement.getActiveLoopId());
        assertEquals(RequirementAutomationStatus.PLANNING, requirement.getAutomationStatus());
        verify(publisher).publishEvent(any(RequirementAutomationLoopEvent.class));
        verify(pmAgentClient, never()).generatePlan(any(), any(), any(), any(), any(), any());
    }

    @Test
    void executePreparedLoop_loopWasReplaced_ignoresPmPlanResult() {
        Requirement initial = new Requirement();
        initial.setId("req-stale-plan");
        initial.setProjectId("proj-1");
        initial.setActiveLoopId("loop-old");
        initial.setAutomationStatus(RequirementAutomationStatus.PLANNING);
        Requirement current = new Requirement();
        current.setId("req-stale-plan");
        current.setProjectId("proj-1");
        current.setActiveLoopId("loop-new");
        current.setAutomationStatus(RequirementAutomationStatus.PLANNING);
        when(requirementDao.findOne("req-stale-plan")).thenReturn(initial, current);
        when(requirementCommentService.findByRequirementId("req-stale-plan")).thenReturn(List.of());
        PmAgentClient.PmPlanResult result = new PmAgentClient.PmPlanResult(
                "goal", List.of(new RequirementAutomationService.PlanTask(
                "BE-1", "title", "desc", "backend-dev-agent", "backend", List.of(), List.of("backend/"))),
                List.of(), List.of());
        when(pmAgentClient.generatePlan(any(), any(), any(), any(), any(), any())).thenReturn(result);

        service.executePreparedLoop("req-stale-plan", "loop-old", ExecutionPlanType.INITIAL, "summary");

        verify(executionPlanDao, never()).save(any(ExecutionPlan.class));
        verify(codingTaskDao, never()).save(any(CodingTask.class));
    }

    @Test
    void onPlanTasksSettled_loopChangesDuringPlanValidation_doesNotRunAcceptance() {
        Requirement original = new Requirement();
        original.setId("req-1");
        original.setProjectId("proj-1");
        original.setActiveLoopId("loop-1");
        original.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        Requirement current = new Requirement();
        current.setId("req-1");
        current.setProjectId("proj-1");
        current.setActiveLoopId("loop-2");
        current.setAutomationStatus(RequirementAutomationStatus.PLANNING);
        when(requirementDao.findOne("req-1")).thenReturn(original, current);

        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-1");
        plan.setRequirementId("req-1");
        plan.setLoopId("loop-1");
        plan.setStatus(ExecutionPlanStatus.DEVELOPING);
        plan.setPlanJson(buildPlanJson());
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc("req-1", "loop-1"))
                .thenReturn(plan);
        CodingTask task = new CodingTask();
        task.setRequirementId("req-1");
        task.setExecutionPlanId("plan-1");
        task.setStatus(CodingTaskStatus.SUCCEEDED);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));

        ValidationLoopService validation = mock(ValidationLoopService.class);
        when(validation.validatePlan(original, plan))
                .thenReturn(new ValidationLoopService.ValidationOutcome(true, List.of()));
        service.setValidationLoopService(validation);

        service.onPlanTasksSettled("req-1");

        verify(pmAgentClient, never()).reviewAcceptance(any(), any(), any(), any(), any());
        verify(requirementDeliveryService, never()).deliver(any(), any());
    }

    @Test
    void stopAutomation_interruptsPlanCancelsRunsAndInvalidatesLoop() {
        Requirement requirement = new Requirement();
        requirement.setId("req-stop");
        requirement.setActiveLoopId("loop-old");
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        when(requirementDao.findOne("req-stop")).thenReturn(requirement);
        ExecutionPlan plan = new ExecutionPlan();
        plan.setLoopId("loop-old");
        plan.setStatus(ExecutionPlanStatus.DEVELOPING);
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc("req-stop", "loop-old"))
                .thenReturn(plan);
        Run run = new Run();
        run.setId("run-stop");
        run.setState(RunState.RUNNING);
        when(runDao.findByRequirementIdAndState("req-stop", RunState.RUNNING)).thenReturn(List.of(run));
        CliRunnerRegistry registry = mock(CliRunnerRegistry.class);
        service.setCliRunnerRegistry(registry);

        Requirement stopped = service.stopAutomation("req-stop");

        assertEquals(RequirementAutomationStatus.INTERRUPTED, stopped.getAutomationStatus());
        assertTrue(!"loop-old".equals(stopped.getActiveLoopId()));
        assertEquals(ExecutionPlanStatus.INTERRUPTED, plan.getStatus());
        assertTrue(Boolean.TRUE.equals(run.getCancelRequested()));
        verify(registry).cancel("run-stop");
        verify(requirementCommentService).append(eq("req-stop"), eq(stopped.getActiveLoopId()),
                eq(RequirementCommentAuthorType.SYSTEM), eq("system"),
                eq(RequirementCommentType.INTERRUPTION), any(), any());
    }

    @Test
    void humanComment_invalidatesRequirementContextBeforeReplanning() {
        Requirement requirement = new Requirement();
        requirement.setId("req-comment");
        requirement.setActiveLoopId("loop-old");
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        when(requirementDao.findOne("req-comment")).thenReturn(requirement);
        when(runDao.findByRequirementIdAndState("req-comment", RunState.RUNNING)).thenReturn(List.of());

        service.handleHumanComment("req-comment", "请调整接口", null);

        verify(requirementDesignContextService).invalidate("req-comment");
    }

    @Test
    void onPlanTasksSettled_remediationWithinLimit_createsNewPlanAndTasks() {
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setProjectId("proj-1");
        requirement.setActiveLoopId("loop-1");
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);

        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-1");
        plan.setRequirementId("req-1");
        plan.setLoopId("loop-1");
        plan.setVersion(1);
        plan.setStatus(ExecutionPlanStatus.DEVELOPING);
        plan.setPlanJson(buildPlanJson());
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc("req-1", "loop-1"))
                .thenReturn(plan);
        when(executionPlanDao.countByRequirementIdAndLoopId("req-1", "loop-1")).thenReturn(1L);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setRequirementId("req-1");
        task.setExecutionPlanId("plan-1");
        task.setLoopId("loop-1");
        task.setPlanTaskKey("BE-001");
        task.setStatus(CodingTaskStatus.FAILED);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));
        when(codingTaskDao.findByRequirementIdAndLoopIdAndPlanTaskKey("req-1", "loop-1", "BE-001"))
                .thenReturn(task);
        when(requirementCommentService.findByRequirementId("req-1")).thenReturn(List.of());

        List<RequirementAutomationService.PlanTask> remediationTasks = List.of(
                new RequirementAutomationService.PlanTask(
                        "BE-001", "修复后端接口", "修复登录接口", "backend-dev-agent", "backend",
                        List.of(), List.of("backend/")));
        PmAgentClient.PmAcceptanceResult acceptance = new PmAgentClient.PmAcceptanceResult(
                false, "需要修复", List.of("接口返回错误"), remediationTasks);
        when(pmAgentClient.reviewAcceptance(any(), any(), any(), any(), any())).thenReturn(acceptance);

        service.onPlanTasksSettled("req-1");

        assertEquals(RequirementAutomationStatus.DEVELOPING, requirement.getAutomationStatus());
        assertEquals(ExecutionPlanStatus.NEEDS_REMEDIATION, plan.getStatus());
        verify(executionPlanDao, times(4)).save(any(ExecutionPlan.class));
        verify(codingTaskDao).save(any(CodingTask.class));
        verify(requirementCommentService).append(eq("req-1"), eq("loop-1"),
                eq(RequirementCommentAuthorType.PM_AGENT), eq("pm-agent"),
                eq(RequirementCommentType.REMEDIATION), any(), any());
    }

    @Test
    void onPlanTasksSettled_remediationExceedsLimit_waitingHuman() {
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setProjectId("proj-1");
        requirement.setActiveLoopId("loop-1");
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);

        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-4");
        plan.setRequirementId("req-1");
        plan.setLoopId("loop-1");
        plan.setVersion(4);
        plan.setStatus(ExecutionPlanStatus.DEVELOPING);
        plan.setPlanJson(buildPlanJson());
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc("req-1", "loop-1"))
                .thenReturn(plan);
        when(executionPlanDao.countByRequirementIdAndLoopId("req-1", "loop-1")).thenReturn(4L);

        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setRequirementId("req-1");
        task.setExecutionPlanId("plan-4");
        task.setLoopId("loop-1");
        task.setPlanTaskKey("BE-001");
        task.setStatus(CodingTaskStatus.FAILED);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));
        when(requirementCommentService.findByRequirementId("req-1")).thenReturn(List.of());

        PmAgentClient.PmAcceptanceResult acceptance = new PmAgentClient.PmAcceptanceResult(
                false, "仍有问题", List.of("仍未修复"), List.of());
        when(pmAgentClient.reviewAcceptance(any(), any(), any(), any(), any())).thenReturn(acceptance);

        service.onPlanTasksSettled("req-1");

        assertEquals(RequirementAutomationStatus.WAITING_HUMAN, requirement.getAutomationStatus());
        assertEquals(ExecutionPlanStatus.NEEDS_REMEDIATION, plan.getStatus());
        verify(executionPlanDao, times(2)).save(any(ExecutionPlan.class));
        verify(codingTaskDao, never()).save(any(CodingTask.class));
    }

    private String buildPlanJson() {
        return """
                {
                  "goal": "实现登录功能",
                  "tasks": [
                    {
                      "taskKey": "BE-001",
                      "title": "后端接口",
                      "description": "实现登录接口",
                      "agent": "backend-dev-agent",
                      "area": "backend",
                      "dependsOn": [],
                      "fileScope": ["backend/"]
                    }
                  ],
                  "risks": [],
                  "validation": {"commands": []}
                }
                """;
    }
}
