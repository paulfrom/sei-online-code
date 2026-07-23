package com.changhong.onlinecode.service.review;

import java.util.Objects;

/**
 * 任务交付审阅请求事件。
 *
 * <p>在 CodingTask 交付结算事务提交后发布；由 {@link TaskDeliveryReviewEventListener}
 * 异步消费，驱动 pm-agent 审阅。携带审阅记录 ID，监听器据此重读最新状态，避免事件载体过期。</p>
 *
 * @param requirementId 需求 ID
 * @param reviewId      交付审阅记录 ID
 * @param codingTaskId  编码任务 ID
 */
public record TaskDeliveryReviewRequested(String requirementId, String reviewId, String codingTaskId) {

    public TaskDeliveryReviewRequested {
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(codingTaskId, "codingTaskId");
    }
}
