package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementDesignContextDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.DeliveryFailureCategory;
import com.changhong.onlinecode.dto.enums.ExecutionPlanStatus;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.TaskDeliveryReview;
import com.changhong.onlinecode.service.review.TaskDeliveryReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CompensationService.recoverDevelopment 收敛规则回归测试（方案 §8 / TEST-RUN-001）。
 *
 * <p>验证：VALIDATION_FAILED 不被自动恢复；未审阅的失败只补发审阅事件不重试；
 * 已 RETRY 决策的 TRANSIENT_INFRA 可恢复；WAITING_HUMAN 需求不被补偿；
 * 补偿器不调用 markRetrying（retryCount 不重复增加）。</p>
 */
class CompensationDevelopmentRulesTest {

    private RequirementDao requirementDao;
    private ExecutionPlanDao executionPlanDao;
    private CodingTaskDao codingTaskDao;
    private FailureInfoSupport failureInfoSupport;
    private TaskDeliveryReviewService taskDeliveryReviewService;
    private RequirementAutomationService automationService;
    private CompensationService service;

    @BeforeEach
    void setUp() {
        requirementDao = mock(RequirementDao.class);
        RequirementDesignContextDao requirementDesignContextDao = mock(RequirementDesignContextDao.class);
        executionPlanDao = mock(ExecutionPlanDao.class);
        codingTaskDao = mock(CodingTaskDao.class);
        RunDao runDao = mock(RunDao.class);
        RequirementAgentService requirementAgentService = mock(RequirementAgentService.class);
        automationService = mock(RequirementAutomationService.class);
        RequirementDeliveryService deliveryService = mock(RequirementDeliveryService.class);
        failureInfoSupport = mock(FailureInfoSupport.class);
        CompensationLogService compensationLogService = mock(CompensationLogService.class);
        RequirementCommentService requirementCommentService = mock(RequirementCommentService.class);
        com.changhong.onlinecode.service.progress.ProgressReconciler progressReconciler =
                mock(com.changhong.onlinecode.service.progress.ProgressReconciler.class);
        com.changhong.onlinecode.service.progress.ProgressService progressService =
                mock(com.changhong.onlinecode.service.progress.ProgressService.class);
        com.changhong.onlinecode.service.agent.AgentExecutionService agentExecutionService =
                mock(com.changhong.onlinecode.service.agent.AgentExecutionService.class);
        taskDeliveryReviewService = mock(TaskDeliveryReviewService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        com.changhong.onlinecode.config.OcConfig ocConfig = mock(com.changhong.onlinecode.config.OcConfig.class);
        when(ocConfig.getRunTimeoutMinutes()).thenReturn(30L);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        service = new CompensationService(requirementDao, requirementDesignContextDao, executionPlanDao,
                codingTaskDao, runDao, requirementAgentService, automationService, deliveryService,
                failureInfoSupport, compensationLogService, requirementCommentService, progressReconciler,
                progressService, agentExecutionService, taskDeliveryReviewService, eventPublisher,
                ocConfig, transactionManager);

        when(codingTaskDao.save(any(com.changhong.onlinecode.entity.CodingTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(failureInfoSupport.canRetry(any(CodingTask.class), any(Date.class))).thenReturn(true);
    }

    private Requirement developingRequirement() {
        Requirement r = new Requirement();
        r.setId("req-1");
        r.setStatus(RequirementStatus.PRD_CONFIRMED);
        r.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        r.setActiveLoopId("loop-1");
        return r;
    }

    private ExecutionPlan developingPlan() {
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-1");
        plan.setLoopId("loop-1");
        plan.setStatus(ExecutionPlanStatus.DEVELOPING);
        return plan;
    }

    private CodingTask failedTask(String id, CodingTaskStatus status) {
        CodingTask task = new CodingTask();
        task.setId(id);
        task.setRequirementId("req-1");
        task.setExecutionPlanId("plan-1");
        task.setLoopId("loop-1");
        task.setStatus(status);
        task.setRetryCount(1);
        return task;
    }

    private TaskDeliveryReview decidedReview(TaskDeliveryReviewDecision decision, DeliveryFailureCategory category) {
        TaskDeliveryReview review = new TaskDeliveryReview();
        review.setStatus(TaskDeliveryReviewStatus.DECIDED);
        review.setDecision(decision);
        review.setFailureCategory(category);
        review.setDeliveryRunId("run-x");
        return review;
    }

    @Test
    void validationFailedTask_isNeverAutoRecoveredEvenWithRetryDecision() {
        // 方案 §8 硬约束：VALIDATION_FAILED 不被自动补偿，即使 PM 错误地 RETRY。
        Requirement req = developingRequirement();
        ExecutionPlan plan = developed(req, plan1 -> {});
        CodingTask task = failedTask("task-1", CodingTaskStatus.VALIDATION_FAILED);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));
        when(taskDeliveryReviewService.findFirstByCodingTaskId("task-1"))
                .thenReturn(Optional.of(decidedReview(TaskDeliveryReviewDecision.RETRY,
                        DeliveryFailureCategory.TRANSIENT_INFRA)));

        service.compensateRequirementLoops(new Date());

        verify(codingTaskDao, never()).updateStatusIfMatch(eq("task-1"), any(), any());
        verify(failureInfoSupport, never()).markRetrying(any(com.changhong.onlinecode.entity.CodingTask.class), any(), any());
    }

    @Test
    void failedTaskWithRetryDecisionAndTransientInfra_isRecoveredWithoutMarkRetrying() {
        // 方案 §8 / §5.3：TRANSIENT_INFRA + RETRY 允许恢复；补偿器不调用 markRetrying（retryCount 已在 PM RETRY 时 +1）。
        Requirement req = developingRequirement();
        developed(req, p -> {});
        CodingTask task = failedTask("task-2", CodingTaskStatus.FAILED);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));
        when(taskDeliveryReviewService.findFirstByCodingTaskId("task-2"))
                .thenReturn(Optional.of(decidedReview(TaskDeliveryReviewDecision.RETRY,
                        DeliveryFailureCategory.TRANSIENT_INFRA)));
        when(codingTaskDao.updateStatusIfMatch("task-2", CodingTaskStatus.FAILED, CodingTaskStatus.PENDING))
                .thenReturn(1);

        service.compensateRequirementLoops(new Date());

        verify(codingTaskDao).updateStatusIfMatch("task-2", CodingTaskStatus.FAILED, CodingTaskStatus.PENDING);
        assertEquals(CodingTaskStatus.PENDING, task.getStatus());
        verify(failureInfoSupport, never()).markRetrying(any(com.changhong.onlinecode.entity.CodingTask.class), any(), any());
        verify(automationService).resumeDevelopmentLoop("req-1", "loop-1");
    }

