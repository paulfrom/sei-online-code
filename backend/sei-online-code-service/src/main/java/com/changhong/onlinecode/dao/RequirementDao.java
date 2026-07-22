package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.dto.enums.RequirementRevisionState;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Requirement DAO。
 *
 * @author sei-online-code
 */
@Repository
public interface RequirementDao extends BaseEntityDao<Requirement> {

    /**
     * 按项目 ID 查询所有需求。
     *
     * @param projectId 项目 ID
     * @return 需求列表
     */
    List<Requirement> findByProjectId(String projectId);

    /**
     * 按项目 ID 查询需求，需求编号生成时用于取项目内最大序号。
     *
     * @param projectId 项目 ID
     * @return 需求列表
     */
    List<Requirement> findByProjectIdOrderByCreatedDateDesc(String projectId);

    /**
     * 按状态查询需求。
     *
     * @param status 状态
     * @return 需求列表
     */
    List<Requirement> findByStatus(com.changhong.onlinecode.dto.enums.RequirementStatus status);

    /**
     * 仅当需求仍处于期望状态时切换状态，用于补偿抢占。
     *
     * @param id 需求 ID
     * @param expected 期望旧状态
     * @param target 目标状态
     * @return 更新条数
     */
    @Modifying
    @Query("UPDATE Requirement r SET r.status = :target, r.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE r.id = :id AND r.status = :expected")
    int updateStatusIfMatch(@Param("id") String id,
                            @Param("expected") com.changhong.onlinecode.dto.enums.RequirementStatus expected,
                            @Param("target") com.changhong.onlinecode.dto.enums.RequirementStatus target);

    /**
     * Atomically claims and completes one plan revision. The surrounding transaction also
     * persists the new plan/tasks, so a later failure rolls this update back with them.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Requirement r SET r.appliedRevisionSeq = :revisionSeq, "
            + "r.revisionState = :targetState, r.revisionFailureReason = NULL, "
            + "r.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE r.id = :id AND r.activeLoopId = :loopId "
            + "AND r.revisionSeq = :revisionSeq AND r.appliedRevisionSeq < :revisionSeq "
            + "AND r.revisionState = :expectedState")
    int applyRevisionIfCurrent(@Param("id") String id,
                               @Param("loopId") String loopId,
                               @Param("revisionSeq") Long revisionSeq,
                               @Param("expectedState") RequirementRevisionState expectedState,
                               @Param("targetState") RequirementRevisionState targetState);

    /** Starts a new revision without replacing the active automation loop. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Requirement r SET r.revisionSeq = r.revisionSeq + 1, "
            + "r.revisionState = :state, r.revisionTriggerCommentId = :commentId, "
            + "r.revisionFailureReason = NULL, r.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE r.id = :id AND r.activeLoopId = :loopId")
    int requestRevision(@Param("id") String id,
                        @Param("loopId") String loopId,
                        @Param("commentId") String commentId,
                        @Param("state") RequirementRevisionState state);

    /** Compare-and-set transition used by the asynchronous revision state machine. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Requirement r SET r.revisionState = :target, "
            + "r.revisionFailureReason = NULL, r.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE r.id = :id AND r.activeLoopId = :loopId AND r.revisionSeq = :revisionSeq "
            + "AND r.revisionState = :expected")
    int transitionRevisionState(@Param("id") String id,
                                @Param("loopId") String loopId,
                                @Param("revisionSeq") Long revisionSeq,
                                @Param("expected") RequirementRevisionState expected,
                                @Param("target") RequirementRevisionState target);

    /** Records failure only if the worker still owns the latest revision token. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Requirement r SET r.revisionState = :failed, "
            + "r.revisionFailureReason = :reason, r.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE r.id = :id AND r.activeLoopId = :loopId AND r.revisionSeq = :revisionSeq "
            + "AND r.revisionState IN :expectedStates "
            + "AND (r.appliedRevisionSeq IS NULL OR r.appliedRevisionSeq < :revisionSeq)")
    int failRevisionIfCurrent(@Param("id") String id,
                              @Param("loopId") String loopId,
                              @Param("revisionSeq") Long revisionSeq,
                              @Param("expectedStates") Collection<RequirementRevisionState> expectedStates,
                              @Param("reason") String reason,
                              @Param("failed") RequirementRevisionState failed);
}
