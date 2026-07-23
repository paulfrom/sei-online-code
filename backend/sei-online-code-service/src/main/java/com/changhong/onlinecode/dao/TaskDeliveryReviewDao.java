package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewStatus;
import com.changhong.onlinecode.entity.TaskDeliveryReview;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 任务交付审阅 DAO。
 *
 * @author sei-online-code
 */
@Repository
public interface TaskDeliveryReviewDao extends BaseEntityDao<TaskDeliveryReview> {

    /** 幂等查询：同一 (codingTaskId, deliveryRunId) 的审阅记录。 */
    Optional<TaskDeliveryReview> findByCodingTaskIdAndDeliveryRunId(String codingTaskId, String deliveryRunId);

    /** 某需求下处于 PENDING 或 REVIEWING（未决）的审阅记录，用于调度门禁（方案 §6.1）。 */
    @Query("SELECT r FROM TaskDeliveryReview r "
            + "WHERE r.requirementId = :requirementId "
            + "AND r.status IN :openStatuses")
    List<TaskDeliveryReview> findOpenByRequirement(
            @Param("requirementId") String requirementId,
            @Param("openStatuses") List<TaskDeliveryReviewStatus> openStatuses);

    @Query("SELECT r FROM TaskDeliveryReview r "
            + "WHERE r.requirementId = :requirementId AND r.loopId = :loopId "
            + "AND r.status IN :openStatuses")
    List<TaskDeliveryReview> findOpenByRequirementAndLoop(
            @Param("requirementId") String requirementId,
            @Param("loopId") String loopId,
            @Param("openStatuses") List<TaskDeliveryReviewStatus> openStatuses);

    /** 某 codingTaskId 下最新一条审阅记录，用于依赖满足条件（方案 §6.3）。 */
    Optional<TaskDeliveryReview> findFirstByCodingTaskIdOrderByCreatedDateDesc(String codingTaskId);

    /** 某 codingTaskId 下是否有决策为 APPROVE 的审阅记录。 */
    boolean existsByCodingTaskIdAndStatusAndDecision(
            String codingTaskId, TaskDeliveryReviewStatus status,
            com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision decision);

    /**
     * 仅当审阅仍处于期望状态时切换状态，用于审阅声明抢占。
     *
     * @return 更新条数
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE TaskDeliveryReview r SET r.status = :target, r.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE r.id = :id AND r.status = :expected")
    int updateStatusIfMatch(@Param("id") String id,
                            @Param("expected") TaskDeliveryReviewStatus expected,
                            @Param("target") TaskDeliveryReviewStatus target);
}