    @Test
    void failedTaskWithoutReview_repostsReviewEventButDoesNotRetry() {
        // 未审阅的失败任务：补发 PENDING review 事件，不自动恢复。
        Requirement req = developingRequirement();
        developed(req, p -> {});
        CodingTask task = failedTask("task-3", CodingTaskStatus.FAILED);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));
        TaskDeliveryReview pending = new TaskDeliveryReview();
        pending.setId("review-3");
        pending.setStatus(TaskDeliveryReviewStatus.PENDING);
        pending.setDeliveryRunId("run-3");
        when(taskDeliveryReviewService.findFirstByCodingTaskId("task-3")).thenReturn(Optional.of(pending));

        service.compensateRequirementLoops(new Date());

        verify(codingTaskDao, never()).updateStatusIfMatch(anyString(), any(), any());
        // 未决 review 阻塞，不应触发 resumeDevelopmentLoop（避免在门禁下空转）。
        verify(automationService, never()).resumeDevelopmentLoop(anyString(), anyString());
    }

    @Test
    void waitingHumanRequirement_isNeverCompensated() {
        Requirement req = developingRequirement();
        req.setAutomationStatus(RequirementAutomationStatus.WAITING_HUMAN);
        when(requirementDao.findByStatus(RequirementStatus.PRD_CONFIRMED)).thenReturn(List.of(req));
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc("req-1", "loop-1"))
                .thenReturn(developingPlan());
        CodingTask task = failedTask("task-4", CodingTaskStatus.FAILED);
        when(codingTaskDao.findByRequirementId("req-1")).thenReturn(List.of(task));
        when(taskDeliveryReviewService.findFirstByCodingTaskId("task-4"))
                .thenReturn(Optional.of(decidedReview(TaskDeliveryReviewDecision.RETRY,
                        DeliveryFailureCategory.TRANSIENT_INFRA)));

        service.compensateRequirementLoops(new Date());

        verify(codingTaskDao, never()).updateStatusIfMatch(anyString(), any(), any());
        verify(automationService, never()).resumeDevelopmentLoop(anyString(), anyString());
    }

    // helper: set up the common plan/requirement stubs used across cases.
    private interface PlanConfigurer { void configure(ExecutionPlan plan); }

    private ExecutionPlan developed(Requirement req, PlanConfigurer cfg) {
        when(requirementDao.findByStatus(RequirementStatus.PRD_CONFIRMED)).thenReturn(List.of(req));
        ExecutionPlan plan = developingPlan();
        cfg.configure(plan);
        when(executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc("req-1", "loop-1"))
                .thenReturn(plan);
        when(executionPlanDao.findByRequirementIdAndLoopId("req-1", "loop-1")).thenReturn(List.of());
        return plan;
    }
}
