package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.ExecutionPlanDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanStatus;
import com.changhong.onlinecode.dto.enums.ExecutionPlanType;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.ExecutionPlan;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.sei.core.utils.TransactionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class CompensationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompensationService.class);

    private final RequirementDao requirementDao;
    private final ExecutionPlanDao executionPlanDao;
    private final CodingTaskDao codingTaskDao;
    private final RunDao runDao;
    private final RequirementAgentService requirementAgentService;
    private final RequirementAutomationService automationService;
    private final RequirementDeliveryService deliveryService;
    private final FailureInfoSupport failureInfoSupport;
    private final CompensationLogService compensationLogService;
    private final TransactionTemplate transactionTemplate;

    @Value("${onlinecode.compensation.loop-stale-minutes:30}")
    private long loopStaleMinutes = 30;

    @Value("${onlinecode.compensation.run-timeout-minutes:30}")
    private long runTimeoutMinutes = 30;

    public CompensationService(RequirementDao requirementDao,
                               ExecutionPlanDao executionPlanDao,
                               CodingTaskDao codingTaskDao,
                               RunDao runDao,
                               RequirementAgentService requirementAgentService,
                               RequirementAutomationService automationService,
                               RequirementDeliveryService deliveryService,
                               FailureInfoSupport failureInfoSupport,
                               CompensationLogService compensationLogService,
                               PlatformTransactionManager transactionManager) {
        this.requirementDao = requirementDao;
        this.executionPlanDao = executionPlanDao;
        this.codingTaskDao = codingTaskDao;
        this.runDao = runDao;
        this.requirementAgentService = requirementAgentService;
        this.automationService = automationService;
        this.deliveryService = deliveryService;
        this.failureInfoSupport = failureInfoSupport;
        this.compensationLogService = compensationLogService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Runs one ordered recovery pass: close timed-out work, then repair upstream to downstream. */
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
            LOGGER.error("compensation phase failed: {}", phase, e);
        }
    }

    /** Marks abandoned runs terminal so the persisted loop can be recovered safely. */
    @Transactional(rollbackFor = Exception.class)
    public void timeoutRuns(Date now) {
        Date deadline = new Date(now.getTime() - runTimeoutMinutes * 60_000L);
        for (Run run : runDao.findByState(RunState.RUNNING)) {
            Date startedAt = run.getStartedDate();
            if (startedAt == null || startedAt.after(deadline)) {
                continue;
            }
            if (runDao.updateStateIfMatch(run.getId(), RunState.RUNNING, RunState.FAILED) == 0) {
                continue;
            }
            run.setState(RunState.FAILED);
            run.setFinishedDate(now);
            run.setFailureSummary("运行超时");
            run.setFailureReason("补偿器检测到 Run 超过 " + runTimeoutMinutes + " 分钟未结束");
            runDao.save(run);

            CodingTask task = run.getCodingTaskId() == null ? null : codingTaskDao.findOne(run.getCodingTaskId());
            if (task != null && (task.getStatus() == CodingTaskStatus.RUNNING
                    || task.getStatus() == CodingTaskStatus.VALIDATING)) {
                task.setStatus(CodingTaskStatus.FAILED);
                failureInfoSupport.markCodingTaskFailure(task, "运行超时", run.getFailureReason(),
                        TriggerSource.SCHEDULED_COMPENSATION, now);
                codingTaskDao.save(task);
            }
            compensationLogService.record("RUN", run.getId(), "TIMEOUT_RUN", true,
                    "超时 Run 已收口", run.getFailureReason(), TriggerSource.SCHEDULED_COMPENSATION);
        }
    }

    /** Keeps the pre-loop PRD generation recovery that existed before the loop migration. */
    @Transactional(rollbackFor = Exception.class)
    public void compensatePrdGeneration(Date now) {
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
            String prompt = "上次生成失败，请结合失败摘要重试："
                    + Objects.toString(requirement.getFailureSummary(), "未知失败");
            String generationToken = requirement.getGenerationToken();
            TransactionUtil.afterCommit(() -> requirementAgentService.spawnPrd(
                    requirement.getId(), prompt, generationToken));
        }
    }

    /** Reconciles every PRD-confirmed requirement according to its persisted automation status. */
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
        if (hasActiveRun(requirement) || (requirement.getAutomationStatus() == RequirementAutomationStatus.PLANNING
                && !isStale(requirement.getLastEditedDate(), now))) {
            return;
        }
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

        String loopId = requirement.getActiveLoopId();
        TransactionUtil.afterCommit(() -> {
            automationService.executePreparedLoop(requirement.getId(), loopId, planType,
                    "补偿恢复：重新生成当前 loop 的 PM 执行计划。");
            Requirement recovered = requirementDao.findOne(requirement.getId());
            if (recovered != null && recovered.getAutomationStatus() == RequirementAutomationStatus.FAILED) {
                failureInfoSupport.markRequirementFailure(recovered, FailureCode.AGENT_EXECUTION_FAILED,
                        FailureStage.PLAN, "PM 执行计划补偿失败", "详见 FAILURE 评论",
                        TriggerSource.SCHEDULED_COMPENSATION, now);
                requirementDao.save(recovered);
            }
        });
    }

    private void recoverDevelopment(Requirement requirement, Date now) {
        ExecutionPlan plan = currentPlan(requirement);
        if (plan == null || (plan.getStatus() != ExecutionPlanStatus.READY
                && plan.getStatus() != ExecutionPlanStatus.DEVELOPING)) {
            return;
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
        }
        if (!waitingForRetryWindow && !hasActiveRun(requirement)) {
            TransactionUtil.afterCommit(() -> automationService.resumeDevelopmentLoop(
                    requirement.getId(), requirement.getActiveLoopId()));
        }
    }

    private void recoverAcceptance(Requirement requirement, Date now) {
        if (!isStale(requirement.getLastEditedDate(), now) || hasActiveRun(requirement)) {
            return;
        }
        compensationLogService.record("REQUIREMENT", requirement.getId(), "RESUME_ACCEPTANCE", true,
                "恢复验证/验收边界", null, TriggerSource.SCHEDULED_COMPENSATION);
        TransactionUtil.afterCommit(() -> automationService.onPlanTasksSettled(requirement.getId()));
    }

    private void recoverDelivery(Requirement requirement, Date now) {
        if (!isStale(requirement.getLastEditedDate(), now) || hasActiveRun(requirement)) {
            return;
        }
        compensationLogService.record("REQUIREMENT", requirement.getId(), "RESUME_DELIVERY", true,
                "恢复 GitLab MR 交付", null, TriggerSource.SCHEDULED_COMPENSATION);
        TransactionUtil.afterCommit(() -> deliveryService.retry(requirement.getId()));
    }

    private ExecutionPlan currentPlan(Requirement requirement) {
        if (requirement.getActiveLoopId() == null) {
            return null;
        }
        return executionPlanDao.findTopByRequirementIdAndLoopIdOrderByVersionDesc(
                requirement.getId(), requirement.getActiveLoopId());
    }

    private boolean hasActiveRun(Requirement requirement) {
        return !runDao.findByRequirementIdAndState(requirement.getId(), RunState.RUNNING).isEmpty();
    }

    private boolean isStale(Date lastEditedAt, Date now) {
        return lastEditedAt == null
                || lastEditedAt.getTime() <= now.getTime() - loopStaleMinutes * 60_000L;
    }
}
