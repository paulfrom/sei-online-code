package com.changhong.onlinecode.service.review;

import com.changhong.onlinecode.dao.TaskDeliveryReviewDao;
import com.changhong.onlinecode.dto.enums.DeliveryFailureCategory;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.TaskDeliveryReview;
import com.changhong.sei.core.service.BaseEntityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 任务交付审阅服务（方案 §4.2 / §4.3 / §6.1 / §8）。
 *
 * <p>负责审阅记录的幂等创建、状态流转、决策合法性校验，以及供调度器/补偿器查询的只读视图。
 * 决策应用（APPROVE/RETRY/REPLAN/WAIT_HUMAN 的副作用）由 BE-RUN-002 的编排服务驱动，
 * 本服务只保证审阅记录本身的状态正确。</p>
 *
 * @author sei-online-code
 */
@Service
@Slf4j
public class TaskDeliveryReviewService extends BaseEntityService<TaskDeliveryReview> {

    /** 仍未决的审阅状态：存在任意一条时，调度门禁生效（方案 §6.1）。 */
    private static final List<TaskDeliveryReviewStatus> OPEN_STATUSES = List.of(
            TaskDeliveryReviewStatus.PENDING, TaskDeliveryReviewStatus.REVIEWING);

    private final TaskDeliveryReviewDao dao;

    public TaskDeliveryReviewService(TaskDeliveryReviewDao dao) {
        this.dao = dao;
    }

    @Override
    protected com.changhong.sei.core.dao.BaseEntityDao<TaskDeliveryReview> getDao() {
        return dao;
    }

    /**
     * 幂等创建交付审阅记录。同一 (codingTaskId, deliveryRunId) 已存在则返回已有记录。
     *
     * <p>在调用方事务内执行，保证 review 创建与 CodingTask 终态结算原子提交。</p>
     *
     * @param task              交付完成的 CodingTask
     * @param deliveryRunId     被审阅的交付 Run ID
     * @param deliverySucceeded 交付是否成功（用于决策校验）
     * @return 已存在的或新建的 PENDING 审阅记录
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskDeliveryReview createOrGet(CodingTask task, String deliveryRunId, boolean deliverySucceeded) {
        TaskDeliveryReview existing = dao.findByCodingTaskIdAndDeliveryRunId(
                task.getId(), deliveryRunId).orElse(null);
        if (existing != null) {
            return existing;
        }
        TaskDeliveryReview review = new TaskDeliveryReview();
        review.setRequirementId(task.getRequirementId());
        review.setExecutionPlanId(task.getExecutionPlanId());
        review.setCodingTaskId(task.getId());
        review.setDeliveryRunId(deliveryRunId);
        review.setLoopId(task.getLoopId());
        review.setStatus(TaskDeliveryReviewStatus.PENDING);
        review.setDeliverySucceeded(deliverySucceeded);
        review.setFailureCategory(DeliveryFailureCategory.NONE);
        return dao.save(review);
    }

    /**
     * 原子声明审阅进入 REVIEWING（PENDING -> REVIEWING），保证重复事件只触发一次 PM 调用。
     *
     * @return true 表示本次成功 claim，调用方应继续调用 pm-agent
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean claimForReview(String reviewId) {
        return dao.updateStatusIfMatch(reviewId, TaskDeliveryReviewStatus.PENDING,
                TaskDeliveryReviewStatus.REVIEWING) > 0;
    }

    /**
     * 记录 PM 决策，先做合法性校验（方案 §4.3）。
     *
     * <p>非法组合 {@code deliverySucceeded=false + APPROVE} 被拒绝并转为 {@code WAIT_HUMAN}。</p>
     *
     * @param review   被审阅记录
     * @param decision PM 原始决策
     * @param decisionJson 完整决策 JSON
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskDeliveryReviewDecision recordDecision(TaskDeliveryReview review, TaskDeliveryReviewDecision decision,
                                                      String summary, String decisionJson,
                                                      DeliveryFailureCategory failureCategory) {
        TaskDeliveryReviewDecision effective = enforceDecisionContract(review, decision);
        review.setDecision(effective);
        review.setSummary(summary);
        review.setDecisionJson(decisionJson);
        review.setFailureCategory(failureCategory == null ? DeliveryFailureCategory.NONE : failureCategory);
        review.setReviewedDate(new java.util.Date());
        review.setStatus(effective == TaskDeliveryReviewDecision.WAIT_HUMAN
                ? TaskDeliveryReviewStatus.WAITING_HUMAN
                : TaskDeliveryReviewStatus.DECIDED);
        dao.save(review);
        return effective;
    }

    /**
     * 落库 WAIT_HUMAN（PM 调用失败 / JSON 解析失败 / 非法决策）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markWaitingHuman(TaskDeliveryReview review, String summary) {
        review.setDecision(TaskDeliveryReviewDecision.WAIT_HUMAN);
        review.setSummary(summary);
        review.setReviewedDate(new java.util.Date());
        review.setStatus(TaskDeliveryReviewStatus.WAITING_HUMAN);
        dao.save(review);
    }

    /**
     * 决策契约校验（方案 §4.3 约束）。
     *
     * <p>{@code FAILED / VALIDATION_FAILED + APPROVE} 必须被服务端拒绝并转为 {@code WAIT_HUMAN}。</p>
     */
    private TaskDeliveryReviewDecision enforceDecisionContract(TaskDeliveryReview review,
                                                               TaskDeliveryReviewDecision decision) {
        if (decision == TaskDeliveryReviewDecision.APPROVE
                && !Boolean.TRUE.equals(review.getDeliverySucceeded())) {
            log.warn("rejected APPROVE for failed delivery; converting to WAIT_HUMAN. codingTaskId={}, deliveryRunId={}",
                    review.getCodingTaskId(), review.getDeliveryRunId());
            return TaskDeliveryReviewDecision.WAIT_HUMAN;
        }
        return decision;
    }

