package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.RequirementWorkspace;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;

/**
 * RequirementWorkspace DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * <p>唯一键并发 insert 冲突时，调用方捕获约束异常后用 findByProjectIdAndRequirementId 回读唯一记录。</p>
 *
 * @author sei-online-code
 */
@Repository
public interface RequirementWorkspaceDao extends BaseEntityDao<RequirementWorkspace> {

    /**
     * 按项目 + 需求唯一查找（uk_req_ws_project_requirement）。
     *
     * @param projectId   项目 ID
     * @param requirementId 需求 ID
     * @return 工作区（存在时）
     */
    Optional<RequirementWorkspace> findByProjectIdAndRequirementId(String projectId, String requirementId);

    /**
     * 按项目 + 分支名唯一查找（uk_req_ws_project_branch）。
     *
     * @param projectId  项目 ID
     * @param branchName 分支名
     * @return 工作区（存在时）
     */
    Optional<RequirementWorkspace> findByProjectIdAndBranchName(String projectId, String branchName);

    /**
     * 获取 Requirement 写 lease（ADR-001 §10.2）。CAS：当前 owner 为本 Run、或无 owner、或 lease 已过期时才成功，
     * 成功时递增 fencing_token。返回更新条数（1=获得，0=被他人持有）。
     *
     * @param workspaceId    工作区 ID
     * @param runId          申请 owner Run ID
     * @param executionId    关联 Execution ID
     * @param leaseExpiresAt 新的过期时间
     * @return 更新条数
     */
    @Modifying
    @Query("UPDATE RequirementWorkspace w SET w.ownerRunId = :runId, "
            + "w.ownerExecutionId = :executionId, "
            + "w.leaseExpiresAt = :leaseExpiresAt, "
            + "w.fencingToken = w.fencingToken + 1, "
            + "w.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE w.id = :workspaceId "
            + "AND (w.ownerRunId = :runId OR w.ownerRunId IS NULL "
            + "OR w.leaseExpiresAt IS NULL OR w.leaseExpiresAt < CURRENT_TIMESTAMP)")
    int acquireLease(@Param("workspaceId") String workspaceId,
                     @Param("runId") String runId,
                     @Param("executionId") String executionId,
                     @Param("leaseExpiresAt") Date leaseExpiresAt);

    /**
     * 递增 snapshotVersion（ADR-001 §10.6）。任一可观测进度提交时在同一事务内调用。
     *
     * @param workspaceId 工作区 ID
     * @return 更新条数
     */
    @Modifying
    @Query("UPDATE RequirementWorkspace w SET w.snapshotVersion = w.snapshotVersion + 1, "
            + "w.lastEditedDate = CURRENT_TIMESTAMP WHERE w.id = :workspaceId")
    int incrementSnapshotVersion(@Param("workspaceId") String workspaceId);
}
