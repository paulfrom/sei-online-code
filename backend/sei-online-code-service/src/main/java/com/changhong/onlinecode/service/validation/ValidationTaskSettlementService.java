package com.changhong.onlinecode.service.validation;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.TaskDeliveryReview;
import com.changhong.onlinecode.service.CodingTaskSchedulingEvents;
import com.changhong.onlinecode.service.review.TaskDeliveryReviewRequested;
import com.changhong.onlinecode.service.review.TaskDeliveryReviewService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/** 验证任务的短事务结算边界。外部 test-agent 调用不得进入本服务。 */
@Service
@Slf4j
@AllArgsConstructor
public class ValidationTaskSettlementService {

    private final CodingTaskDao codingTaskDao;
    private final RequirementDao requirementDao;
    private final RunDao runDao;
    private final TaskDeliveryReviewService taskDeliveryReviewService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void finish(String codingTaskId, boolean passed) {
        CodingTask task = codingTaskDao.findOne(codingTaskId);
        if (task == null || task.getStatus() != CodingTaskStatus.VALIDATING) {
            log.info("validation finish skipped: task not VALIDATING. codingTaskId={}, status={}",
                    codingTaskId, task == null ? null : task.getStatus());
            return;
        }
        Requirement requirement = requirementDao.findOne(task.getRequirementId());
        if (requirement == null || !Objects.equals(task.getLoopId(), requirement.getActiveLoopId())) {
            task.setStatus(CodingTaskStatus.STALE);
            codingTaskDao.save(task);
            return;
        }
        if (passed) {
            task.setStatus(CodingTaskStatus.SUCCEEDED);
            task.setFailureSummary(null);
            task.setFailureDetail(null);
        } else {
            task.setStatus(CodingTaskStatus.VALIDATION_FAILED);
            task.setFailureSummary("任务级验证失败");
            task.setFailureDetail("详见 VALIDATION_RESULT 评论及 Run 记录");
            task.setLastFailedAt(new java.util.Date());
        }
        codingTaskDao.save(task);
        createReviewAndPublish(task, passed);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void finishOnFailure(String codingTaskId, String reason) {
        CodingTask task = codingTaskDao.findOne(codingTaskId);
        if (task == null || task.getStatus() != CodingTaskStatus.VALIDATING) {
            return;
        }
        task.setStatus(CodingTaskStatus.VALIDATION_FAILED);
        task.setFailureSummary("test-agent 验证失败");
        task.setFailureDetail(reason);
        task.setLastFailedAt(new java.util.Date());
        codingTaskDao.save(task);
        createReviewAndPublish(task, false);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void markStale(String codingTaskId) {
        CodingTask task = codingTaskDao.findOne(codingTaskId);
        if (task == null || task.getStatus() != CodingTaskStatus.VALIDATING) {
            return;
        }
        task.setStatus(CodingTaskStatus.STALE);
        codingTaskDao.save(task);
    }

    /** 线程池饱和是调度延迟：恢复 PENDING，不创建 review、不增加 retryCount。 */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void defer(String codingTaskId, String reason) {
        CodingTask task = codingTaskDao.findOne(codingTaskId);
        if (task == null || task.getStatus() != CodingTaskStatus.VALIDATING) {
            return;
        }
        task.setStatus(CodingTaskStatus.PENDING);
        codingTaskDao.save(task);
        log.warn("validation execution deferred. codingTaskId={}, reason={}", codingTaskId, reason);
        afterCommit(() -> eventPublisher.publishEvent(
                new CodingTaskSchedulingEvents.ScheduleRequested(task.getRequirementId())));
    }

    private void createReviewAndPublish(CodingTask task, boolean passed) {
        String deliveryRunId = runDao.findByCodingTaskId(task.getId()).stream()
                .max((left, right) -> Integer.compare(
                        Objects.requireNonNullElse(left.getRunNo(), 0),
                        Objects.requireNonNullElse(right.getRunNo(), 0)))
                .map(Run::getId)
                .orElse(task.getId());
        TaskDeliveryReview review = taskDeliveryReviewService.createOrGet(task, deliveryRunId, passed);
        afterCommit(() -> {
            eventPublisher.publishEvent(new TaskDeliveryReviewRequested(
                    task.getRequirementId(), review.getId(), task.getId()));
            eventPublisher.publishEvent(new CodingTaskSchedulingEvents.ScheduleRequested(task.getRequirementId()));
        });
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
