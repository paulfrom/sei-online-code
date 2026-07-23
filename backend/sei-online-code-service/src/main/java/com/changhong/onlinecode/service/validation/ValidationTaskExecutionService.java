package com.changhong.onlinecode.service.validation;

import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.CodingTaskStatus;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Requirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 验证任务异步执行服务（方案 §7 三阶段事务拆分）。
 *
 * <p>把 test-agent 验证从"调度事务内同步等待"拆成三段，解决 §3.4：</p>
 * <ol>
 *   <li>{@code claim}：调度器事务内 PENDING/候选 -> VALIDATING 并提交，其他事务可查询到 VALIDATING；</li>
 *   <li>{@code execute}：事务外通过 {@code validationAgentExecutor} 有界线程池运行 test-agent，
 *       不占用 scheduler 数据库事务；</li>
 *   <li>{@code finish}：新短事务保存 Run、CodingTask 终态、VALIDATION_RESULT 评论与交付审阅，
 *       提交后发布审阅事件与调度事件。</li>
 * </ol>
 *
 * <p>调度器在 {@code doSchedule} 中先 claim（提交），再调用 {@link #executeAsync} 触发事务外执行。
 * 本类不直接持有数据库事务执行 agent future（方案 §7 硬约束）。</p>
 *
 * @author sei-online-code
 */
@Service
@Slf4j
public class ValidationTaskExecutionService {

    private final CodingTaskDao codingTaskDao;
    private final RequirementDao requirementDao;
    private final ValidationLoopService validationLoopService;
    private final ValidationTaskSettlementService settlementService;
    private final Executor validationAgentExecutor;

    public ValidationTaskExecutionService(CodingTaskDao codingTaskDao,
                                          RequirementDao requirementDao,
                                          ValidationLoopService validationLoopService,
                                          ValidationTaskSettlementService settlementService,
                                          @Qualifier("validationAgentExecutor") Executor validationAgentExecutor) {
        this.codingTaskDao = codingTaskDao;
        this.requirementDao = requirementDao;
        this.validationLoopService = validationLoopService;
        this.settlementService = settlementService;
        this.validationAgentExecutor = validationAgentExecutor;
    }

    /**
     * claim 阶段：把任务置为 VALIDATING 并提交。
     *
     * <p>由调度器在其事务内调用。返回 false 表示任务已被其他轮次抢占或 loop 变更，调用方应跳过。</p>
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.MANDATORY)
    public boolean claim(CodingTask task) {
        CodingTask persisted = codingTaskDao.findOne(task.getId());
        if (persisted == null) {
            return false;
        }
        if (persisted.getStatus() != CodingTaskStatus.PENDING) {
            return false;
        }
        Requirement requirement = requirementDao.findOne(persisted.getRequirementId());
        if (requirement == null || !Objects.equals(persisted.getLoopId(), requirement.getActiveLoopId())) {
            return false;
        }
        persisted.setStatus(CodingTaskStatus.VALIDATING);
        codingTaskDao.save(persisted);
        return true;
    }

    /**
     * execute 阶段：在调度器事务提交后，提交到有界线程池运行 test-agent，
     * 完成后调用 finish 阶段（新事务）。
     *
     * <p>必须在调度器事务提交后调用，保证 VALIDATING 状态已可见。</p>
     */
    public void executeAsync(String codingTaskId) {
        try {
            validationAgentExecutor.execute(() -> {
                try {
                    runValidationAndFinish(codingTaskId);
                } catch (Exception e) {
                    log.error("validation task async execution failed. codingTaskId={}", codingTaskId, e);
                    settlementService.finishOnFailure(codingTaskId,
                            "test-agent 异步执行异常：" + e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            settlementService.defer(codingTaskId, "validation agent executor queue is full");
        }
    }

    /**
     * 事务外运行 test-agent，随后在新事务中结算（finish）。
     */
    private void runValidationAndFinish(String codingTaskId) {
        CodingTask task = codingTaskDao.findOne(codingTaskId);
        if (task == null) {
            log.warn("validation task skipped: not found. codingTaskId={}", codingTaskId);
            return;
        }
        if (task.getStatus() != CodingTaskStatus.VALIDATING) {
            log.info("validation task skipped: not VALIDATING (already settled?). codingTaskId={}, status={}",
                    codingTaskId, task.getStatus());
            return;
        }
        // loop 校验：调度器 claim 后 loop 可能被人类评论中断。
        Requirement requirement = requirementDao.findOne(task.getRequirementId());
        if (requirement == null || !Objects.equals(task.getLoopId(), requirement.getActiveLoopId())) {
            log.info("validation task skipped: loop changed. codingTaskId={}", codingTaskId);
            settlementService.markStale(codingTaskId);
            return;
        }

        // 事务外运行 test-agent（最长 1800s），不占用任何数据库事务。
        ValidationLoopService.ValidationOutcome outcome;
        try {
            outcome = validationLoopService.validateTask(task);
        } catch (Exception e) {
            log.error("test-agent validation threw. codingTaskId={}", codingTaskId, e);
            settlementService.finishOnFailure(codingTaskId, "test-agent 执行异常：" + e.getMessage());
            return;
        }
        boolean passed = outcome != null && outcome.passed();
        settlementService.finish(codingTaskId, passed);
    }
}
