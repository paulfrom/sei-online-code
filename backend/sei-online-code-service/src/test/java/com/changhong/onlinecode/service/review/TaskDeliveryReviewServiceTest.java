package com.changhong.onlinecode.service.review;

import com.changhong.onlinecode.dao.TaskDeliveryReviewDao;
import com.changhong.onlinecode.dto.enums.DeliveryFailureCategory;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.TaskDeliveryReview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskDeliveryReviewService 单元测试（方案 §4.2 / §4.3 / §6.1 / §6.3）。
 *
 * <p>覆盖：幂等创建、APPROVE-for-failed 被拒绝转 WAIT_HUMAN、未决审阅门禁、依赖 APPROVE 条件。</p>
 */
class TaskDeliveryReviewServiceTest {

    private TaskDeliveryReviewDao dao;
    private TaskDeliveryReviewService service;

    @BeforeEach
    void setUp() {
        dao = mock(TaskDeliveryReviewDao.class);
        service = new TaskDeliveryReviewService(dao);
    }

    private CodingTask task(String id, String runId) {
        CodingTask task = new CodingTask();
        task.setId(id);
        task.setRequirementId("req-1");
        task.setExecutionPlanId("plan-1");
        task.setLoopId("loop-1");
        return task;
    }

    @Test
    void createOrGet_isIdempotentForSameTaskAndRun() {
        CodingTask task = task("task-1", "run-1");
        TaskDeliveryReview existing = new TaskDeliveryReview();
        existing.setId("review-1");
        when(dao.findByCodingTaskIdAndDeliveryRunId("task-1", "run-1")).thenReturn(Optional.of(existing));

        TaskDeliveryReview result = service.createOrGet(task, "run-1", true);

        assertEquals("review-1", result.getId());
        verify(dao, never()).save(any(com.changhong.onlinecode.entity.TaskDeliveryReview.class));
    }

    @Test
    void createOrGet_createsPendingReviewForNewDelivery() {
        CodingTask task = task("task-1", "run-1");
        when(dao.findByCodingTaskIdAndDeliveryRunId("task-1", "run-1")).thenReturn(Optional.empty());
        when(dao.save(any(com.changhong.onlinecode.entity.TaskDeliveryReview.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskDeliveryReview result = service.createOrGet(task, "run-1", true);

        assertEquals(TaskDeliveryReviewStatus.PENDING, result.getStatus());
        assertEquals(Boolean.TRUE, result.getDeliverySucceeded());
        assertEquals("task-1", result.getCodingTaskId());
        assertEquals("run-1", result.getDeliveryRunId());
    }

    @Test
    void recordDecision_rejectsApproveForFailedDeliveryAndConvertsToWaitingHuman() {
        // 方案 §4.3：FAILED/VALIDATION_FAILED + APPROVE 必须被服务端拒绝并转 WAIT_HUMAN。
        TaskDeliveryReview review = new TaskDeliveryReview();
        review.setId("review-1");
        review.setCodingTaskId("task-1");
        review.setDeliveryRunId("run-1");
        review.setDeliverySucceeded(false);
        review.setStatus(TaskDeliveryReviewStatus.REVIEWING);
        when(dao.save(any(com.changhong.onlinecode.entity.TaskDeliveryReview.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordDecision(review, TaskDeliveryReviewDecision.APPROVE, "summary", "{}",
                DeliveryFailureCategory.VALIDATION_FAILED);

        assertEquals(TaskDeliveryReviewDecision.WAIT_HUMAN, review.getDecision());
        assertEquals(TaskDeliveryReviewStatus.WAITING_HUMAN, review.getStatus());
    }

    @Test
    void recordDecision_allowsApproveForSucceededDelivery() {
        TaskDeliveryReview review = new TaskDeliveryReview();
        review.setDeliverySucceeded(true);
        review.setStatus(TaskDeliveryReviewStatus.REVIEWING);
        when(dao.save(any(com.changhong.onlinecode.entity.TaskDeliveryReview.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordDecision(review, TaskDeliveryReviewDecision.APPROVE, "ok", "{}",
                DeliveryFailureCategory.NONE);

        assertEquals(TaskDeliveryReviewDecision.APPROVE, review.getDecision());
        assertEquals(TaskDeliveryReviewStatus.DECIDED, review.getStatus());
    }

    @Test
    void hasOpenReview_trueWhenPendingOrReviewingExists() {
        TaskDeliveryReview pending = new TaskDeliveryReview();
        pending.setStatus(TaskDeliveryReviewStatus.PENDING);
        when(dao.findOpenByRequirement(eq("req-1"), anyList())).thenReturn(List.of(pending));

        assertTrue(service.hasOpenReview("req-1"));
    }

    @Test
    void hasOpenReview_scopesGateToCurrentLoop() {
        when(dao.findOpenByRequirementAndLoop(eq("req-1"), eq("loop-2"), anyList()))
                .thenReturn(List.of());

        assertEquals(false, service.hasOpenReview("req-1", "loop-2"));
        verify(dao).findOpenByRequirementAndLoop(eq("req-1"), eq("loop-2"), anyList());
    }

    @Test
    void isApproved_trueOnlyWhenDecidedApproveExists() {
        TaskDeliveryReview latest = new TaskDeliveryReview();
        latest.setStatus(TaskDeliveryReviewStatus.DECIDED);
        latest.setDecision(TaskDeliveryReviewDecision.APPROVE);
        when(dao.findFirstByCodingTaskIdOrderByCreatedDateDesc("task-1"))
                .thenReturn(Optional.of(latest));

        assertTrue(service.isApproved("task-1"));
    }

    @Test
    void isApproved_usesLatestReviewInsteadOfAnyHistoricalApprove() {
        TaskDeliveryReview latest = new TaskDeliveryReview();
        latest.setStatus(TaskDeliveryReviewStatus.DECIDED);
        latest.setDecision(TaskDeliveryReviewDecision.RETRY);
        when(dao.findFirstByCodingTaskIdOrderByCreatedDateDesc("task-1"))
                .thenReturn(Optional.of(latest));
        when(dao.existsByCodingTaskIdAndStatusAndDecision("task-1",
                TaskDeliveryReviewStatus.DECIDED, TaskDeliveryReviewDecision.APPROVE)).thenReturn(true);

        assertEquals(false, service.isApproved("task-1"));
    }

    @Test
    void claimForReview_usesCasToUpdatePendingToReviewing() {
        when(dao.updateStatusIfMatch(eq("review-1"),
                eq(TaskDeliveryReviewStatus.PENDING), eq(TaskDeliveryReviewStatus.REVIEWING))).thenReturn(1);

        boolean claimed = service.claimForReview("review-1");

        assertTrue(claimed);
    }

    @Test
    void claimForReview_returnsFalseWhenAlreadyClaimed() {
        // 重复事件触发：CAS 失败说明已被其他实例处理。
        when(dao.updateStatusIfMatch(anyString(), any(), any())).thenReturn(0);

        boolean claimed = service.claimForReview("review-1");

        assertEquals(false, claimed);
    }
}
