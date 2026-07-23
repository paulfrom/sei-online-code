package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementDesignContextDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.DeliveryFailureCategory;
import com.changhong.onlinecode.dto.enums.ExecutionPlanStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.MemoryRecordStatus;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewStatus;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.TaskDeliveryReview;
import com.changhong.onlinecode.config.OcConfig;
import com.changhong.onlinecode.service.agent.AgentExecutionService;
import com.changhong.onlinecode.service.progress.ProgressReconciler;
import com.changhong.onlinecode.service.review.TaskDeliveryReviewRequested;
import com.changhong.onlinecode.service.review.TaskDeliveryReviewService;
import com.changhong.sei.core.utils.TransactionUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Compensation for the comment-driven requirement loop.
 *
 * <p>The service repairs persisted boundaries and delegates normal progression to
 * {@link RequirementAutomationService}, {@link CodingTaskScheduler}, and
 * {@link RequirementDeliveryService}. It never creates a replacement loop and never advances
 * {@code INTERRUPTED} or {@code WAITING_HUMAN} requirements.</p>
 */
@Service
@Slf4j
@AllArgsConstructor
public class CompensationService {

    private final RequirementDao requirementDao;
    private final RequirementDesignContextDao requirementDesignContextDao;
    private final ExecutionPlanDao executionPlanDao;
    private final CodingTaskDao codingTaskDao;
    private final RunDao runDao;
    private final RequirementAgentService requirementAgentService;
    private final RequirementAutomationService automationService;
    private final RequirementDeliveryService deliveryService;
    private final FailureInfoSupport failureInfoSupport;
    private final CompensationLogService compensationLogService;
    private final RequirementCommentService requirementCommentService;
    private final ProgressReconciler progressReconciler;
    private final AgentExecutionService agentExecutionService;
    private final TaskDeliveryReviewService taskDeliveryReviewService;
    private final ApplicationEventPublisher eventPublisher;
    private final OcConfig ocConfig;
    private final TransactionTemplate transactionTemplate;


    /**
     * Runs one ordered recovery pass: close timed-out work, then repair upstream to downstream.
     */
    @Transactional
    public void runCycle() {
        Date now = new Date();
        runPhase("timeout-runs", () -> timeoutRuns(now));
        runPhase("prd-generation", () -> compensatePrdGeneration(now));
        runPhase("requirement-loops", () -> compensateRequirementLoops(now));
    }

    private void runPhase(String phase, Runnable action) {
        try {
            transactionTemplate.executeWithoutResult(status -> action.run());
        } catch (Exception e) {
            log.error("compensation phase failed: {}", phase, e);
        }
    }

    /**
     * 将超时 Run 收敛为带 TIMEOUT 原因的失败终态，并调用 ProgressReconciler 对账内部步骤。
     * 计划内 CodingTask 同步进入可重试失败态，由后续补偿周期按原计划和原工作区恢复。
     */
    @Transactional(rollbackFor = Exception.class)
    public void timeoutRuns(Date now) {
        long runTimeoutMinutes = ocConfig.getRunTimeoutMinutes();
        Date deadline = new Date(now.getTime() - runTimeoutMinutes * 60_000L);
        List<Run> runList = runDao.findByState(RunState.RUNNING);
        for (Run run : runList) {
            Date startedAt = run.getStartedDate();
            if (startedAt == null || startedAt.after(deadline)) {
                continue;
            }
            try {
                // threadId 是会话审计标识，不代表本地进程存活。必须先按 runId
                // 终止并等待 runner 持有的整个进程树退出，才能开放同一工作区重试。
                agentExecutionService.cancel(run.getId());
            } catch (Exception e) {
                log.error("超时 Run 的 Agent 进程未能终止，暂不开放重试: runId={}", run.getId(), e);
                continue;
            }
            if (runDao.updateStateIfMatch(run.getId(), RunState.RUNNING, RunState.FAILED) == 0) {
                continue;
            }
            run.setState(RunState.FAILED);
            run.setTerminalReason(RunTerminalReason.TIMEOUT);
            run.setFinishedDate(now);
            run.setFailureSummary("运行超时（已终止，等待按原计划恢复）");
            run.setFailureReason("补偿器检测到 Run 超过 " + runTimeoutMinutes
                    + " 分钟未结束；系统将保留原工作区成果并重试同一计划任务");
            runDao.save(run);
            markTimedOutCodingTaskRecoverable(run, now);
            log.info("compensation runNo {} loopId {} 超时→FAILED/TIMEOUT，等待计划任务恢复",
                    run.getRunNo(), run.getLoopId());

            // EXE-007: 调用 ProgressReconciler 对账
            try {
                progressReconciler.reconcileTimedOutRun(run.getId());
            } catch (Exception e) {
                log.warn("ProgressReconciler 对账异常 runId={}", run.getId(), e);
            }

            // PM agent 超时：不再直接 fail Requirement；追加 comment 提醒人工关注
            if ("pm-agent".equals(run.getAgentName()) && run.getRequirementId() != null) {
                Requirement requirement = requirementDao.findOne(run.getRequirementId());
                if (requirement != null
                        && requirement.getStatus() == RequirementStatus.PRD_CONFIRMED
                        && requirement.getAutomationStatus() == RequirementAutomationStatus.PLANNING
                        && Objects.equals(requirement.getActiveLoopId(), run.getLoopId())) {
                    log.info("PM agent 超时→FAILED/TIMEOUT，requirement {} 仍保持 PLANNING，待补偿续作",
                            requirement.getId());
                }
            }

            compensationLogService.record("RUN", run.getId(), "TIMEOUT_RUN_RECONCILE", true,
                    "超时 Run 已终止并触发对账", run.getFailureReason(), TriggerSource.SCHEDULED_COMPENSATION);
        }
    }

