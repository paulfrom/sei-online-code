package com.changhong.onlinecode.service.review;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.DeliveryFailureCategory;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.TaskDeliveryReview;
import com.changhong.onlinecode.service.CodingTaskSchedulingEvents;
import com.changhong.onlinecode.service.FailureInfoSupport;
import com.changhong.onlinecode.service.RequirementAutomationService;
import com.changhong.onlinecode.service.RequirementCommentService;
import com.changhong.onlinecode.service.RequirementDesignContextService;
import com.changhong.onlinecode.service.agent.PmDeliveryDecision;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
                commentService, mock(RequirementDesignContextService.class), mock(FailureInfoSupport.class),
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
}
