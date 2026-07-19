package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.dto.enums.ExecutionEffectStatus;
import com.changhong.onlinecode.entity.ExecutionEffect;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ExecutionEffect DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * <p>effect_key 唯一：相同 key+requestHash 返回首次结果；key 相同 hash 不同稳定冲突，不覆盖原请求。</p>
 *
 * @author sei-online-code
 */
@Repository
public interface ExecutionEffectDao extends BaseEntityDao<ExecutionEffect> {

    /**
     * 按 effect_key 唯一查找。
     *
     * @param effectKey effect 键
     * @return effect（存在时）
     */
    Optional<ExecutionEffect> findByEffectKey(String effectKey);

    /**
     * 某 Execution 某状态下的 effect 列表。
     *
     * @param executionId Execution ID
     * @param status      effect 状态
     * @return effect 列表
     */
    List<ExecutionEffect> findByExecutionIdAndStatus(String executionId, ExecutionEffectStatus status);

    /**
     * 统计某 Execution 下尚未确认的 effect。Execution 成功收口前必须为 0。
     */
    long countByExecutionIdAndStatusIn(String executionId, List<ExecutionEffectStatus> statuses);

    /**
     * 某 Execution 的 effect 分页（按 preparedAt 倒序）。
     */
    Page<ExecutionEffect> findByExecutionIdOrderByPreparedAtDesc(String executionId, Pageable pageable);

    /**
     * 某 Execution 某状态的 effect 分页（按 preparedAt 倒序）。
     */
    Page<ExecutionEffect> findByExecutionIdAndStatusOrderByPreparedAtDesc(String executionId,
                                                                          ExecutionEffectStatus status,
                                                                          Pageable pageable);

    /**
     * CAS 推进 PREPARED→APPLIED（ADR-001 §5 / EXE-006）。写入 result/externalReference。
     * 返回 0 表示非 PREPARED 状态或 version 冲突。
     */
    @Modifying
    @Query("UPDATE ExecutionEffect e SET e.status = :applied, e.resultSnapshot = :resultSnapshot, "
            + "e.externalReference = :externalReference, e.appliedAt = CURRENT_TIMESTAMP, "
            + "e.version = e.version + 1, e.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE e.id = :effectId AND e.status = :prepared")
    int applyEffect(@Param("effectId") String effectId,
                    @Param("applied") ExecutionEffectStatus applied,
                    @Param("prepared") ExecutionEffectStatus prepared,
                    @Param("resultSnapshot") String resultSnapshot,
                    @Param("externalReference") String externalReference);

    /**
     * CAS 推进 APPLIED→CONFIRMED（ADR-001 §5）。已 CONFIRMED 时幂等返回 0（由服务层处理）。
     */
    @Modifying
    @Query("UPDATE ExecutionEffect e SET e.status = :confirmed, e.confirmedAt = CURRENT_TIMESTAMP, "
            + "e.version = e.version + 1, e.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE e.id = :effectId AND e.status = :applied")
    int confirmEffect(@Param("effectId") String effectId,
                      @Param("confirmed") ExecutionEffectStatus confirmed,
                      @Param("applied") ExecutionEffectStatus applied);

    /**
     * 对账确认 UNKNOWN→CONFIRMED。外部查询已证明副作用存在时使用，不重新执行副作用。
     */
    @Modifying
    @Query("UPDATE ExecutionEffect e SET e.status = :confirmed, e.confirmedAt = CURRENT_TIMESTAMP, "
            + "e.lastReconciledAt = CURRENT_TIMESTAMP, e.version = e.version + 1, "
            + "e.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE e.id = :effectId AND e.status = :unknown")
    int confirmUnknownEffect(@Param("effectId") String effectId,
                             @Param("confirmed") ExecutionEffectStatus confirmed,
                             @Param("unknown") ExecutionEffectStatus unknown);

    /**
     * CAS 推进 APPLIED→UNKNOWN（ADR-001 §5）。结果不确定，需对账。
     */
    @Modifying
    @Query("UPDATE ExecutionEffect e SET e.status = :unknown, e.lastReconciledAt = CURRENT_TIMESTAMP, "
            + "e.version = e.version + 1, e.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE e.id = :effectId AND e.status = :applied")
    int markEffectUnknown(@Param("effectId") String effectId,
                          @Param("unknown") ExecutionEffectStatus unknown,
                          @Param("applied") ExecutionEffectStatus applied);
}