    private void markTimedOutCodingTaskRecoverable(Run run, Date now) {
        if (run.getCodingTaskId() == null) {
            return;
        }
        CodingTask task = codingTaskDao.findOne(run.getCodingTaskId());
        if (task == null || task.getStatus() != CodingTaskStatus.RUNNING) {
            return;
        }
        if (run.getLoopId() != null && task.getLoopId() != null
                && !Objects.equals(run.getLoopId(), task.getLoopId())) {
            return;
        }
        task.setStatus(CodingTaskStatus.FAILED);
        failureInfoSupport.markCodingTaskFailure(task, run.getFailureSummary(), run.getFailureReason(),
                TriggerSource.SCHEDULED_COMPENSATION, now);
        codingTaskDao.save(task);
    }

    /**
     * Keeps the pre-loop PRD generation recovery that existed before the loop migration.
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensatePrdGeneration(Date now) {
        compensateStalePrdContexts(now);
        List<Requirement> candidates = new java.util.ArrayList<>(requirementDao.findByStatus(RequirementStatus.FAILED));
        requirementDao.findByStatus(RequirementStatus.PRD_GENERATING).stream()
                .filter(requirement -> isStale(requirement.getLastEditedDate(), now))
                .forEach(candidates::add);
        for (Requirement requirement : candidates) {
            if (!failureInfoSupport.canRetry(requirement, now)) {
                continue;
            }
            RequirementStatus previousStatus = requirement.getStatus();
            if (requirementDao.updateStatusIfMatch(requirement.getId(), previousStatus,
                    RequirementStatus.PRD_GENERATING) == 0) {
                continue;
            }
            requirement.setStatus(RequirementStatus.PRD_GENERATING);
            requirement.setGenerationToken(GenerationTokenSupport.newToken());
            failureInfoSupport.markRetrying(requirement, TriggerSource.SCHEDULED_COMPENSATION, now);
            requirementDao.save(requirement);
            compensationLogService.record("REQUIREMENT", requirement.getId(), "RETRY_PRD", true,
                    "恢复 PRD 生成", requirement.getFailureSummary(), TriggerSource.SCHEDULED_COMPENSATION);
            log.info("恢复prd生成，prd {}", requirement.getTitle());
            String prompt = "上次生成失败，请结合失败摘要重试："
                    + Objects.toString(requirement.getFailureSummary(), "未知失败");
            String generationToken = requirement.getGenerationToken();
            TransactionUtil.afterCommit(() -> requirementAgentService.spawnPrd(
                    requirement.getId(), prompt, generationToken));
        }
    }

    /**
     * 为仍停留在 PRD_REVIEW、但当前设计上下文已 STALE 的需求生成新版本 PRD。
     */
    private void compensateStalePrdContexts(Date now) {
        List<RequirementDesignContext> staleContexts = requirementDesignContextDao.findByContextStatusAndStatus(
                RequirementDesignContextStatus.STALE, MemoryRecordStatus.CURRENT);
        if (staleContexts == null) {
            return;
        }
        for (RequirementDesignContext context : staleContexts) {
            Requirement requirement = requirementDao.findOne(context.getRequirementId());
            if (requirement == null
                    || requirement.getStatus() != RequirementStatus.PRD_REVIEW
                    || !Objects.equals(requirement.getDesignContextId(), context.getId())) {
                continue;
            }
            if (requirementDao.updateStatusIfMatch(requirement.getId(), RequirementStatus.PRD_REVIEW,
                    RequirementStatus.PRD_GENERATING) == 0) {
                continue;
            }
            requirement.setStatus(RequirementStatus.PRD_GENERATING);
            requirement.setPrdVersion(Objects.requireNonNullElse(requirement.getPrdVersion(), 0) + 1);
            requirement.setLastRetryAt(now);
            requirement.setGenerationToken(GenerationTokenSupport.newToken());
            requirementDao.save(requirement);
            compensationLogService.record("REQUIREMENT", requirement.getId(), "REGENERATE_STALE_PRD", true,
                    "设计上下文 STALE，生成新版本 PRD", null, TriggerSource.SCHEDULED_COMPENSATION);
            log.info("设计上下文 STALE，重新生成 PRD，requirement {}，version {}",
                    requirement.getId(), requirement.getPrdVersion());
            requirementCommentService.append(
                    requirement.getId(), requirement.getActiveLoopId(), RequirementCommentAuthorType.SYSTEM,
                    "设计上下文", RequirementCommentType.VALIDATION_RESULT,
                    "补偿器检测到设计上下文已过期（STALE）；已自动触发 PRD v"
                            + requirement.getPrdVersion() + " 重新生成。", null);
            String generationToken = requirement.getGenerationToken();
            TransactionUtil.afterCommit(() -> requirementAgentService.spawnPrd(
                    requirement.getId(),
                    "补偿恢复：设计上下文已过期，请基于最新需求与 WorkspaceMemory 重新生成 PRD。",
                    generationToken));
        }
    }

