package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.MemoryJobDao;
import com.changhong.onlinecode.dto.enums.MemoryJobStatus;
import com.changhong.onlinecode.dto.enums.MemoryJobTriggerSource;
import com.changhong.onlinecode.dto.enums.MemoryJobType;
import com.changhong.onlinecode.entity.MemoryJob;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 记忆任务服务。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.4、§10.6、§12。
 *
 * <p>职责：投递 memory job（基于 {@code idempotency_key} 防重复）；按 {@code projectId} 串行执行（第一版不实现
 * 取消）；处理 retry（maxRetry=3，delay 1min/5min/15min）；记录失败；暴露 job 查询与重试能力。</p>
 *
 * <p>第一版仅提供投递、查询、重试、状态流转 API 与执行抢占原语；具体 job 执行器
 * 在 Phase 2/4 接入扫描器与回写后实现。</p>
 *
 * @author sei-online-code
 */
@Service
public class MemoryJobService extends BaseEntityService<MemoryJob> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryJobService.class);

    /** 重试延迟（秒）：1min / 5min / 15min（契约 §12.3）。 */
    private static final long[] RETRY_DELAY_SECONDS = {60L, 300L, 900L};

    /** 默认最大重试次数。 */
    private static final int DEFAULT_MAX_RETRY = 3;

    private final MemoryJobDao dao;

    @PersistenceContext
    private EntityManager entityManager;

    public MemoryJobService(MemoryJobDao dao) {
        this.dao = dao;
    }

    @Override
    protected BaseEntityDao<MemoryJob> getDao() {
        return dao;
    }

    /**
     * 投递 memory job。基于 idempotency_key 防重复：相同 key 已存在则返回既有 job。
     *
     * @param projectId              项目 id
     * @param jobType                任务类型
     * @param triggerSource          触发来源
     * @param idempotencyKey         幂等键（由调用方按 §12.4 规则构造）
     * @param requirementId          关联需求 id（可空）
     * @param codingTaskId           关联 CodingTask id（可空）
     * @param runId                  关联 run id（可空）
     * @param baseWorkspaceMemoryId  增量回写基准 WorkspaceMemory id（可空）
     * @return 投递结果（携带 job；若因幂等命中既有 job 则其失败，成功为新投递 job）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<MemoryJob> submit(String projectId,
                                                   MemoryJobType jobType,
                                                   MemoryJobTriggerSource triggerSource,
                                                   String idempotencyKey,
                                                   String requirementId,
                                                   String codingTaskId,
                                                   String runId,
                                                   String baseWorkspaceMemoryId) {
        MemoryJob existing = dao.findByIdempotencyKey(idempotencyKey);
        if (Objects.nonNull(existing)) {
            return OperateResultWithData.operationSuccessWithData(existing);
        }
        MemoryJob job = new MemoryJob();
        job.setProjectId(projectId);
        job.setJobType(jobType);
        job.setTriggerSource(triggerSource);
        job.setIdempotencyKey(idempotencyKey);
        job.setRequirementId(requirementId);
        job.setCodingTaskId(codingTaskId);
        job.setRunId(runId);
        job.setBaseWorkspaceMemoryId(baseWorkspaceMemoryId);
        job.setStatus(MemoryJobStatus.PENDING);
        job.setRetryCount(0);
        job.setMaxRetryCount(DEFAULT_MAX_RETRY);
        return super.save(job);
    }

    /**
     * 查询项目下全部 job（按创建倒序）。
     *
     * @param projectId 项目 id
     * @return job 列表
     */
    public List<MemoryJob> findByProject(String projectId) {
        return dao.findByProjectIdOrderByCreatedDateDesc(projectId);
    }

    /**
     * 查询项目下 active（PENDING/RUNNING）job，用于串行控制（契约 §12.4）。
     *
     * @param projectId 项目 id
     * @return active job 列表
     */
    public List<MemoryJob> findActiveByProject(String projectId) {
        return dao.findByProjectIdAndStatusIn(projectId,
                List.of(MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING));
    }

    /**
     * 抢占：基于条件 UPDATE 的 CAS 语义，仅当 job 仍处于 expected 状态时切换为 target。
     * 按受影响行数判定是否抢占成功，避免 read-check-write 竞态（两个执行器并发读到 PENDING 都切 RUNNING）（契约 §12.4）。
     *
     * @param jobId    job id
     * @param expected 期望旧状态
     * @param target   目标状态
     * @return 抢占成功返回最新 job；已被他人抢占或状态已变返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryJob tryClaim(String jobId, MemoryJobStatus expected, MemoryJobStatus target) {
        Date startedAt = target == MemoryJobStatus.RUNNING ? new Date() : null;
        int affected = dao.claimIfStatus(jobId, expected, target, startedAt);
        if (affected != 1) {
            return null;
        }
        // 条件更新成功后回读最新状态；flush 保证读到的不是事务内一级缓存旧值
        entityManager.flush();
        return dao.findOne(jobId);
    }

    /**
     * 标记 job 成功，写入 newWorkspaceMemoryId 与完成时间。
     *
     * @param jobId                 job id
     * @param newWorkspaceMemoryId 产出的 WorkspaceMemory id
     */
    @Transactional(rollbackFor = Exception.class)
    public void markSucceeded(String jobId, String newWorkspaceMemoryId) {
        MemoryJob job = dao.findOne(jobId);
        if (Objects.isNull(job)) {
            return;
        }
        job.setStatus(MemoryJobStatus.SUCCEEDED);
        job.setNewWorkspaceMemoryId(newWorkspaceMemoryId);
        job.setFinishedAt(new Date());
        dao.save(job);
    }

    /**
     * 在 platform-memory 镜像写入前记录已经成功创建的 WorkspaceMemory。
     * 若镜像写入失败，重试可直接补写该版本，避免重复创建 CURRENT 版本。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordOutput(String jobId, String newWorkspaceMemoryId) {
        MemoryJob job = dao.findOne(jobId);
        if (job == null) {
            return;
        }
        job.setNewWorkspaceMemoryId(newWorkspaceMemoryId);
        dao.save(job);
    }

    /**
     * 标记 job 失败：若仍可重试则置 PENDING 并按 §12.3 计算 next_retry_at；否则 FAILED。
     *
     * @param jobId         job id
     * @param failureSummary 失败摘要
     * @param failureDetail  失败详情
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String jobId, String failureSummary, String failureDetail) {
        MemoryJob job = dao.findOne(jobId);
        if (Objects.isNull(job)) {
            return;
        }
        job.setFailureSummary(failureSummary);
        job.setFailureDetail(failureDetail);
        int nextRetry = job.getRetryCount() + 1;
        if (nextRetry < Objects.requireNonNullElse(job.getMaxRetryCount(), DEFAULT_MAX_RETRY)) {
            job.setRetryCount(nextRetry);
            job.setStatus(MemoryJobStatus.PENDING);
            long delaySeconds = RETRY_DELAY_SECONDS[Math.min(nextRetry - 1, RETRY_DELAY_SECONDS.length - 1)];
            job.setNextRetryAt(new Date(System.currentTimeMillis() + delaySeconds * 1000L));
            LOGGER.info("memory-job: 失败重试 jobId={}, retryCount={}, nextRetryAt 标记", jobId, nextRetry);
        } else {
            job.setRetryCount(nextRetry);
            job.setStatus(MemoryJobStatus.FAILED);
            job.setFinishedAt(new Date());
            LOGGER.warn("memory-job: 重试耗尽进入 FAILED jobId={}", jobId);
        }
        dao.save(job);
    }

    /**
     * 手动重试：创建新的 job 记录（契约 §12.3 失败 3 次后用户手动重试，创建新 job）。
     *
     * @param jobId 原 FAILED job id
     * @return 新投递的 job
     */
    @Transactional(rollbackFor = Exception.class)
    public OperateResultWithData<MemoryJob> retry(String jobId) {
        MemoryJob origin = dao.findOne(jobId);
        if (Objects.isNull(origin)) {
            return OperateResultWithData.operationFailure("memory job 不存在: " + jobId);
        }
        String idempotencyKey = origin.getIdempotencyKey() + ":retry:" + System.currentTimeMillis();
        return submit(origin.getProjectId(), origin.getJobType(), MemoryJobTriggerSource.MANUAL,
                idempotencyKey, origin.getRequirementId(), origin.getCodingTaskId(),
                origin.getRunId(), origin.getBaseWorkspaceMemoryId());
    }
}
