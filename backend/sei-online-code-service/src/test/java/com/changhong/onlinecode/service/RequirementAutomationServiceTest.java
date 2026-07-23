package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.config.OcConfig;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.service.agent.AgentExecutionService;
import com.changhong.onlinecode.service.agent.PmAgentClient;
import com.changhong.onlinecode.service.validation.ValidationLoopService;
import com.changhong.onlinecode.service.revision.PlanRevisionRequestedEvent;
import com.changhong.onlinecode.service.revision.PlanRevisionStateService;
import com.changhong.onlinecode.service.revision.apply.EffectiveTaskGraph;
import com.changhong.onlinecode.service.revision.apply.EffectiveTaskGraphResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private ApplicationEventPublisher eventPublisher;
    private RequirementCommentService requirementCommentService;
    private RequirementDesignContextService requirementDesignContextService;
    private RunDao runDao;
    private RequirementDeliveryService requirementDeliveryService;
    private PmAgentClient pmAgentClient;
    private AgentExecutionService agentExecutionService;
    private ValidationLoopService validationLoopService;
    private FailureInfoSupport failureInfoSupport;
    private PlanRevisionStateService revisionStateService;
    private EffectiveTaskGraphResolver effectiveTaskGraphResolver;
    private OcConfig ocConfig;
    private RequirementAutomationService service;
    private List<ExecutionPlanStatus> capturedPlanStatuses;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        codingTaskDao = mock(CodingTaskDao.class);
        executionPlanDao = mock(ExecutionPlanDao.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        requirementCommentService = mock(RequirementCommentService.class);
        requirementDesignContextService = mock(RequirementDesignContextService.class);
        runDao = mock(RunDao.class);
        requirementDeliveryService = mock(RequirementDeliveryService.class);
        pmAgentClient = mock(PmAgentClient.class);
        agentExecutionService = mock(AgentExecutionService.class);
        validationLoopService = mock(ValidationLoopService.class);
        failureInfoSupport = mock(FailureInfoSupport.class);
        revisionStateService = mock(PlanRevisionStateService.class);
        effectiveTaskGraphResolver = mock(EffectiveTaskGraphResolver.class);
        com.changhong.onlinecode.service.review.TaskDeliveryReviewService taskDeliveryReviewService =
                mock(com.changhong.onlinecode.service.review.TaskDeliveryReviewService.class);
        // 默认：所有任务交付审阅已结算，允许进入计划级验收。针对门禁的专门测试可在用例内覆盖。
        when(taskDeliveryReviewService.allReviewsSettled(anyString(), anyString())).thenReturn(true);
        ocConfig = mock(OcConfig.class);
        when(ocConfig.isIncrementalCommentRevisionEnabled()).thenReturn(true);
        capturedPlanStatuses = new ArrayList<>();

        service = new RequirementAutomationService(requirementDao, codingTaskDao, eventPublisher,
                executionPlanDao, requirementCommentService, requirementDesignContextService,
                runDao, requirementDeliveryService, pmAgentClient, agentExecutionService,
                failureInfoSupport, revisionStateService,
                effectiveTaskGraphResolver, taskDeliveryReviewService, ocConfig);

        when(requirementDao.save(any(Requirement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(revisionStateService.request(any(), any(), any())).thenReturn(1L);
        when(effectiveTaskGraphResolver.resolve(any(ExecutionPlan.class))).thenAnswer(invocation -> {
            ExecutionPlan plan = invocation.getArgument(0);
            List<CodingTask> tasks = codingTaskDao.findByRequirementId(plan.getRequirementId()).stream()
                    .filter(task -> plan.getId().equals(task.getExecutionPlanId()))
                    .toList();
            return new EffectiveTaskGraph(0L, tasks, Map.of());
        });
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
        service.executePreparedLoop("req-1", requirement.getActiveLoopId(),
                ExecutionPlanType.INITIAL, "PRD 已确认，启动 PM 初始执行计划。");

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
    void resumeDevelopmentLoop_recreatesMissingTasksFromPersistedPlanWithoutNewLoop() {
        Requirement requirement = new Requirement();
        requirement.setId("req-recover");
        requirement.setProjectId("proj-1");
        requirement.setActiveLoopId("loop-recover");
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-recover");
        plan.setRequirementId("req-recover");
        plan.setLoopId("loop-recover");
        plan.setStatus(ExecutionPlanStatus.READY);
        plan.setPlanJson("{\"tasks\":[{\"taskKey\":\"BE-001\",\"title\":\"恢复后端任务\","
                + "\"description\":\"实现恢复\",\"agent\":\"backend-dev-agent\","
                + "\"area\":\"backend\",\"dependsOn\":[],\"fileScope\":[\"backend/\"]}]}");
        when(requirementDao.findOne("req-recover")).thenReturn(requirement);
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                "req-recover", "loop-recover")).thenReturn(plan);
        when(codingTaskDao.findByRequirementIdAndLoopIdAndPlanTaskKey(
                "req-recover", "loop-recover", "BE-001")).thenReturn(null);

        boolean recovered = service.resumeDevelopmentLoop("req-recover", "loop-recover");

        assertTrue(recovered);
        ArgumentCaptor<CodingTask> taskCaptor = ArgumentCaptor.forClass(CodingTask.class);
        verify(codingTaskDao).save(taskCaptor.capture());
        assertEquals("plan-recover", taskCaptor.getValue().getExecutionPlanId());
        assertEquals("loop-recover", taskCaptor.getValue().getLoopId());
        assertEquals(CodingTaskStatus.PENDING, taskCaptor.getValue().getStatus());
        assertEquals(ExecutionPlanStatus.DEVELOPING, plan.getStatus());
        assertEquals("loop-recover", requirement.getActiveLoopId());
    }

    @Test
    void resumeCurrentPlan_developingRequirement_republishesCurrentLoop() {
        Requirement requirement = new Requirement();
        requirement.setId("req-manual-resume");
        requirement.setProjectId("proj-1");
        requirement.setStatus(RequirementStatus.PRD_CONFIRMED);
        requirement.setActiveLoopId("loop-current");
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-current");
        plan.setRequirementId(requirement.getId());
        plan.setLoopId("loop-current");
        plan.setStatus(ExecutionPlanStatus.DEVELOPING);
        plan.setPlanJson("{\"tasks\":[{\"taskKey\":\"BE-001\",\"title\":\"待恢复任务\","
                + "\"description\":\"继续执行\",\"agent\":\"backend-dev-agent\","
                + "\"area\":\"backend\",\"dependsOn\":[],\"fileScope\":[\"backend/\"]}]}");
        CodingTask existing = new CodingTask();
        existing.setId("task-existing");
        existing.setStatus(CodingTaskStatus.SUCCEEDED);
        when(requirementDao.findOne(requirement.getId())).thenReturn(requirement);
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                requirement.getId(), "loop-current")).thenReturn(plan);
        when(codingTaskDao.findByRequirementIdAndLoopIdAndPlanTaskKey(
                requirement.getId(), "loop-current", "BE-001")).thenReturn(existing);

        Requirement resumed = service.resumeCurrentPlan(requirement.getId());

        assertEquals(requirement, resumed);
        verify(eventPublisher).publishEvent(
                new CodingTaskSchedulingEvents.ScheduleRequested(requirement.getId()));
        assertEquals(CodingTaskStatus.SUCCEEDED, existing.getStatus());
        verify(codingTaskDao).save(existing);
    }

    @Test
    void resumeCurrentPlan_nonDevelopingRequirement_rejects() {
        Requirement requirement = new Requirement();
        requirement.setId("req-not-developing");
        requirement.setStatus(RequirementStatus.PRD_CONFIRMED);
        requirement.setAutomationStatus(RequirementAutomationStatus.WAITING_HUMAN);
        when(requirementDao.findOne(requirement.getId())).thenReturn(requirement);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.resumeCurrentPlan(requirement.getId()));

        verify(eventPublisher, never()).publishEvent(any());
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
        service.executePreparedLoop("req-1", requirement.getActiveLoopId(),
                ExecutionPlanType.INITIAL, "PRD 已确认，启动 PM 初始执行计划。");

        assertEquals(RequirementAutomationStatus.FAILED, requirement.getAutomationStatus());
        verify(requirementCommentService).append(eq("req-1"), any(),
                eq(RequirementCommentAuthorType.SYSTEM), eq("system"),
                eq(RequirementCommentType.FAILURE), any(), eq(null));
        verify(codingTaskDao, never()).save(any(CodingTask.class));
        verify(eventPublisher, never()).publishEvent(any(CodingTaskSchedulingEvents.ScheduleRequested.class));
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
        service.startInitialLoop("req-async");

        assertNotNull(requirement.getActiveLoopId());
        assertEquals(RequirementAutomationStatus.PLANNING, requirement.getAutomationStatus());
        verify(eventPublisher).publishEvent(any(RequirementAutomationLoopEvent.class));
        verify(pmAgentClient, never()).generatePlan(any(), any(), any(), any(), any(), any());
    }

    @Test
    void startInitialLoop_usesNewTransactionAfterPrdCommit() throws NoSuchMethodException {
        Method method = RequirementAutomationService.class.getMethod("startInitialLoop", String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
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

        service.onPlanTasksSettled("req-1");

        verify(validationLoopService, never()).validatePlan(any(), any());
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
        Requirement stopped = service.stopAutomation("req-stop");

        assertEquals(RequirementAutomationStatus.INTERRUPTED, stopped.getAutomationStatus());
        assertTrue(!"loop-old".equals(stopped.getActiveLoopId()));
        assertEquals(ExecutionPlanStatus.INTERRUPTED, plan.getStatus());
        assertTrue(Boolean.TRUE.equals(run.getCancelRequested()));
        verify(agentExecutionService).cancel("run-stop");
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
        verify(revisionStateService).request(eq("req-comment"), eq("loop-old"), any());
        verify(runDao, never()).findByRequirementIdAndState(any(), any());
        verify(agentExecutionService, never()).cancel(any());
        assertEquals("loop-old", requirement.getActiveLoopId());
        ArgumentCaptor<PlanRevisionRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(PlanRevisionRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(1L, eventCaptor.getValue().revisionSeq());
        assertEquals("loop-old", eventCaptor.getValue().loopId());
    }

    @Test
    void humanComment_featureDisabledFallsBackToLegacyInterruptedNewLoop() {
        when(ocConfig.isIncrementalCommentRevisionEnabled()).thenReturn(false);
        Requirement requirement = new Requirement();
        requirement.setId("req-legacy");
        requirement.setActiveLoopId("loop-old");
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        when(requirementDao.findOne("req-legacy")).thenReturn(requirement);
        ExecutionPlan plan = new ExecutionPlan();
        plan.setStatus(ExecutionPlanStatus.DEVELOPING);
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc("req-legacy", "loop-old"))
                .thenReturn(plan);
        Run run = new Run();
        run.setId("run-legacy");
        run.setState(RunState.RUNNING);
        when(runDao.findByRequirementIdAndState("req-legacy", RunState.RUNNING)).thenReturn(List.of(run));

        service.handleHumanComment("req-legacy", "使用旧路径", null);

        verify(revisionStateService, never()).request(any(), any(), any());
        verify(agentExecutionService).cancel("run-legacy");
        assertEquals(ExecutionPlanStatus.INTERRUPTED, plan.getStatus());
        assertTrue(!"loop-old".equals(requirement.getActiveLoopId()));
        verify(eventPublisher).publishEvent(any(RequirementAutomationLoopEvent.class));
    }

    @Test
    void humanComment_completedRequirementRetainsNewLoopBehavior() {
        Requirement requirement = new Requirement();
        requirement.setId("req-completed");
        requirement.setActiveLoopId("loop-completed");
        requirement.setAutomationStatus(RequirementAutomationStatus.COMPLETED);
        when(requirementDao.findOne("req-completed")).thenReturn(requirement);

        service.handleHumanComment("req-completed", "增加导出功能", null);

        assertTrue(!"loop-completed".equals(requirement.getActiveLoopId()));
        assertEquals(RequirementAutomationStatus.PLANNING, requirement.getAutomationStatus());
        verify(revisionStateService, never()).request(any(), any(), any());
        verify(eventPublisher).publishEvent(any(RequirementAutomationLoopEvent.class));
    }

    @Test
    void humanComment_rollbackDoesNotPublishRevisionRequest() {
        Requirement requirement = new Requirement();
        requirement.setId("req-rollback");
        requirement.setActiveLoopId("loop-rollback");
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        when(requirementDao.findOne("req-rollback")).thenReturn(requirement);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.handleHumanComment("req-rollback", "只在提交后处理", null);
            verify(eventPublisher, never()).publishEvent(any(PlanRevisionRequestedEvent.class));
            // Clearing synchronization without invoking afterCommit models transaction rollback.
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(eventPublisher, never()).publishEvent(any(PlanRevisionRequestedEvent.class));
    }

    @Test
    void humanComment_failedRequirementStartsNewLoopAndResetsFailure() {
        Requirement requirement = new Requirement();
        requirement.setId("req-failed-comment");
        requirement.setActiveLoopId("loop-failed");
        requirement.setAutomationStatus(RequirementAutomationStatus.FAILED);
        requirement.setRetryCount(3);
        requirement.setNextRetryAt(new java.util.Date(System.currentTimeMillis() + 60_000L));
        when(requirementDao.findOne("req-failed-comment")).thenReturn(requirement);
        doAnswer(inv -> {
            Requirement failedRequirement = inv.getArgument(0);
            failedRequirement.setRetryCount(0);
            failedRequirement.setNextRetryAt(null);
            return null;
        }).when(failureInfoSupport).clearRequirementFailure(any(Requirement.class));

        service.handleHumanComment("req-failed-comment", "修复失败原因后重新规划", null);

        assertEquals(RequirementAutomationStatus.PLANNING, requirement.getAutomationStatus());
        assertTrue(!"loop-failed".equals(requirement.getActiveLoopId()));
        assertEquals(0, requirement.getRetryCount());
        assertNull(requirement.getNextRetryAt());
        verify(failureInfoSupport).clearRequirementFailure(requirement);
        verify(requirementDesignContextService).invalidate("req-failed-comment");
        verify(requirementCommentService).append(eq("req-failed-comment"), eq("loop-failed"),
                eq(RequirementCommentAuthorType.HUMAN), eq("human"),
                eq(RequirementCommentType.HUMAN_FEEDBACK), eq("修复失败原因后重新规划"), eq(null));

        ArgumentCaptor<RequirementAutomationLoopEvent> eventCaptor =
                ArgumentCaptor.forClass(RequirementAutomationLoopEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        RequirementAutomationLoopEvent event = eventCaptor.getValue();
        assertEquals("req-failed-comment", event.requirementId());
        assertEquals(requirement.getActiveLoopId(), event.loopId());
        assertEquals(ExecutionPlanType.CHANGE_REQUEST, event.planType());
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
