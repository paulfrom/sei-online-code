package com.changhong.onlinecode.service.review;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.DeliveryFailureCategory;
import com.changhong.onlinecode.dto.enums.EvidenceCompleteness;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.RunEvidence;
import com.changhong.onlinecode.entity.TaskDeliveryReview;
import com.changhong.onlinecode.service.CodingTaskSchedulingEvents;
import com.changhong.onlinecode.service.FailureInfoSupport;
import com.changhong.onlinecode.service.RequirementAutomationService;
import com.changhong.onlinecode.service.RequirementCommentService;
import com.changhong.onlinecode.service.RequirementDesignContextService;
import com.changhong.onlinecode.service.agent.AgentExecutionDeferredException;
import com.changhong.onlinecode.service.agent.PmDeliveryDecision;
import com.changhong.onlinecode.service.evidence.AgentBehaviorMemoryService;
import com.changhong.onlinecode.service.evidence.RunArtifactService;
import com.changhong.onlinecode.service.evidence.RunEvidenceService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskDeliveryReviewOrchestratorTest {

    @Test
    void failedDeliveryApproveIsConvertedToWaitingHumanWithoutResumingScheduling() {
        TaskDeliveryReviewService reviewService = mock(TaskDeliveryReviewService.class);
        RequirementDao requirementDao = mock(RequirementDao.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        RequirementCommentService commentService = mock(RequirementCommentService.class);
        TaskDeliveryReviewOrchestrator orchestrator = new TaskDeliveryReviewOrchestrator(
                reviewService, mock(CodingTaskDao.class), requirementDao, mock(ExecutionPlanDao.class),
                mock(RunDao.class), commentService, mock(RequirementDesignContextService.class), mock(FailureInfoSupport.class),
                mock(RequirementAutomationService.class), publisher,
                mock(org.springframework.transaction.PlatformTransactionManager.class));

        TaskDeliveryReview review = new TaskDeliveryReview();
        review.setDeliverySucceeded(false);
        review.setStatus(TaskDeliveryReviewStatus.REVIEWING);
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setRequirementId("req-1");
        task.setLoopId("loop-1");
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setActiveLoopId("loop-1");
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);

        doAnswer(invocation -> {
            review.setDecision(TaskDeliveryReviewDecision.WAIT_HUMAN);
            review.setStatus(TaskDeliveryReviewStatus.WAITING_HUMAN);
            return TaskDeliveryReviewDecision.WAIT_HUMAN;
        }).when(reviewService).recordDecision(any(), any(), any(), any(), any());

        PmDeliveryDecision illegalApprove = new PmDeliveryDecision(
                TaskDeliveryReviewDecision.APPROVE, "approve failed delivery",
                DeliveryFailureCategory.VALIDATION_FAILED, List.of(), null, List.of());

        orchestrator.applyDecision(review, task, requirement, null, illegalApprove);

        assertEquals(RequirementAutomationStatus.WAITING_HUMAN, requirement.getAutomationStatus());
        verify(publisher, never()).publishEvent(new CodingTaskSchedulingEvents.ScheduleRequested("req-1"));
        verify(requirementDao).save(requirement);
    }

    @Test
    void deferredPmReviewIsRequeuedInsteadOfMovingRequirementToWaitingHuman() {
        TaskDeliveryReviewService reviewService = mock(TaskDeliveryReviewService.class);
        CodingTaskDao codingTaskDao = mock(CodingTaskDao.class);
        RequirementDao requirementDao = mock(RequirementDao.class);
        RequirementAutomationService automationService = mock(RequirementAutomationService.class);
        TaskDeliveryReviewOrchestrator orchestrator = new TaskDeliveryReviewOrchestrator(
                reviewService, codingTaskDao, requirementDao, mock(ExecutionPlanDao.class),
                mock(RunDao.class), mock(RequirementCommentService.class), mock(RequirementDesignContextService.class),
                mock(FailureInfoSupport.class), automationService, mock(ApplicationEventPublisher.class),
                mock(org.springframework.transaction.PlatformTransactionManager.class));

        TaskDeliveryReview review = new TaskDeliveryReview();
        review.setId("review-1");
        review.setCodingTaskId("task-1");
        review.setStatus(TaskDeliveryReviewStatus.PENDING);
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setRequirementId("req-1");
        task.setLoopId("loop-1");
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setActiveLoopId("loop-1");
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        when(reviewService.findOne("review-1")).thenReturn(review);
        when(reviewService.claimForReview("review-1")).thenReturn(true);
        when(reviewService.requeueReview("review-1")).thenReturn(true);
        when(codingTaskDao.findOne("task-1")).thenReturn(task);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);
        when(automationService.reviewTaskDelivery(any(), any(), any()))
                .thenThrow(new AgentExecutionDeferredException(
                        "run-deferred", "workspace busy"));

        orchestrator.review("review-1");

        verify(reviewService).requeueReview("review-1");
        verify(reviewService, never()).markWaitingHuman(any(), any());
        assertEquals(RequirementAutomationStatus.DEVELOPING, requirement.getAutomationStatus());
    }

    @Test
    void deliveryEvidenceIncludesSuccessfulRunResultForPmDecision() throws Exception {
        RunDao runDao = mock(RunDao.class);
        TaskDeliveryReviewOrchestrator orchestrator = new TaskDeliveryReviewOrchestrator(
                mock(TaskDeliveryReviewService.class), mock(CodingTaskDao.class),
                mock(RequirementDao.class), mock(ExecutionPlanDao.class), runDao,
                mock(RequirementCommentService.class), mock(RequirementDesignContextService.class),
                mock(FailureInfoSupport.class), mock(RequirementAutomationService.class),
                mock(ApplicationEventPublisher.class),
                mock(org.springframework.transaction.PlatformTransactionManager.class));
        Run run = new Run();
        run.setId("run-1");
        run.setState(RunState.SUCCEEDED);
        run.setSummary("实现已返回，但回归测试仍失败");
        when(runDao.findOne("run-1")).thenReturn(run);
        CodingTask task = new CodingTask();
        TaskDeliveryReview review = new TaskDeliveryReview();
        review.setDeliveryRunId("run-1");
        review.setDeliverySucceeded(true);
        Method method = TaskDeliveryReviewOrchestrator.class.getDeclaredMethod(
                "buildDeliveryEvidence", CodingTask.class, TaskDeliveryReview.class);
        method.setAccessible(true);

        String evidence = (String) method.invoke(orchestrator, task, review);

        assertTrue(evidence.contains("\"runState\":\"SUCCEEDED\""));
        assertTrue(evidence.contains("\"runSummary\":\"实现已返回，但回归测试仍失败\""));
    }

    @Test
    void incompleteEvidenceRejectsPmApproveWithoutChangingRunResult() {
        TaskDeliveryReviewService reviewService = mock(TaskDeliveryReviewService.class);
        RequirementDao requirementDao = mock(RequirementDao.class);
        RunEvidenceService evidenceService = mock(RunEvidenceService.class);
        RunEvidence incomplete = new RunEvidence();
        incomplete.setId("evidence-1");
        incomplete.setRunId("run-1");
        incomplete.setCompleteness(EvidenceCompleteness.PARTIAL);
        when(evidenceService.findLatest("run-1")).thenReturn(incomplete);
        when(reviewService.recordDecision(any(), any(), any(), any(), any()))
                .thenReturn(TaskDeliveryReviewDecision.WAIT_HUMAN);

        TaskDeliveryReviewOrchestrator orchestrator = new TaskDeliveryReviewOrchestrator(
                reviewService, mock(CodingTaskDao.class), requirementDao,
                mock(ExecutionPlanDao.class), mock(RunDao.class),
                mock(RequirementCommentService.class), mock(RequirementDesignContextService.class),
                mock(FailureInfoSupport.class), mock(RequirementAutomationService.class),
                mock(ApplicationEventPublisher.class),
                mock(org.springframework.transaction.PlatformTransactionManager.class),
                evidenceService, mock(RunArtifactService.class),
                mock(AgentBehaviorMemoryService.class));
        TaskDeliveryReview review = new TaskDeliveryReview();
        review.setId("review-1");
        review.setDeliveryRunId("run-1");
        review.setDeliverySucceeded(true);
        CodingTask task = new CodingTask();
        task.setId("task-1");
        task.setProjectId("project-1");
        task.setRequirementId("req-1");
        task.setLoopId("loop-1");
        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setActiveLoopId("loop-1");
        requirement.setAutomationStatus(RequirementAutomationStatus.DEVELOPING);
        when(requirementDao.findOne("req-1")).thenReturn(requirement);

        orchestrator.applyDecision(review, task, requirement, null,
                new PmDeliveryDecision(TaskDeliveryReviewDecision.APPROVE, "approve",
                        DeliveryFailureCategory.NONE, List.of(), null, List.of()));

        verify(reviewService).recordDecision(eq(review), eq(TaskDeliveryReviewDecision.WAIT_HUMAN),
                org.mockito.ArgumentMatchers.contains("结构化证据完整度为 PARTIAL"),
                any(), eq(DeliveryFailureCategory.NONE));
        assertEquals(RequirementAutomationStatus.WAITING_HUMAN,
                requirement.getAutomationStatus());
    }
}
