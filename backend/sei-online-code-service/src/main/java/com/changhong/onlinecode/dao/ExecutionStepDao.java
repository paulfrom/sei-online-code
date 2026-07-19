package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.dto.enums.ExecutionStepStatus;
import com.changhong.onlinecode.entity.ExecutionStep;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * ExecutionStep DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * <p>同 Execution 计划版本内 (executionId, stepKey, planVersion) 唯一；冲突后用唯一查找回读。
 * 状态迁移通过 CAS {@code @Modifying} 查询实现，调用方事务内执行。</p>
 *
 * @author sei-online-code
 */
@Repository
public interface ExecutionStepDao extends BaseEntityDao<ExecutionStep> {

    /**
     * 按 Execution + stepKey + planVersion 唯一查找。
     */
    Optional<ExecutionStep> findByExecutionIdAndStepKeyAndPlanVersion(String executionId, String stepKey, Integer planVersion);

    /**
     * 某 Execution 某计划版本下的全部步骤。
     */
    List<ExecutionStep> findByExecutionIdAndPlanVersion(String executionId, Integer planVersion);

    /**
     * Claim 步骤（ADR-001 §10.3）。CAS：stepId + 期望 version + 状态在可 claim 集合内才成功。
     * 成功时生成新 owner/claimToken、捕获 fencingToken、递增 attempt_count/version、首次 claim 记录 startedAt。
     * 返回更新条数（1=claim 成功，0=版本冲突或状态不可 claim）。
     */
    @Modifying
    @Query("UPDATE ExecutionStep s SET s.status = :inProgress, "
            + "s.ownerRunId = :runId, s.claimToken = :claimToken, "
            + "s.workspaceFencingToken = :fencingToken, s.leaseExpiresAt = :leaseExpiresAt, "
            + "s.attemptCount = s.attemptCount + 1, s.version = s.version + 1, "
            + "s.startedAt = CASE WHEN s.startedAt IS NULL THEN CURRENT_TIMESTAMP ELSE s.startedAt END, "
            + "s.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE s.id = :stepId AND s.version = :expectedVersion "
            + "AND s.status IN :claimableStatuses")
    int claimStep(@Param("stepId") String stepId,
                  @Param("expectedVersion") Long expectedVersion,
                  @Param("claimableStatuses") Collection<ExecutionStepStatus> claimableStatuses,
                  @Param("inProgress") ExecutionStepStatus inProgress,
                  @Param("runId") String runId,
                  @Param("claimToken") String claimToken,
                  @Param("fencingToken") Long fencingToken,
                  @Param("leaseExpiresAt") Date leaseExpiresAt);

    /**
     * 心跳：仅当 owner_run_id/claim_token 匹配且仍 IN_PROGRESS 时续 lease。返回 0 表示 stale owner。
     */
    @Modifying
    @Query("UPDATE ExecutionStep s SET s.lastHeartbeatAt = CURRENT_TIMESTAMP, "
            + "s.leaseExpiresAt = :leaseExpiresAt, s.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE s.id = :stepId AND s.ownerRunId = :runId AND s.claimToken = :claimToken "
            + "AND s.status = :inProgress")
    int heartbeat(@Param("stepId") String stepId,
                  @Param("runId") String runId,
                  @Param("claimToken") String claimToken,
                  @Param("leaseExpiresAt") Date leaseExpiresAt,
                  @Param("inProgress") ExecutionStepStatus inProgress);

    /**
     * 标记 APPLIED：仅 IN_PROGRESS/UNKNOWN 且 owner 匹配时可推进。返回 0 表示 stale owner 或非法状态。
     */
    @Modifying
    @Query("UPDATE ExecutionStep s SET s.status = :applied, s.appliedAt = CURRENT_TIMESTAMP, "
            + "s.version = s.version + 1, s.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE s.id = :stepId AND s.ownerRunId = :runId AND s.claimToken = :claimToken "
            + "AND s.status IN (:inProgress, :unknown)")
    int markApplied(@Param("stepId") String stepId,
                    @Param("runId") String runId,
                    @Param("claimToken") String claimToken,
                    @Param("applied") ExecutionStepStatus applied,
                    @Param("inProgress") ExecutionStepStatus inProgress,
                    @Param("unknown") ExecutionStepStatus unknown);

