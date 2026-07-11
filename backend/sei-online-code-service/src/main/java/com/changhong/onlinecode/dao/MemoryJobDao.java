package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.dto.enums.MemoryJobStatus;
import com.changhong.onlinecode.entity.MemoryJob;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * MemoryJob DAO。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.4。
 *
 * @author sei-online-code
 */
@Repository
public interface MemoryJobDao extends BaseEntityDao<MemoryJob> {

    /**
     * 按 projectId 查询全部 job，按创建时间倒序。
     *
     * @param projectId 项目 id
     * @return job 列表
     */
    List<MemoryJob> findByProjectIdOrderByCreatedDateDesc(String projectId);

    /**
     * 查询项目下处于 active（PENDING/RUNNING）的 job。第一版按 projectId 串行执行（契约 §12.4）。
     *
     * @param projectId 项目 id
     * @param pending PENDING 状态
     * @param running RUNNING 状态
     * @return active job 列表
     */
    List<MemoryJob> findByProjectIdAndStatusIn(String projectId, List<MemoryJobStatus> statuses);

    /**
     * 按 idempotencyKey 查询 job。
     *
     * @param idempotencyKey 幂等键
     * @return job；不存在返回 null
     */
    MemoryJob findByIdempotencyKey(String idempotencyKey);

    /**
     * 查询待执行的 job（PENDING 且到达 next_retry_at 或无 next_retry_at），按优先级和创建时间排序（契约 §12）。
     *
     * @param pending PENDING 状态
     * @param now 当前时间（已生效时间点）
     * @return 待执行 job 列表
     */
    @Query("SELECT j FROM MemoryJob j WHERE j.status = :pending AND (j.nextRetryAt IS NULL OR j.nextRetryAt <= :now) "
            + "ORDER BY j.priority ASC, j.createdDate ASC")
    List<MemoryJob> findRunnableJobs(@Param("pending") MemoryJobStatus pending, @Param("now") java.util.Date now);

    /**
     * 条件更新抢占：仅当 job 仍处于 expected 状态时切换为 target，并按需写入 startedAt。
     * 返回受影响行数（1=抢占成功，0=已被他人抢占或状态已变）。CAS 语义，避免 read-check-write 竞态（契约 §12.4）。
     *
     * @param id       job id
     * @param expected 期望旧状态
     * @param target   目标状态
     * @param startedAt 仅当 target=RUNNING 时写入的开始时间，否则传 null
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE MemoryJob j SET j.status = :target, "
            + "j.startedAt = CASE WHEN :target = com.changhong.onlinecode.dto.enums.MemoryJobStatus.RUNNING "
            + "THEN COALESCE(:startedAt, j.startedAt) ELSE j.startedAt END, "
            + "j.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE j.id = :id AND j.status = :expected")
    int claimIfStatus(@Param("id") String id,
                      @Param("expected") MemoryJobStatus expected,
                      @Param("target") MemoryJobStatus target,
                      @Param("startedAt") Date startedAt);
}