    /**
     * Reconciles every PRD-confirmed requirement according to its persisted automation status.
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateRequirementLoops(Date now) {
        for (Requirement requirement : requirementDao.findByStatus(RequirementStatus.PRD_CONFIRMED)) {
            RequirementAutomationStatus status = requirement.getAutomationStatus();
            if (status == null) {
                continue;
            }
            switch (status) {
                case PLANNING, FAILED -> recoverPlanning(requirement, now);
                case DEVELOPING -> recoverDevelopment(requirement, now);
                case VALIDATING, ACCEPTING -> recoverAcceptance(requirement, now);
                case DELIVERING -> recoverDelivery(requirement, now);
                default -> {
                    // IDLE, INTERRUPTED, WAITING_HUMAN and COMPLETED are deliberate human/terminal boundaries.
                }
            }
        }
    }

    private void recoverPlanning(Requirement requirement, Date now) {
        ExecutionPlan current = currentPlan(requirement);
        if (current != null && (current.getStatus() == ExecutionPlanStatus.READY
                || current.getStatus() == ExecutionPlanStatus.DEVELOPING)) {
            TransactionUtil.afterCommit(() -> automationService.resumeDevelopmentLoop(
                    requirement.getId(), requirement.getActiveLoopId()));
            return;
        }
        if (!runDao.findByRequirementIdAndState(requirement.getId(), RunState.RUNNING).isEmpty()) {
            log.debug("跳过 PM 规划补偿，requirement {} 仍有运行中的 Run", requirement.getId());
            return;
        }
        if (requirement.getAutomationStatus() == RequirementAutomationStatus.PLANNING
                && !isStale(requirement.getLastEditedDate(), now)) {
            log.debug("跳过 PM 规划补偿，requirement {} 的 PLANNING 状态尚未过期", requirement.getId());
            return;
        }
        if (!failureInfoSupport.canRetry(requirement, now)) {
            return;
        }

        ExecutionPlan latest = executionPlanDao.findTopByRequirementIdOrderByVersionDesc(requirement.getId());
        ExecutionPlanType planType = current != null && current.getPlanType() != null
                ? current.getPlanType() : latest == null ? ExecutionPlanType.INITIAL : ExecutionPlanType.CHANGE_REQUEST;
        failureInfoSupport.markRetrying(requirement, TriggerSource.SCHEDULED_COMPENSATION, now);
        requirement.setAutomationStatus(RequirementAutomationStatus.PLANNING);
        requirementDao.save(requirement);
        compensationLogService.record("REQUIREMENT", requirement.getId(), "RESUME_PLANNING", true,
                "恢复 PM 执行计划生成", null, TriggerSource.SCHEDULED_COMPENSATION);
        log.info("恢复 PM 执行计划生成，requirement {}，loopId {}",
                requirement.getId(), requirement.getActiveLoopId());

        String loopId = requirement.getActiveLoopId();
        if (loopId == null || loopId.isBlank()) {
            TransactionUtil.afterCommit(() -> automationService.startInitialLoop(requirement.getId()));
            return;
        }
        TransactionUtil.afterCommit(() -> automationService.retryPreparedLoop(
                requirement.getId(), loopId, planType,
                "补偿恢复：重新生成当前 loop 的 PM 执行计划。"));
    }

    /**
     * 补偿开发态失败任务（方案 §8 补偿策略收敛）。
     *
     * <p>补偿器只恢复既有 PM 决策，不产生新的业务决策。允许自动恢复的范围：</p>
     * <ul>
     *   <li>已取得 PM {@code RETRY} 决策但调度事件丢失的任务（重置 PENDING 并重新调度）；</li>
     *   <li>服务重启导致的孤儿任务：仍 PENDING 的可正常调度，无需补偿；</li>
     *   <li>已明确 {@code TRANSIENT_INFRA} 且策略允许自动恢复的基础设施故障。</li>
     * </ul>
     * <p>禁止自动补偿：{@code VALIDATION_FAILED}、{@code DELIVERY_INCOMPLETE}、
     * {@code UPSTREAM_INCOMPLETE}、{@code PLAN_DEFECT}、尚未完成 PM 审阅的失败任务、
     * {@code WAITING_HUMAN} 需求。补偿器对这类任务保留原失败证据，等待 PM 决策或人工介入。</p>
     */
    private void recoverDevelopment(Requirement requirement, Date now) {
        ExecutionPlan plan = currentPlan(requirement);
        if (plan == null || (plan.getStatus() != ExecutionPlanStatus.READY
                && plan.getStatus() != ExecutionPlanStatus.DEVELOPING)) {
            return;
        }
        // WAITING_HUMAN 需求不自动补偿（方案 §8）。
        if (requirement.getAutomationStatus() == RequirementAutomationStatus.WAITING_HUMAN
                || requirement.getAutomationStatus() == RequirementAutomationStatus.INTERRUPTED) {
            return;
        }
        boolean blockedByReview = false;
        boolean resumed = false;
        List<CodingTask> tasks = codingTaskDao.findByRequirementId(requirement.getId()).stream()
                .filter(task -> Objects.equals(task.getExecutionPlanId(), plan.getId()))
                .filter(task -> Objects.equals(task.getLoopId(), requirement.getActiveLoopId()))
                .toList();
        for (CodingTask task : tasks) {
            if (task.getStatus() != CodingTaskStatus.FAILED
                    && task.getStatus() != CodingTaskStatus.VALIDATION_FAILED) {
                continue;
            }
            // 失败任务必须先有 PM 决策；未审阅的失败重发审阅事件，不自动恢复。
            TaskDeliveryReview review = taskDeliveryReviewService
                    .findFirstByCodingTaskId(task.getId()).orElse(null);
            if (review == null || review.getStatus() == TaskDeliveryReviewStatus.PENDING
                    || review.getStatus() == TaskDeliveryReviewStatus.REVIEWING) {
                if (review != null && review.getStatus() == TaskDeliveryReviewStatus.PENDING) {
                    TransactionUtil.afterCommit(() -> eventPublisher.publishEvent(
                            new TaskDeliveryReviewRequested(requirement.getId(), review.getId(), task.getId())));
                    compensationLogService.record("CODING_TASK", task.getId(), "REPOST_REVIEW", true,
                            "补发未决 PM 审阅事件",
                            "deliveryRunId=" + review.getDeliveryRunId(), TriggerSource.SCHEDULED_COMPENSATION);
                }
                blockedByReview = true;
                continue;
            }
            // 仅 PM 决策 RETRY 的任务允许自动恢复；WAITING_HUMAN / REPLAN 已有自己的副作用路径。
            if (review.getDecision() != TaskDeliveryReviewDecision.RETRY) {
                continue;
            }
            // VALIDATION_FAILED 即使 PM 错误地 RETRY，补偿器也不自动恢复（方案 §8 硬约束）。
            if (!isAutoRecoverable(review, task)) {
                continue;
            }
            if (!failureInfoSupport.canRetry(task, now)) {
                continue;
            }
            CodingTaskStatus failedStatus = task.getStatus();
            if (codingTaskDao.updateStatusIfMatch(task.getId(), failedStatus, CodingTaskStatus.PENDING) == 0) {
                continue;
            }
            // 关键：补偿器只重置状态并重新调度，不再调用 markRetrying（retryCount 已在 PM RETRY 时增加一次）。
            task.setStatus(CodingTaskStatus.PENDING);
            codingTaskDao.save(task);
            compensationLogService.record("CODING_TASK", task.getId(), "RETRY_LOOP_TASK", true,
                    "基于 PM RETRY 决策恢复 loop 编码任务",
                    "decisionSource=PM_REVIEW, deliveryRunId=" + review.getDeliveryRunId()
                            + ", category=" + review.getFailureCategory(),
                    TriggerSource.SCHEDULED_COMPENSATION);
            log.info("恢复 loop 编码任务（PM RETRY 决策），task {}，requirement {}，deliveryRunId={}",
                    task.getId(), requirement.getId(), review.getDeliveryRunId());
            resumed = true;
        }
        // 仅在没有未决审阅阻塞时才恢复调度：失败的/未审阅的任务等待 PM 决策，
        // 否则恢复调度会在门禁下被拦住。无失败任务的崩溃恢复（服务重启孤儿任务）仍走此路径。
        if (!blockedByReview) {
            TransactionUtil.afterCommit(() -> automationService.resumeDevelopmentLoop(
                    requirement.getId(), requirement.getActiveLoopId()));
        }
    }

