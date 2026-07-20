package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RequirementDesignContextDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.MemoryRecordStatus;
import com.changhong.onlinecode.dto.enums.ObservationSourceType;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.RunObservationType;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.RunTerminalReason;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.enums.VerificationStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.service.progress.ProgressReconciler;
import com.changhong.onlinecode.service.progress.ProgressService;
import com.changhong.sei.core.utils.TransactionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
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
    private final ProgressService progressService;
    private final TransactionTemplate transactionTemplate;

    @Value("${onlinecode.compensation.loop-stale-minutes:30}")
    private long loopStaleMinutes = 30;

    @Value("${onlinecode.compensation.run-timeout-minutes:30}")
    private long runTimeoutMinutes = 30;

    public CompensationService(RequirementDao requirementDao,
                               RequirementDesignContextDao requirementDesignContextDao,
                               ExecutionPlanDao executionPlanDao,
                               CodingTaskDao codingTaskDao,
                               RunDao runDao,
                               RequirementAgentService requirementAgentService,
                               RequirementAutomationService automationService,
                               RequirementDeliveryService deliveryService,
                               FailureInfoSupport failureInfoSupport,
                               CompensationLogService compensationLogService,
                               RequirementCommentService requirementCommentService,
                               ProgressReconciler progressReconciler,
                               ProgressService progressService,
                               PlatformTransactionManager transactionManager) {
        this.requirementDao = requirementDao;
        this.requirementDesignContextDao = requirementDesignContextDao;
        this.executionPlanDao = executionPlanDao;
        this.codingTaskDao = codingTaskDao;
        this.runDao = runDao;
        this.requirementAgentService = requirementAgentService;
        this.automationService = automationService;
        this.deliveryService = deliveryService;
        this.failureInfoSupport = failureInfoSupport;
        this.compensationLogService = compensationLogService;
        this.requirementCommentService = requirementCommentService;
        this.progressReconciler = progressReconciler;
        this.progressService = progressService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

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
     * 标记超时 Run 为 UNKNOWN（EXE-007：不再直接 FAILED），调用 ProgressReconciler 对账。
     * Run 状态改为 UNKNOWN 并追加 TERMINAL observation，不直接判定 CodingTask/Requirement 失败。
     */
    @Transactional(rollbackFor = Exception.class)
    public void timeoutRuns(Date now) {
        Date deadline = new Date(now.getTime() - runTimeoutMinutes * 60_000L);
        List<Run> runList = runDao.findByState(RunState.RUNNING);
        for (Run run : runList) {
            Date startedAt = run.getStartedDate();
            if (startedAt == null || startedAt.after(deadline)) {
                continue;
            }
            if (runDao.updateStateIfMatch(run.getId(), RunState.RUNNING, RunState.UNKNOWN) == 0) {
                continue;
            }
            run.setState(RunState.UNKNOWN);
            run.setTerminalReason(RunTerminalReason.TIMEOUT);
            run.setFinishedDate(now);
            run.setFailureSummary("运行超时（已进入 UNKNOWN，待对账）");
            run.setFailureReason("补偿器检测到 Run 超过 " + runTimeoutMinutes + " 分钟未结束；进度账本对账后将决定续作或接管");
            runDao.save(run);
            log.info("compensation runNo {} loopId {} 超时→UNKNOWN（EXE-007 对账流程）", run.getRunNo(), run.getLoopId());

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
                    log.info("PM agent 超时→UNKNOWN，requirement {} 仍保持 PLANNING，待对账后人工干预或自动续作",
                            requirement.getId());
                }
            }

            compensationLogService.record("RUN", run.getId(), "TIMEOUT_RUN_RECONCILE", true,
                    "超时 Run→UNKNOWN，已触发对账", run.getFailureReason(), TriggerSource.SCHEDULED_COMPENSATION);
        }
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

    private void recoverDevelopment(Requirement requirement, Date now) {
        ExecutionPlan plan = currentPlan(requirement);
        if (plan == null || (plan.getStatus() != ExecutionPlanStatus.READY
                && plan.getStatus() != ExecutionPlanStatus.DEVELOPING)) {
            return;
        }
        // EXE-007: 先对账 Execution 进度——有 VERIFIED steps 则从 nextAction 续作
        if (requirement.getActiveLoopId() != null) {
            try {
                ProgressReconciler.ExecutionReconciliation reconciliation =
                        progressReconciler.reconcileExecutionByRequirement(requirement.getId(), requirement.getActiveLoopId());
                if (reconciliation != null && reconciliation.allVerified()) {
                    log.info("recoverDevelopment: requirement {} Execution 全部 VERIFIED，跳过编码重试 → 推进验证",
                            requirement.getId());
                    TransactionUtil.afterCommit(() -> automationService.resumeDevelopmentLoop(
                            requirement.getId(), requirement.getActiveLoopId()));
                    return;
                }
                if (reconciliation != null && reconciliation.recoveredBlocked() > 0) {
                    log.info("recoverDevelopment: requirement {} 已自动解除 {} 个阻塞步骤 → 恢复开发调度",
                            requirement.getId(), reconciliation.recoveredBlocked());
                    TransactionUtil.afterCommit(() -> automationService.resumeDevelopmentLoop(
                            requirement.getId(), requirement.getActiveLoopId()));
                    return;
                }
            } catch (Exception e) {
                log.warn("recoverDevelopment: Execution 对账异常 requirement={}", requirement.getId(), e);
            }
        }
        boolean waitingForRetryWindow = false;
        List<CodingTask> tasks = codingTaskDao.findByRequirementId(requirement.getId()).stream()
                .filter(task -> Objects.equals(task.getExecutionPlanId(), plan.getId()))
                .filter(task -> Objects.equals(task.getLoopId(), requirement.getActiveLoopId()))
                .toList();
        for (CodingTask task : tasks) {
            if (task.getStatus() != CodingTaskStatus.FAILED
                    && task.getStatus() != CodingTaskStatus.VALIDATION_FAILED) {
                continue;
            }
            if (!failureInfoSupport.canRetry(task, now)) {
                waitingForRetryWindow |= Objects.requireNonNullElse(task.getRetryCount(), 0)
                        < failureInfoSupport.maxRetry();
                continue;
            }
            CodingTaskStatus failedStatus = task.getStatus();
            if (codingTaskDao.updateStatusIfMatch(task.getId(), failedStatus, CodingTaskStatus.PENDING) == 0) {
                continue;
            }
            task.setStatus(CodingTaskStatus.PENDING);
            failureInfoSupport.markRetrying(task, TriggerSource.SCHEDULED_COMPENSATION, now);
            codingTaskDao.save(task);
            compensationLogService.record("CODING_TASK", task.getId(), "RETRY_LOOP_TASK", true,
                    "恢复 loop 编码任务", task.getFailureSummary(), TriggerSource.SCHEDULED_COMPENSATION);
            log.info("恢复 loop 编码任务，task {}，requirement {}",
                    task.getId(), requirement.getId());
        }
        if (!waitingForRetryWindow) {
            TransactionUtil.afterCommit(() -> automationService.resumeDevelopmentLoop(
                    requirement.getId(), requirement.getActiveLoopId()));
        }
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
                || lastEditedAt.getTime() <= now.getTime() - loopStaleMinutes * 60_000L;
    }
}