    /**
     * 标记 VERIFIED：仅 APPLIED 且 owner 匹配时可推进（ADR-001 不变量 5：VERIFIED 不可由普通更新回退）。
     * 已 VERIFIED 时 CAS 自然返回 0，由服务层判定为幂等成功而非回退。
     */
    @Modifying
    @Query("UPDATE ExecutionStep s SET s.status = :verified, s.completedAt = CURRENT_TIMESTAMP, "
            + "s.version = s.version + 1, s.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE s.id = :stepId AND s.ownerRunId = :runId AND s.claimToken = :claimToken "
            + "AND s.status = :applied")
    int markVerified(@Param("stepId") String stepId,
                     @Param("runId") String runId,
                     @Param("claimToken") String claimToken,
                     @Param("verified") ExecutionStepStatus verified,
                     @Param("applied") ExecutionStepStatus applied);

    /**
     * 更新步骤最新 checkpoint 镜像；仅 owner 匹配时成功。返回 0 触发调用方回滚（关键 checkpoint 原子性）。
     */
    @Modifying
    @Query("UPDATE ExecutionStep s SET s.latestCheckpointId = :checkpointId, "
            + "s.checkpointData = :checkpointData, s.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE s.id = :stepId AND s.ownerRunId = :runId AND s.claimToken = :claimToken")
    int updateLatestCheckpoint(@Param("stepId") String stepId,
                               @Param("runId") String runId,
                               @Param("claimToken") String claimToken,
                               @Param("checkpointId") String checkpointId,
                               @Param("checkpointData") String checkpointData);

    /**
     * 标记 UNKNOWN（IN_PROGRESS→UNKNOWN）：结果不确定，需对账后再续作（ADR-001 §2 reconcile 入口）。
     * 仅 owner 匹配时成功；返回 0 表示 stale owner 或非 IN_PROGRESS。
     */
    @Modifying
    @Query("UPDATE ExecutionStep s SET s.status = :unknown, s.version = s.version + 1, "
            + "s.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE s.id = :stepId AND s.ownerRunId = :runId AND s.claimToken = :claimToken "
            + "AND s.status = :inProgress")
    int markUnknown(@Param("stepId") String stepId,
                    @Param("runId") String runId,
                    @Param("claimToken") String claimToken,
                    @Param("unknown") ExecutionStepStatus unknown,
                    @Param("inProgress") ExecutionStepStatus inProgress);

    /**
     * 标记 BLOCKED：owner 匹配且当前处于可阻塞状态时写入阻塞证据。
     */
    @Modifying
    @Query("UPDATE ExecutionStep s SET s.status = :blocked, s.evidenceData = :evidenceData, "
            + "s.version = s.version + 1, s.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE s.id = :stepId AND s.ownerRunId = :runId AND s.claimToken = :claimToken "
            + "AND s.status IN (:inProgress, :applied, :unknown)")
    int markBlocked(@Param("stepId") String stepId,
                    @Param("runId") String runId,
                    @Param("claimToken") String claimToken,
                    @Param("blocked") ExecutionStepStatus blocked,
                    @Param("inProgress") ExecutionStepStatus inProgress,
                    @Param("applied") ExecutionStepStatus applied,
                    @Param("unknown") ExecutionStepStatus unknown,
                    @Param("evidenceData") String evidenceData);

    /**
     * 自动恢复可重试 BLOCKED step：清理 owner/claim/lease 并回到 PENDING，等待新 Run claim。
     */
    @Modifying
    @Query("UPDATE ExecutionStep s SET s.status = :pending, s.ownerRunId = NULL, s.claimToken = NULL, "
            + "s.workspaceFencingToken = NULL, s.leaseExpiresAt = NULL, s.evidenceData = :evidenceData, "
            + "s.version = s.version + 1, s.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE s.id = :stepId AND s.status = :blocked")
    int unblockForRetry(@Param("stepId") String stepId,
                        @Param("blocked") ExecutionStepStatus blocked,
                        @Param("pending") ExecutionStepStatus pending,
                        @Param("evidenceData") String evidenceData);
}