    /**
     * 失败分类是否允许自动恢复（方案 §8）。
     * 仅 TRANSIENT_INFRA 允许自动恢复；其余分类禁止。
     */
    private boolean isAutoRecoverable(TaskDeliveryReview review, CodingTask task) {
        if (task.getStatus() == CodingTaskStatus.VALIDATION_FAILED) {
            return false;
        }
        DeliveryFailureCategory category = review.getFailureCategory();
        return category == DeliveryFailureCategory.TRANSIENT_INFRA;
    }

    private void recoverAcceptance(Requirement requirement, Date now) {
        if (!isStale(requirement.getLastEditedDate(), now)) {
            return;
        }
        compensationLogService.record("REQUIREMENT", requirement.getId(), "RESUME_ACCEPTANCE", true,
                "恢复验证/验收边界", null, TriggerSource.SCHEDULED_COMPENSATION);
        log.info("恢复验证/验收边界，requirement {}，loopId {}",
                requirement.getId(), requirement.getActiveLoopId());
        TransactionUtil.afterCommit(() -> automationService.onPlanTasksSettled(requirement.getId()));
    }

    private void recoverDelivery(Requirement requirement, Date now) {
        if (!isStale(requirement.getLastEditedDate(), now)) {
            return;
        }
        compensationLogService.record("REQUIREMENT", requirement.getId(), "RESUME_DELIVERY", true,
                "恢复 GitLab MR 交付", null, TriggerSource.SCHEDULED_COMPENSATION);
        log.info("恢复 GitLab MR 交付，requirement {}", requirement.getId());
        TransactionUtil.afterCommit(() -> deliveryService.retry(requirement.getId()));
    }

    private ExecutionPlan currentPlan(Requirement requirement) {
        if (requirement.getActiveLoopId() == null) {
            return null;
        }
        return executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                requirement.getId(), requirement.getActiveLoopId());
    }

    private boolean isStale(Date lastEditedAt, Date now) {
        return lastEditedAt == null
                || lastEditedAt.getTime() <= now.getTime() - ocConfig.getLoopStaleMinutes() * 60_000L;
    }
}
