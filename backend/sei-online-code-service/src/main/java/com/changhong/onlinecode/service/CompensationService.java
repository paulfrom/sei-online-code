package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.DetailedDesignDao;
import com.changhong.onlinecode.dao.OverviewDesignDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.dto.enums.DetailedDesignStatus;
import com.changhong.onlinecode.dto.enums.OverviewDesignStatus;
import com.changhong.onlinecode.dto.enums.RequirementStatus;
import com.changhong.onlinecode.dto.enums.RunState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.DetailedDesign;
import com.changhong.onlinecode.entity.OverviewDesign;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.entity.Run;
import com.changhong.sei.core.utils.TransactionUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final RequirementDao requirementDao;
    private final OverviewDesignDao overviewDesignDao;
    private final DetailedDesignDao detailedDesignDao;
    private final CodingTaskDao codingTaskDao;
    private final RunDao runDao;

    private final RequirementAgentService requirementAgentService;
    private final OverviewDesignService overviewDesignService;
    private final DetailedDesignService detailedDesignService;
    private final CodingTaskService codingTaskService;

    private final FailureInfoSupport failureInfoSupport;
    private final CompensationLogService compensationLogService;

    @Value("${onlinecode.compensation.auto-run-enabled:true}")
    private boolean autoRunEnabled;

    @Value("${onlinecode.compensation.run-timeout-minutes:30}")
    private long runTimeoutMinutes;

    public CompensationService(RequirementDao requirementDao,
                               OverviewDesignDao overviewDesignDao,
                               DetailedDesignDao detailedDesignDao,
                               CodingTaskDao codingTaskDao,
                               RunDao runDao,
                               RequirementAgentService requirementAgentService,
                               OverviewDesignService overviewDesignService,
                               DetailedDesignService detailedDesignService,
                               CodingTaskService codingTaskService,
                               FailureInfoSupport failureInfoSupport,
                               CompensationLogService compensationLogService) {
        this.requirementDao = requirementDao;
        this.overviewDesignDao = overviewDesignDao;
        this.detailedDesignDao = detailedDesignDao;
        this.codingTaskDao = codingTaskDao;
        this.runDao = runDao;
        this.requirementAgentService = requirementAgentService;
        this.overviewDesignService = overviewDesignService;
        this.detailedDesignService = detailedDesignService;
        this.codingTaskService = codingTaskService;
        this.failureInfoSupport = failureInfoSupport;
        this.compensationLogService = compensationLogService;
    }

    /**
     * 执行一轮补偿扫描。按“先上游、后下游；先修复失败、后补齐缺失”的顺序执行，
     * 避免下游在依赖缺失时重复重试。
     */
    public void runCycle() {
        Date now = new Date();
        compensateFailedRequirements(now);
        compensateMissingOverviewDesigns(now);
        compensateFailedOverviewDesigns(now);
        compensateMissingDetailedDesigns(now);
        compensateFailedDetailedDesigns(now);
        compensateMissingCodingTasks(now);
        if (autoRunEnabled) {
            compensateFailedCodingTasks(now);
            timeoutRunningRuns(now);
        }
    }

    /**
     * 1. PRD 生成失败：满足重试窗口后重新发起 prd-agent。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateFailedRequirements(Date now) {
        for (Requirement requirement : requirementDao.findByStatus(RequirementStatus.FAILED)) {
            if (!failureInfoSupport.canRetry(requirement, now)) {
                continue;
            }
            String requirementId = requirement.getId();
            String summary = requirement.getFailureSummary();
            failureInfoSupport.markRetrying(requirement, TriggerSource.SCHEDULED_COMPENSATION, now);
            requirement.setStatus(RequirementStatus.PRD_GENERATING);
            requirementDao.save(requirement);
            compensationLogService.record("REQUIREMENT", requirementId, "RETRY_PRD", true,
                    "补偿重试 PRD", summary, TriggerSource.SCHEDULED_COMPENSATION);
            String hint = retryHint(summary);
            TransactionUtil.afterCommit(() -> requirementAgentService.spawnPrd(requirementId, hint));
        }
    }

    /**
     * 2. PRD 已确认但缺失概览设计：创建 GENERATING 概览设计并触发 overview-design-agent。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateMissingOverviewDesigns(Date now) {
        for (Requirement requirement : requirementDao.findByStatus(RequirementStatus.PRD_CONFIRMED)) {
            OverviewDesign overview = overviewDesignDao.findByRequirementId(requirement.getId());
            if (overview != null) {
                continue;
            }
            compensationLogService.record("REQUIREMENT", requirement.getId(), "FILL_MISSING_OVERVIEW", true,
                    "补齐概览设计", null, TriggerSource.CHAIN_COMPENSATION);
            overviewDesignService.createGeneratingOverview(requirement);
        }
    }

    /**
     * 3. 概览设计生成失败：满足重试窗口后重生成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateFailedOverviewDesigns(Date now) {
        for (OverviewDesign overview : overviewDesignDao.findByStatus(OverviewDesignStatus.FAILED)) {
            if (!failureInfoSupport.canRetry(overview, now)) {
                continue;
            }
            failureInfoSupport.markRetrying(overview, TriggerSource.SCHEDULED_COMPENSATION, now);
            overviewDesignDao.save(overview);
            compensationLogService.record("OVERVIEW_DESIGN", overview.getId(), "RETRY_OVERVIEW", true,
                    "补偿重试概览设计", overview.getFailureSummary(), TriggerSource.SCHEDULED_COMPENSATION);
            overviewDesignService.regenerate(overview.getId(), retryHint(overview.getFailureSummary()));
        }
    }

    /**
     * 4. 概览设计已确认但缺失详细设计：按 content 中的 feature 列表补建详细设计。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateMissingDetailedDesigns(Date now) {
        for (OverviewDesign overview : overviewDesignDao.findByStatus(OverviewDesignStatus.CONFIRMED)) {
            List<DetailedDesign> existing = detailedDesignDao.findByOverviewDesignId(overview.getId());
            if (existing == null || existing.isEmpty()) {
                compensationLogService.record("OVERVIEW_DESIGN", overview.getId(), "FILL_DETAILED_DESIGNS", true,
                        "补齐全部详细设计", null, TriggerSource.CHAIN_COMPENSATION);
                detailedDesignService.createFromOverviewDesign(overview);
                continue;
            }
            long confirmedCount = existing.stream()
                    .filter(d -> d.getStatus() == DetailedDesignStatus.CONFIRMED)
                    .count();
            if (confirmedCount == existing.size()) {
                // 全部已确认，不再补充；若上游已变应由人工触发重生成
                continue;
            }
            compensationLogService.record("OVERVIEW_DESIGN", overview.getId(), "FILL_MISSING_DETAILED_DESIGNS", true,
                    "补齐缺失详细设计", null, TriggerSource.CHAIN_COMPENSATION);
            detailedDesignService.createMissingFromOverviewDesign(overview);
        }
    }

    /**
     * 5. 详细设计生成失败：满足重试窗口后重生成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateFailedDetailedDesigns(Date now) {
        for (DetailedDesign design : detailedDesignDao.findByStatus(DetailedDesignStatus.FAILED)) {
            if (!failureInfoSupport.canRetry(design, now)) {
                continue;
            }
            failureInfoSupport.markRetrying(design, TriggerSource.SCHEDULED_COMPENSATION, now);
            detailedDesignDao.save(design);
            compensationLogService.record("DETAILED_DESIGN", design.getId(), "RETRY_DETAILED", true,
                    "补偿重试详细设计", design.getFailureSummary(), TriggerSource.SCHEDULED_COMPENSATION);
            detailedDesignService.regenerate(design.getId(), retryHint(design.getFailureSummary()));
        }
    }

    /**
     * 6. 详细设计已确认但缺失编码任务：创建 CodingTask。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateMissingCodingTasks(Date now) {
        for (DetailedDesign design : detailedDesignDao.findByStatus(DetailedDesignStatus.CONFIRMED)) {
            List<CodingTask> tasks = codingTaskDao.findByDetailedDesignId(design.getId());
            if (tasks != null && !tasks.isEmpty()) {
                continue;
            }
            compensationLogService.record("DETAILED_DESIGN", design.getId(), "FILL_MISSING_CODING_TASK", true,
                    "补齐编码任务", null, TriggerSource.CHAIN_COMPENSATION);
            codingTaskService.createFromDetailedDesign(design);
        }
    }

    /**
     * 7. 编码任务失败：满足重试窗口后重跑。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateFailedCodingTasks(Date now) {
        for (CodingTask task : codingTaskDao.findByStatus(CodingTaskStatus.FAILED)) {
            if (!failureInfoSupport.canRetry(task, now)) {
                continue;
            }
            failureInfoSupport.markRetrying(task, TriggerSource.SCHEDULED_COMPENSATION, now);
            codingTaskDao.save(task);
            compensationLogService.record("CODING_TASK", task.getId(), "RETRY_CODING_TASK", true,
                    "补偿重试编码任务", task.getFailureSummary(), TriggerSource.SCHEDULED_COMPENSATION);
            codingTaskService.rerun(task.getId(), retryHint(task.getFailureSummary()));
        }
    }

    /**
     * 8. 运行中超时收口：将超时的 Run 标记为 FAILED，并同步更新关联 CodingTask。
     * 不在同一轮直接重跑，由下一轮 {@link #compensateFailedCodingTasks} 处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public void timeoutRunningRuns(Date now) {
        Date cutoff = new Date(now.getTime() - runTimeoutMinutes * 60_000L);
        for (Run run : runDao.findByState(RunState.RUNNING)) {
            Date started = run.getStartedDate();
            if (started == null || !started.before(cutoff)) {
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
                    task.setStatus(CodingTaskStatus.FAILED);
                    failureInfoSupport.markCodingTaskFailure(task, "编码执行超时",
                            "RUNNING 超过超时时间未收口", TriggerSource.SCHEDULED_COMPENSATION, now);
                    codingTaskDao.save(task);
                }
            }
            compensationLogService.record("RUN", run.getId(), "TIMEOUT_RUN", true,
                    "运行超时收口", "timeout", TriggerSource.SCHEDULED_COMPENSATION);
        }
    }

    private String retryHint(String summary) {
        if (summary == null || summary.isBlank()) {
            return "补偿重试";
        }
        return "补偿重试，最近失败原因：" + summary;
    }
}
