package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.DetailedDesignDao;
import com.changhong.onlinecode.dao.OverviewDesignDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.DetailedDesignStatus;
import com.changhong.onlinecode.dto.enums.OverviewDesignStatus;
import com.changhong.onlinecode.dto.enums.RequirementAutomationStatus;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.DetailedDesign;
import com.changhong.onlinecode.entity.OverviewDesign;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 新流程补偿服务。
 *
 * <p>覆盖 Project -> Requirement(PRD) -> OverviewDesign -> DetailedDesign -> CodingTask -> Run
 * 全链路。职责：扫描失败/断链节点，做条件抢占后调用对应 agent 或服务，并记录补偿日志。</p>
 *
 * <p>补偿器不自动确认任何需要人工审阅的产物，不覆盖 DRAFT/REVIEW 等人工编辑态，
 * 仅重试 FAILED 和补齐已确认上游的缺失下游。</p>
 */
@Service
public class CompensationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompensationService.class);

    private final RequirementDao requirementDao;
    private final OverviewDesignDao overviewDesignDao;
    private final DetailedDesignDao detailedDesignDao;
    private final CodingTaskDao codingTaskDao;
    private final RunDao runDao;

    private final RequirementAgentService requirementAgentService;
    private final OverviewDesignService overviewDesignService;
    private final OverviewDesignAgentService overviewDesignAgentService;
    private final DetailedDesignService detailedDesignService;
    private final DetailedDesignAgentService detailedDesignAgentService;
    private final CodingTaskService codingTaskService;
    private final CodingTaskExecutionService codingTaskExecutionService;

    private final FailureInfoSupport failureInfoSupport;
    private final CompensationLogService compensationLogService;
    private final TransactionTemplate transactionTemplate;

    @Value("${onlinecode.compensation.auto-run-enabled:true}")
    private boolean autoRunEnabled;

    @Value("${onlinecode.compensation.run-timeout-minutes:30}")
    private long runTimeoutMinutes;

    @Value("${onlinecode.compensation.prd-generating-timeout-minutes:30}")
    private long prdGeneratingTimeoutMinutes;

    public CompensationService(RequirementDao requirementDao,
                               OverviewDesignDao overviewDesignDao,
                               DetailedDesignDao detailedDesignDao,
                               CodingTaskDao codingTaskDao,
                               RunDao runDao,
                               RequirementAgentService requirementAgentService,
                               OverviewDesignService overviewDesignService,
                               OverviewDesignAgentService overviewDesignAgentService,
                               DetailedDesignService detailedDesignService,
                               DetailedDesignAgentService detailedDesignAgentService,
                               CodingTaskService codingTaskService,
                               CodingTaskExecutionService codingTaskExecutionService,
                               FailureInfoSupport failureInfoSupport,
                               CompensationLogService compensationLogService,
                               PlatformTransactionManager transactionManager) {
        this.requirementDao = requirementDao;
        this.overviewDesignDao = overviewDesignDao;
        this.detailedDesignDao = detailedDesignDao;
        this.codingTaskDao = codingTaskDao;
        this.runDao = runDao;
        this.requirementAgentService = requirementAgentService;
        this.overviewDesignService = overviewDesignService;
        this.overviewDesignAgentService = overviewDesignAgentService;
        this.detailedDesignService = detailedDesignService;
        this.detailedDesignAgentService = detailedDesignAgentService;
        this.codingTaskService = codingTaskService;
        this.codingTaskExecutionService = codingTaskExecutionService;
        this.failureInfoSupport = failureInfoSupport;
        this.compensationLogService = compensationLogService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 执行一轮补偿扫描。按“先上游、后下游；先修复失败、后补齐缺失”的顺序执行，
     * 避免下游在依赖缺失时重复重试。
     *
     * <p>每个补偿阶段独立捕获异常，防止单阶段失败影响后续阶段。</p>
     */
    public void runCycle() {
        long start = System.currentTimeMillis();
        Date now = new Date();
        LOGGER.info("补偿扫描开始，时间={}", now);
        runPhase("补偿失败需求", () -> compensateFailedRequirements(now));
        runPhase("补齐缺失概览设计", () -> compensateMissingOverviewDesigns(now));
        runPhase("补偿失败概览设计", () -> compensateFailedOverviewDesigns(now));
        runPhase("补齐缺失详细设计", () -> compensateMissingDetailedDesigns(now));
        runPhase("补偿失败详细设计", () -> compensateFailedDetailedDesigns(now));
        runPhase("补齐缺失编码任务", () -> compensateMissingCodingTasks(now));
        if (autoRunEnabled) {
            runPhase("补偿失败编码任务", () -> compensateFailedCodingTasks(now));
            runPhase("超时运行收口", () -> timeoutRunningRuns(now));
        } else {
            LOGGER.debug("自动运行已关闭，跳过编码任务补偿与运行超时收口");
        }
        LOGGER.info("补偿扫描结束，耗时={}ms", System.currentTimeMillis() - start);
    }

    private void runPhase(String phaseName, Runnable action) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("补偿阶段 [{}] 开始", phaseName);
        }
        try {
            transactionTemplate.executeWithoutResult(status -> action.run());
        } catch (Exception e) {
            LOGGER.error("补偿阶段 [{}] 执行异常", phaseName, e);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("补偿阶段 [{}] 结束", phaseName);
        }
    }

    /**
     * 1. PRD 生成失败或长时间卡在 PRD_GENERATING：满足重试窗口后重新发起 prd-agent。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateFailedRequirements(Date now) {
        List<Requirement> candidates = new ArrayList<>(requirementDao.findByStatus(RequirementStatus.FAILED));
        for (Requirement requirement : requirementDao.findByStatus(RequirementStatus.PRD_GENERATING)) {
            if (isPrdGeneratingStuck(requirement, now)) {
                candidates.add(requirement);
            }
        }
        if (candidates.isEmpty()) {
            LOGGER.debug("没有失败或卡住的 PRD 生成需求需要补偿");
            return;
        }
        LOGGER.info("扫描到 {} 个失败/卡住需求，准备按重试窗口补偿", candidates.size());
        int skipped = 0;
        int retried = 0;
        int stuckRetried = 0;
        for (Requirement requirement : candidates) {
            boolean wasStuckGenerating = requirement.getStatus() == RequirementStatus.PRD_GENERATING;
            if (!failureInfoSupport.canRetry(requirement, now)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("需求 {} 尚未到达重试窗口，跳过", requirement.getId());
                }
                skipped++;
                continue;
            }
            String requirementId = requirement.getId();
            String summary = requirement.getFailureSummary();
            if (requirementDao.updateStatusIfMatch(requirementId, requirement.getStatus(),
                    RequirementStatus.PRD_GENERATING) == 0) {
                LOGGER.info("需求 {} 抢占补偿失败，跳过本轮", requirementId);
                skipped++;
                continue;
            }
            requirement.setStatus(RequirementStatus.PRD_GENERATING);
            failureInfoSupport.markRetrying(requirement, TriggerSource.SCHEDULED_COMPENSATION, now);
            requirement.setGenerationToken(GenerationTokenSupport.newToken());
            requirementDao.save(requirement);
            if (wasStuckGenerating) {
                stuckRetried++;
            }
            compensationLogService.record("REQUIREMENT", requirementId, "RETRY_PRD", true,
                    "补偿重试 PRD", summary, TriggerSource.SCHEDULED_COMPENSATION);
            LOGGER.info("需求 {} 进入 PRD 补偿重试，当前重试次数={}", requirementId, requirement.getRetryCount());
            String hint = retryHint(summary);
            String generationToken = requirement.getGenerationToken();
            TransactionUtil.afterCommit(() -> requirementAgentService.spawnPrd(requirementId, hint, generationToken));
            retried++;
        }
        LOGGER.info("失败需求补偿完成，跳过={}，重试={}（其中卡住={}）", skipped, retried, stuckRetried);
    }

    /**
     * 2. PRD 已确认但缺失概览设计：创建 GENERATING 概览设计并触发 overview-design-agent。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateMissingOverviewDesigns(Date now) {
        List<Requirement> candidates = requirementDao.findByStatus(RequirementStatus.PRD_CONFIRMED);
        if (candidates.isEmpty()) {
            LOGGER.debug("没有已确认 PRD 的需求需要补齐概览设计");
            return;
        }
        LOGGER.info("扫描到 {} 个已确认 PRD 需求，准备补齐概览设计", candidates.size());
        int filled = 0;
        int existed = 0;
        for (Requirement requirement : candidates) {
            if (requirement.getAutomationStatus() != null
                    && requirement.getAutomationStatus() != RequirementAutomationStatus.IDLE) {
                continue;
            }
            OverviewDesign overview = overviewDesignDao.findByRequirementId(requirement.getId());
            if (overview != null) {
                existed++;
                continue;
            }
            compensationLogService.record("REQUIREMENT", requirement.getId(), "FILL_MISSING_OVERVIEW", true,
                    "补齐概览设计", null, TriggerSource.CHAIN_COMPENSATION);
            LOGGER.info("需求 {} 缺失概览设计，触发补齐", requirement.getId());
            overviewDesignService.createGeneratingOverview(requirement);
            filled++;
        }
        LOGGER.info("概览设计补齐完成，已存在={}，新补齐={}", existed, filled);
    }

    /**
     * 3. 概览设计生成失败：满足重试窗口后重生成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateFailedOverviewDesigns(Date now) {
        List<OverviewDesign> candidates = overviewDesignDao.findByStatus(OverviewDesignStatus.FAILED);
        if (candidates.isEmpty()) {
            LOGGER.debug("没有失败的概览设计需要补偿");
            return;
        }
        LOGGER.info("扫描到 {} 个失败概览设计，准备按重试窗口补偿", candidates.size());
        int skipped = 0;
        int retried = 0;
        for (OverviewDesign overview : candidates) {
            if (!failureInfoSupport.canRetry(overview, now)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("概览设计 {} 尚未到达重试窗口，跳过", overview.getId());
                }
                skipped++;
                continue;
            }
            if (overviewDesignDao.updateStatusIfMatch(overview.getId(), OverviewDesignStatus.FAILED,
                    OverviewDesignStatus.GENERATING) == 0) {
                LOGGER.info("概览设计 {} 抢占补偿失败，跳过本轮", overview.getId());
                skipped++;
                continue;
            }
            overview.setStatus(OverviewDesignStatus.GENERATING);
            failureInfoSupport.markRetrying(overview, TriggerSource.SCHEDULED_COMPENSATION, now);
            overview.setVersion(overview.getVersion() + 1);
            overview.setGenerationToken(GenerationTokenSupport.newToken());
            overviewDesignDao.save(overview);
            compensationLogService.record("OVERVIEW_DESIGN", overview.getId(), "RETRY_OVERVIEW", true,
                    "补偿重试概览设计", overview.getFailureSummary(), TriggerSource.SCHEDULED_COMPENSATION);
            LOGGER.info("概览设计 {} 进入补偿重试，当前重试次数={}", overview.getId(), overview.getRetryCount());
            String prompt = retryHint(overview.getFailureSummary());
            String generationToken = overview.getGenerationToken();
            TransactionUtil.afterCommit(() -> overviewDesignAgentService.spawnOverviewDesign(overview.getId(), prompt,
                    generationToken));
            retried++;
        }
        LOGGER.info("失败概览设计补偿完成，跳过={}，重试={}", skipped, retried);
    }

    /**
     * 4. 概览设计已确认但缺失详细设计：按 content 中的 feature 列表补建详细设计。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateMissingDetailedDesigns(Date now) {
        List<OverviewDesign> candidates = overviewDesignDao.findByStatus(OverviewDesignStatus.CONFIRMED);
        if (candidates.isEmpty()) {
            LOGGER.debug("没有已确认概览设计需要补齐详细设计");
            return;
        }
        LOGGER.info("扫描到 {} 个已确认概览设计，准备补齐详细设计", candidates.size());
        int allCreated = 0;
        int partialCreated = 0;
        int skipped = 0;
        for (OverviewDesign overview : candidates) {
            List<DetailedDesign> existing = detailedDesignDao.findByOverviewDesignId(overview.getId());
            if (existing == null || existing.isEmpty()) {
                compensationLogService.record("OVERVIEW_DESIGN", overview.getId(), "FILL_DETAILED_DESIGNS", true,
                        "补齐全部详细设计", null, TriggerSource.CHAIN_COMPENSATION);
                LOGGER.info("概览设计 {} 没有详细设计，补齐全部", overview.getId());
                detailedDesignService.createFromOverviewDesign(overview);
                allCreated++;
                continue;
            }
            long confirmedCount = existing.stream()
                    .filter(d -> d.getStatus() == DetailedDesignStatus.CONFIRMED)
                    .count();
            if (confirmedCount == existing.size()) {
                // 全部已确认，不再补充；若上游已变应由人工触发重生成
                skipped++;
                continue;
            }
            compensationLogService.record("OVERVIEW_DESIGN", overview.getId(), "FILL_MISSING_DETAILED_DESIGNS", true,
                    "补齐缺失详细设计", null, TriggerSource.CHAIN_COMPENSATION);
            LOGGER.info("概览设计 {} 部分详细设计未确认，补齐缺失", overview.getId());
            detailedDesignService.createMissingFromOverviewDesign(overview);
            partialCreated++;
        }
        LOGGER.info("详细设计补齐完成，已跳过={}，全量补齐={}，部分补齐={}", skipped, allCreated, partialCreated);
    }

    /**
     * 5. 详细设计生成失败：满足重试窗口后重生成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateFailedDetailedDesigns(Date now) {
        List<DetailedDesign> candidates = detailedDesignDao.findByStatus(DetailedDesignStatus.FAILED);
        if (candidates.isEmpty()) {
            LOGGER.debug("没有失败的详细设计需要补偿");
            return;
        }
        LOGGER.info("扫描到 {} 个失败详细设计，准备按重试窗口补偿", candidates.size());
        int skipped = 0;
        int retried = 0;
        for (DetailedDesign design : candidates) {
            if (!failureInfoSupport.canRetry(design, now)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("详细设计 {} 尚未到达重试窗口，跳过", design.getId());
                }
                skipped++;
                continue;
            }
            if (detailedDesignDao.updateStatusIfMatch(design.getId(), DetailedDesignStatus.FAILED,
                    DetailedDesignStatus.GENERATING) == 0) {
                LOGGER.info("详细设计 {} 抢占补偿失败，跳过本轮", design.getId());
                skipped++;
                continue;
            }
            design.setStatus(DetailedDesignStatus.GENERATING);
            failureInfoSupport.markRetrying(design, TriggerSource.SCHEDULED_COMPENSATION, now);
            design.setVersion(design.getVersion() + 1);
            design.setGenerationToken(GenerationTokenSupport.newToken());
            detailedDesignDao.save(design);
            compensationLogService.record("DETAILED_DESIGN", design.getId(), "RETRY_DETAILED", true,
                    "补偿重试详细设计", design.getFailureSummary(), TriggerSource.SCHEDULED_COMPENSATION);
            LOGGER.info("详细设计 {} 进入补偿重试，当前重试次数={}", design.getId(), design.getRetryCount());
            String prompt = retryHint(design.getFailureSummary());
            String generationToken = design.getGenerationToken();
            TransactionUtil.afterCommit(() -> detailedDesignAgentService.spawnDetailedDesign(design.getId(), prompt,
                    generationToken));
            retried++;
        }
        LOGGER.info("失败详细设计补偿完成，跳过={}，重试={}", skipped, retried);
    }

    /**
     * 6. 详细设计已确认但缺失编码任务：创建 CodingTask。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateMissingCodingTasks(Date now) {
        List<DetailedDesign> candidates = detailedDesignDao.findByStatus(DetailedDesignStatus.CONFIRMED);
        if (candidates.isEmpty()) {
            LOGGER.debug("没有已确认详细设计需要补齐编码任务");
            return;
        }
        LOGGER.info("扫描到 {} 个已确认详细设计，准备补齐编码任务", candidates.size());
        int filled = 0;
        int existed = 0;
        for (DetailedDesign design : candidates) {
            List<CodingTask> tasks = codingTaskDao.findByDetailedDesignId(design.getId());
            if (tasks != null && !tasks.isEmpty()) {
                existed++;
                continue;
            }
            compensationLogService.record("DETAILED_DESIGN", design.getId(), "FILL_MISSING_CODING_TASK", true,
                    "补齐编码任务", null, TriggerSource.CHAIN_COMPENSATION);
            LOGGER.info("详细设计 {} 缺失编码任务，触发补齐", design.getId());
            codingTaskService.createFromDetailedDesign(design);
            filled++;
        }
        LOGGER.info("编码任务补齐完成，已存在={}，新补齐={}", existed, filled);
    }

    /**
     * 7. 编码任务失败：满足重试窗口后重跑。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateFailedCodingTasks(Date now) {
        List<CodingTask> candidates = codingTaskDao.findByStatus(CodingTaskStatus.FAILED);
        if (candidates.isEmpty()) {
            LOGGER.debug("没有失败的编码任务需要补偿");
            return;
        }
        LOGGER.info("扫描到 {} 个失败编码任务，准备按重试窗口补偿", candidates.size());
        int skipped = 0;
        int retried = 0;
        for (CodingTask task : candidates) {
            if (!failureInfoSupport.canRetry(task, now)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("编码任务 {} 尚未到达重试窗口，跳过", task.getId());
                }
                skipped++;
                continue;
            }
            if (codingTaskDao.updateStatusIfMatch(task.getId(), CodingTaskStatus.FAILED, CodingTaskStatus.PENDING) == 0) {
                LOGGER.info("编码任务 {} 抢占补偿失败，跳过本轮", task.getId());
                skipped++;
                continue;
            }
            task.setStatus(CodingTaskStatus.PENDING);
            failureInfoSupport.markRetrying(task, TriggerSource.SCHEDULED_COMPENSATION, now);
            codingTaskDao.save(task);
            compensationLogService.record("CODING_TASK", task.getId(), "RETRY_CODING_TASK", true,
                    "补偿重试编码任务", task.getFailureSummary(), TriggerSource.SCHEDULED_COMPENSATION);
            LOGGER.info("编码任务 {} 进入补偿重试，当前重试次数={}", task.getId(), task.getRetryCount());
            String prompt = retryHint(task.getFailureSummary());
            TransactionUtil.afterCommit(() -> codingTaskExecutionService.execute(task.getId(), prompt));
            retried++;
        }
        LOGGER.info("失败编码任务补偿完成，跳过={}，重试={}", skipped, retried);
    }

    /**
     * 8. 运行中超时收口：将超时的 Run 标记为 FAILED，并同步更新关联 CodingTask。
     * 不在同一轮直接重跑，由下一轮 {@link #compensateFailedCodingTasks} 处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public void timeoutRunningRuns(Date now) {
        Date cutoff = new Date(now.getTime() - runTimeoutMinutes * 60_000L);
        List<Run> candidates = runDao.findByState(RunState.RUNNING);
        if (candidates.isEmpty()) {
            LOGGER.debug("没有运行中的 Run 需要超时检查");
            return;
        }
        LOGGER.info("扫描到 {} 个运行中 Run，准备按截止时间 {} 检查超时", candidates.size(), cutoff);
        int timeoutCount = 0;
        int safeCount = 0;
        for (Run run : candidates) {
            Date started = run.getStartedDate();
            if (started == null || !started.before(cutoff)) {
                safeCount++;
                continue;
            }
            if (runDao.updateStateIfMatch(run.getId(), RunState.RUNNING, RunState.FAILED) == 0) {
                safeCount++;
                continue;
            }
            run.setState(RunState.FAILED);
            run.setFinishedDate(now);
            run.setFailureSummary("编码执行超时");
            run.setFailureReason("RUNNING 超过超时时间未收口");
            runDao.save(run);

            if (run.getCodingTaskId() != null) {
                CodingTask task = codingTaskDao.findOne(run.getCodingTaskId());
                if (task != null) {
                    if (task.getStatus() != CodingTaskStatus.RUNNING
                            && task.getStatus() != CodingTaskStatus.PENDING) {
                        LOGGER.info("Run {} 超时，但关联编码任务 {} 已非活动态 {}", run.getId(), task.getId(), task.getStatus());
                        safeCount++;
                        continue;
                    }
                    task.setStatus(CodingTaskStatus.FAILED);
                    failureInfoSupport.markCodingTaskFailure(task, "编码执行超时",
                            "RUNNING 超过超时时间未收口", TriggerSource.SCHEDULED_COMPENSATION, now);
                    codingTaskDao.save(task);
                    LOGGER.info("Run {} 超时，关联编码任务 {} 标记为 FAILED", run.getId(), task.getId());
                } else {
                    LOGGER.warn("Run {} 超时，但未找到关联编码任务 {}", run.getCodingTaskId());
                }
            }
            compensationLogService.record("RUN", run.getId(), "TIMEOUT_RUN", true,
                    "运行超时收口", "timeout", TriggerSource.SCHEDULED_COMPENSATION);
            timeoutCount++;
        }
        LOGGER.info("运行中超时收口完成，安全={}，超时={}", safeCount, timeoutCount);
    }

    private boolean isPrdGeneratingStuck(Requirement requirement, Date now) {
        Date reference = requirement.getLastRetryAt();
        if (reference == null) {
            reference = requirement.getCreatedDate();
        }
        if (reference == null) {
            return false;
        }
        long timeoutMs = prdGeneratingTimeoutMinutes * 60_000L;
        return now.getTime() - reference.getTime() >= timeoutMs;
    }

    private String retryHint(String summary) {
        if (summary == null || summary.isBlank()) {
            return "补偿重试";
        }
        return "补偿重试，最近失败原因：" + summary;
    }
}
