package com.changhong.onlinecode.service.review;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 任务级交付审阅事件监听器（方案 §5.1 / §7 异步边界）。
 *
 * <p>消费 {@link TaskDeliveryReviewRequested}，在提交后异步驱动
 * {@link TaskDeliveryReviewOrchestrator#review}。PM 审阅与 test-agent 执行一样，
 * 在任何数据库事务之外运行（orchestrator 内部决策副作用各自开启新事务）。</p>
 *
 * <p>使用 {@code @Async} + 默认 task executor；若需专用有界池，可在
 * {@code @Async("pmReviewExecutor")} 指定（见方案 §7）。</p>
 *
 * @author sei-online-code
 */
@Component
@Slf4j
public class TaskDeliveryReviewEventListener {

    private final TaskDeliveryReviewOrchestrator orchestrator;

    public TaskDeliveryReviewEventListener(TaskDeliveryReviewOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Async("pmReviewExecutor")
    @EventListener
    public void onDeliveryReviewRequested(TaskDeliveryReviewRequested event) {
        try {
            orchestrator.review(event.reviewId());
        } catch (Exception e) {
            log.error("task delivery review failed. requirementId={}, reviewId={}, codingTaskId={}",
                    event.requirementId(), event.reviewId(), event.codingTaskId(), e);
        }
    }
}