    /** 某需求是否存在未决的交付审阅（调度门禁，方案 §6.1）。 */
    public boolean hasOpenReview(String requirementId) {
        return !dao.findOpenByRequirement(requirementId, OPEN_STATUSES).isEmpty();
    }

    /** 当前 loop 是否存在未决审阅；旧 loop 的遗留记录不得阻塞新计划。 */
    public boolean hasOpenReview(String requirementId, String loopId) {
        return !dao.findOpenByRequirementAndLoop(requirementId, loopId, OPEN_STATUSES).isEmpty();
    }

    /** 某 codingTaskId 的最新审阅决策（依赖满足条件，方案 §6.3）。 */
    public Optional<TaskDeliveryReviewDecision> latestDecisionForCodingTask(String codingTaskId) {
        return dao.findFirstByCodingTaskIdOrderByCreatedDateDesc(codingTaskId)
                .map(TaskDeliveryReview::getDecision);
    }

    /** 某 codingTaskId 的最新审阅记录（补偿器据此判断是否已决策，方案 §8）。 */
    public Optional<TaskDeliveryReview> findFirstByCodingTaskId(String codingTaskId) {
        return dao.findFirstByCodingTaskIdOrderByCreatedDateDesc(codingTaskId);
    }

    /** 某依赖任务是否已 APPROVE（依赖满足条件，方案 §6.3）。 */
    public boolean isApproved(String codingTaskId) {
        return dao.findFirstByCodingTaskIdOrderByCreatedDateDesc(codingTaskId)
                .filter(review -> review.getStatus() == TaskDeliveryReviewStatus.DECIDED)
                .map(TaskDeliveryReview::getDecision)
                .filter(decision -> decision == TaskDeliveryReviewDecision.APPROVE)
                .isPresent();
    }

    /** 某需求所有任务是否都已 DECIDED 或 WAITING_HUMAN（计划结算前置条件，方案 §6.1）。 */
    public boolean allReviewsSettled(String requirementId) {
        return dao.findOpenByRequirement(requirementId, OPEN_STATUSES).isEmpty();
    }

    public boolean allReviewsSettled(String requirementId, String loopId) {
        return dao.findOpenByRequirementAndLoop(requirementId, loopId, OPEN_STATUSES).isEmpty();
    }

    /** 某需求下未决审阅（补偿器只补发 PENDING review 事件，方案 §11）。 */
    public List<TaskDeliveryReview> findPendingReviews(String requirementId) {
        return dao.findOpenByRequirement(requirementId, List.of(TaskDeliveryReviewStatus.PENDING));
    }
}
