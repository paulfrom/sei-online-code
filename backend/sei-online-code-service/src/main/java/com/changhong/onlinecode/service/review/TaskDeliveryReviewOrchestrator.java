package com.changhong.onlinecode.service.review;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.enums.EvidenceCompleteness;
import com.changhong.onlinecode.dto.enums.RunArtifactType;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.TaskDeliveryReview;
import com.changhong.onlinecode.entity.RunEvidence;
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
import com.changhong.sei.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 任务级 PM 交付审阅编排（方案 §5.3 决策应用 / §6.3 依赖条件 / §8 补偿契约）。
 *
 * <p>驱动单次交付 Run 的 PM 审阅全过程：组装输入 -> 调用 pm-agent -> 落库决策 -> 应用四类决策副作用。
 * 由 {@link TaskDeliveryReviewEventListener} 在事务外异步触发，决策副作用在新事务中提交。</p>
 *
 * <ul>
 *   <li>APPROVE：审阅 DECIDED，调度器继续启动后续任务。</li>
 *   <li>RETRY：CodingTask 回 PENDING，retryCount 仅 +1 一次，提交后重新调度。</li>
 *   <li>REPLAN：当前计划 NEEDS_REMEDIATION，复用 startRemediationLoop 生成单个自修复、自验证任务。</li>
 *   <li>WAIT_HUMAN：需求 WAITING_HUMAN，停止自动调度/补偿。</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Service
@Slf4j
public class TaskDeliveryReviewOrchestrator {

    private final TaskDeliveryReviewService reviewService;
    private final CodingTaskDao codingTaskDao;
    private final RequirementDao requirementDao;
    private final ExecutionPlanDao executionPlanDao;
    private final RunDao runDao;
    private final RequirementCommentService commentService;
    private final RequirementDesignContextService designContextService;
    private final FailureInfoSupport failureInfoSupport;
    private final RequirementAutomationService automationService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate decisionTransactions;
    private final RunEvidenceService runEvidenceService;
    private final RunArtifactService runArtifactService;
    private final AgentBehaviorMemoryService behaviorMemoryService;

    @Autowired
    public TaskDeliveryReviewOrchestrator(TaskDeliveryReviewService reviewService,
                                          CodingTaskDao codingTaskDao,
                                          RequirementDao requirementDao,
                                          ExecutionPlanDao executionPlanDao,
                                          RunDao runDao,
                                          RequirementCommentService commentService,
                                          RequirementDesignContextService designContextService,
                                          FailureInfoSupport failureInfoSupport,
                                          RequirementAutomationService automationService,
                                          ApplicationEventPublisher eventPublisher,
                                          PlatformTransactionManager transactionManager,
                                          RunEvidenceService runEvidenceService,
                                          RunArtifactService runArtifactService,
                                          AgentBehaviorMemoryService behaviorMemoryService) {
        this.reviewService = reviewService;
        this.codingTaskDao = codingTaskDao;
        this.requirementDao = requirementDao;
        this.executionPlanDao = executionPlanDao;
        this.runDao = runDao;
        this.commentService = commentService;
        this.designContextService = designContextService;
        this.failureInfoSupport = failureInfoSupport;
        this.automationService = automationService;
        this.eventPublisher = eventPublisher;
        this.runEvidenceService = runEvidenceService;
        this.runArtifactService = runArtifactService;
        this.behaviorMemoryService = behaviorMemoryService;
        this.decisionTransactions = new TransactionTemplate(transactionManager);
        this.decisionTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 兼容现有单元测试构造；生产使用带 evidence/memory service 的构造器。 */
    public TaskDeliveryReviewOrchestrator(TaskDeliveryReviewService reviewService,
                                          CodingTaskDao codingTaskDao,
                                          RequirementDao requirementDao,
                                          ExecutionPlanDao executionPlanDao,
                                          RunDao runDao,
                                          RequirementCommentService commentService,
                                          RequirementDesignContextService designContextService,
                                          FailureInfoSupport failureInfoSupport,
                                          RequirementAutomationService automationService,
                                          ApplicationEventPublisher eventPublisher,
                                          PlatformTransactionManager transactionManager) {
        this(reviewService, codingTaskDao, requirementDao, executionPlanDao, runDao,
                commentService, designContextService, failureInfoSupport, automationService,
                eventPublisher, transactionManager, null, null, null);
    }

    /**
     * 执行一次任务级 PM 审阅。事务外调用：内部 {@link #applyDecision} 自带事务。
     *
     * <p>幂等：claim 失败说明已被其他实例处理，直接返回。</p>
     */
    public void review(String reviewId) {
        TaskDeliveryReview review = reviewService.findOne(reviewId);
        if (review == null || review.getStatus() != com.changhong.onlinecode.dto.enums.TaskDeliveryReviewStatus.PENDING) {
            log.info("delivery review skipped: not pending. reviewId={}, status={}",
                    reviewId, review == null ? null : review.getStatus());
            return;
        }
        CodingTask task = codingTaskDao.findOne(review.getCodingTaskId());
        if (task == null) {
            log.warn("delivery review skipped: coding task not found. reviewId={}, codingTaskId={}",
                    reviewId, review.getCodingTaskId());
            reviewService.markWaitingHuman(review, "编码任务已不存在");
            return;
        }
        Requirement requirement = requirementDao.findOne(task.getRequirementId());
        if (requirement == null || !Objects.equals(task.getLoopId(), requirement.getActiveLoopId())) {
            log.info("delivery review skipped: requirement gone or loop changed. reviewId={}", reviewId);
            reviewService.markWaitingHuman(review, "需求不存在或 loop 已变更");
            return;
        }
        if (requirement.getAutomationStatus() == RequirementAutomationStatus.WAITING_HUMAN
                || requirement.getAutomationStatus() == RequirementAutomationStatus.INTERRUPTED) {
            log.info("delivery review skipped: requirement is terminal. reviewId={}, status={}",
                    reviewId, requirement.getAutomationStatus());
            return;
        }

        if (!reviewService.claimForReview(reviewId)) {
            log.info("delivery review already claimed by another pass. reviewId={}", reviewId);
            return;
        }

        ExecutionPlan plan = task.getExecutionPlanId() == null ? null
                : executionPlanDao.findOne(task.getExecutionPlanId());
        PmDeliveryDecision decision;
        try {
            decision = invokePmAgent(requirement, plan, task, review);
        } catch (AgentExecutionDeferredException e) {
            boolean requeued = reviewService.requeueReview(reviewId);
            log.info("delivery review deferred and requeued. reviewId={}, runId={}, requeued={}, reason={}",
                    reviewId, e.getRunId(), requeued, e.getMessage());
            return;
        }
        decisionTransactions.executeWithoutResult(ignored ->
                applyDecision(review, task, requirement, plan, decision));
    }

    private PmDeliveryDecision invokePmAgent(Requirement requirement, ExecutionPlan plan,
                                             CodingTask task, TaskDeliveryReview review) {
        try {
            com.changhong.onlinecode.service.agent.PmAgentClient.DeliveryReviewInput input =
                    buildReviewInput(requirement, plan, task, review);
            return automationService.reviewTaskDelivery(requirement, plan, input);
        } catch (AgentExecutionDeferredException e) {
            throw e;
        } catch (Exception e) {
            log.error("pm-agent delivery review invocation failed. reviewId={}, codingTaskId={}",
                    review.getId(), task.getId(), e);
            return null;
        }
    }

    private com.changhong.onlinecode.service.agent.PmAgentClient.DeliveryReviewInput buildReviewInput(
            Requirement requirement, ExecutionPlan plan, CodingTask task, TaskDeliveryReview review) {
        List<RequirementComment> comments = commentService.findByRequirementId(requirement.getId());
        String evidence = buildDeliveryEvidence(task, review);
        String taskType = "test-agent".equals(task.getAssignedAgent()) ? "validation-task" : "coding-task";
        return new com.changhong.onlinecode.service.agent.PmAgentClient.DeliveryReviewInput(
                requirement.getId(), task.getLoopId(), task.getId(), review.getDeliveryRunId(),
                task.getPlanTaskKey(), task.getTitle(), task.getDescription(), task.getArea(),
                task.getAssignedAgent(), taskType, Boolean.TRUE.equals(review.getDeliverySucceeded()),
                task.getAcceptanceCriteria() == null ? List.of() : task.getAcceptanceCriteria(),
                evidence, comments);
    }

    private String buildDeliveryEvidence(CodingTask task, TaskDeliveryReview review) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        Run deliveryRun = review.getDeliveryRunId() == null
                ? null : runDao.findOne(review.getDeliveryRunId());
        evidence.put("deliveryRunId", review.getDeliveryRunId());
        evidence.put("deliverySucceeded", review.getDeliverySucceeded());
        evidence.put("runState", deliveryRun == null ? null : deliveryRun.getState());
        evidence.put("runSummary", deliveryRun == null ? null : deliveryRun.getSummary());
        evidence.put("runFailureReason", deliveryRun == null ? null : deliveryRun.getFailureReason());
        evidence.put("failureSummary", task.getFailureSummary());
        evidence.put("failureDetail", task.getFailureDetail());
        evidence.put("retryCount", task.getRetryCount());
        if (runEvidenceService != null && review.getDeliveryRunId() != null) {
            RunEvidence runEvidence = runEvidenceService.findLatest(review.getDeliveryRunId());
            if (runEvidence != null) {
                review.setEvidenceId(runEvidence.getId());
                evidence.put("runEvidence", runEvidenceService.findLatestDto(review.getDeliveryRunId()));
            } else {
                evidence.put("runEvidence", Map.of(
                        "status", "MISSING",
                        "completeness", EvidenceCompleteness.MISSING));
            }
        }
        if (runArtifactService != null && review.getDeliveryRunId() != null) {
            runArtifactService.findByRunId(review.getDeliveryRunId()).stream()
                    .filter(artifact -> artifact.getArtifactType() == RunArtifactType.GIT_DIFF)
                    .reduce((first, second) -> second)
                    .ifPresent(artifact -> evidence.put(
                            "gitDiffExcerpt",
                            bounded(runArtifactService.readText(artifact.getId()), 48_000)));
            evidence.put("rawLogExcerpt",
                    runArtifactService.readLogFrames(review.getDeliveryRunId(), 0L, 200).getFrames());
        }
        try {
            return JsonUtils.mapper().writeValueAsString(evidence);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 应用 PM 决策副作用。新事务，决策落库与状态变更原子提交。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyDecision(TaskDeliveryReview review, CodingTask task, Requirement requirement,
                              ExecutionPlan plan, PmDeliveryDecision decision) {
        if (decision == null) {
            // PM 调用或解析失败 -> WAIT_HUMAN（方案 §11）
            reviewService.markWaitingHuman(review, "PM 调用失败或返回 JSON 无法解析，转人工处理");
            setRequirementWaitingHuman(requirement, plan, "PM 交付审阅失败，转人工处理");
            return;
        }

        decision = enforceEvidenceGate(review, decision);
        String decisionJson = serializeDecision(decision);
        review.setRemediationBriefJson(decision.remediationBrief() == null
                ? null : toJson(decision.remediationBrief()));
        com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision effectiveDecision =
                reviewService.recordDecision(review, decision.decision(), decision.summary(),
                        decisionJson, decision.failureCategory());
        appendReviewComment(requirement, task, decision);
        if (behaviorMemoryService != null) {
            behaviorMemoryService.recordPmDecision(
                    task.getProjectId(), task.getAssignedAgent(), review.getId(),
                    review.getEvidenceId(), review.getDeliveryRunId(), decision);
        }

        switch (effectiveDecision) {
            case APPROVE -> handleApprove(requirement);
            case RETRY -> handleRetry(task, requirement, decision);
            case REPLAN -> handleReplan(requirement, task, plan, decision);
            case WAIT_HUMAN -> setRequirementWaitingHuman(requirement, plan, decision.summary());
            default -> { }
        }
    }

    private void handleApprove(Requirement requirement) {
        // APPROVE 后恢复调度，让依赖该任务的下游任务启动。
        eventPublisher.publishEvent(new CodingTaskSchedulingEvents.ScheduleRequested(requirement.getId()));
    }

    private void handleRetry(CodingTask task, Requirement requirement, PmDeliveryDecision decision) {
        Date now = new Date();
        // retryCount 仅在 PM 决策后增加一次（方案 §5.3 / §8 单调约束）。
        if (codingTaskDao.updateStatusIfMatch(task.getId(), task.getStatus(),
                CodingTaskStatus.PENDING) == 0) {
            log.info("RETRY skipped: task status changed before decision applied. taskId={}, status={}",
                    task.getId(), task.getStatus());
            return;
        }
        task.setStatus(CodingTaskStatus.PENDING);
        failureInfoSupport.markRetrying(task, TriggerSource.SCHEDULED_COMPENSATION, now);
        task.setFailureSummary(decision.summary());
        codingTaskDao.save(task);
        log.info("RETRY applied: taskId={}, requirementId={}, retryCount={}, retryReason={}",
                task.getId(), requirement.getId(), task.getRetryCount(), decision.retryReason());
        eventPublisher.publishEvent(new CodingTaskSchedulingEvents.ScheduleRequested(requirement.getId()));
    }

    private void handleReplan(Requirement requirement, CodingTask task, ExecutionPlan plan,
                              PmDeliveryDecision decision) {
        // 任务级 REPLAN 复用计划级补救路径（方案 §5.3）：startRemediationLoop 作为唯一入口，
        // 复用 MAX_REMEDIATION_ROUNDS 计数与 remediationTasks 契约。
        RequirementDesignContext context = designContextService.findCurrentByRequirement(requirement.getId());
        List<RequirementComment> comments = commentService.findByRequirementId(requirement.getId());
        if (plan != null) {
            plan.setStatus(com.changhong.onlinecode.dto.enums.ExecutionPlanStatus.NEEDS_REMEDIATION);
            executionPlanDao.save(plan);
        }
        boolean remediated = automationService.startRemediationLoopFromDelivery(
                requirement, plan, context, comments, decision);
        if (!remediated) {
            // 无补救任务或超上限 -> WAIT_HUMAN
            setRequirementWaitingHuman(requirement, plan,
                    "PM REPLAN 无法生成补救计划，转人工处理：" + decision.summary());
        }
    }

    private void setRequirementWaitingHuman(Requirement requirement, ExecutionPlan plan, String reason) {
        Requirement fresh = requirementDao.findOne(requirement.getId());
        if (fresh == null) {
            return;
        }
        if (fresh.getAutomationStatus() == RequirementAutomationStatus.WAITING_HUMAN
                || fresh.getAutomationStatus() == RequirementAutomationStatus.INTERRUPTED) {
            return;
        }
        fresh.setAutomationStatus(RequirementAutomationStatus.WAITING_HUMAN);
        requirementDao.save(fresh);
        if (plan != null) {
            ExecutionPlan freshPlan = executionPlanDao.findOne(plan.getId());
            if (freshPlan != null && freshPlan.getStatus() != com.changhong.onlinecode.dto.enums.ExecutionPlanStatus.INTERRUPTED) {
                freshPlan.setStatus(com.changhong.onlinecode.dto.enums.ExecutionPlanStatus.NEEDS_REMEDIATION);
                executionPlanDao.save(freshPlan);
            }
        }
        commentService.append(requirement.getId(), requirement.getActiveLoopId(),
                RequirementCommentAuthorType.PM_AGENT, "pm-agent",
                RequirementCommentType.REMEDIATION, reason, null);
    }

    private void appendReviewComment(Requirement requirement, CodingTask task, PmDeliveryDecision decision) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("taskId", task.getId());
        metadata.put("taskKey", task.getPlanTaskKey());
        metadata.put("decision", decision.decision().name());
        metadata.put("failureCategory", decision.failureCategory().name());
        metadata.put("findings", decision.findings());
        metadata.put("remediationBrief", decision.remediationBrief());
        metadata.put("behaviorMemoryCandidates", decision.behaviorMemoryCandidates());
        String content = "任务交付审阅：" + decision.decision().name() + " — " + decision.summary();
        if (decision.remediationBrief() != null) {
            content += "\n\n权威修复说明（下一次 Agent 必须执行并验证）：\n"
                    + toJson(decision.remediationBrief());
        }
        commentService.append(requirement.getId(), task.getLoopId(),
                RequirementCommentAuthorType.PM_AGENT, "pm-agent",
                RequirementCommentType.VALIDATION_RESULT,
                content,
                toJson(metadata));
    }

    private PmDeliveryDecision enforceEvidenceGate(TaskDeliveryReview review,
                                                   PmDeliveryDecision decision) {
        if (decision.decision() != TaskDeliveryReviewDecision.APPROVE
                || runEvidenceService == null) {
            return decision;
        }
        RunEvidence evidence = runEvidenceService.findLatest(review.getDeliveryRunId());
        if (evidence != null && evidence.getCompleteness() == EvidenceCompleteness.COMPLETE) {
            return decision;
        }
        String actual = evidence == null ? "MISSING" : evidence.getCompleteness().name();
        return PmDeliveryDecision.waitingHuman(
                "PM 返回 APPROVE，但结构化证据完整度为 " + actual
                        + "；Run 结果保留成功，批准门禁转人工决定补证或修复。");
    }

    private String bounded(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        int side = maxChars / 2;
        return value.substring(0, side)
                + "\n...[excerpt truncated; use artifactId to inspect full redacted content]...\n"
                + value.substring(value.length() - side);
    }

    private String serializeDecision(PmDeliveryDecision decision) {
        try {
            return JsonUtils.mapper().writeValueAsString(decision);
        } catch (Exception e) {
            return "{\"decision\":\"" + decision.decision() + "\"}";
        }
    }

    private String toJson(Object value) {
        try {
            return JsonUtils.mapper().writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
