package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.dto.enums.WorkspaceMemoryStatus;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * WorkspaceMemory DAO。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.2。
 *
 * @author sei-online-code
 */
@Repository
public interface WorkspaceMemoryDao extends BaseEntityDao<WorkspaceMemory> {

    /**
     * 查询项目当前 CURRENT WorkspaceMemory（partial unique index 保证至多一条）。
     *
     * @param projectId 项目 id
     * @param status    CURRENT
     * @return 当前版本；不存在返回 null
     */
    WorkspaceMemory findByProjectIdAndStatus(String projectId, WorkspaceMemoryStatus status);

    /**
     * 查询项目全部历史版本，按版本倒序。
     *
     * @param projectId 项目 id
     * @return 全部版本（含 CURRENT）
     */
    List<WorkspaceMemory> findByProjectIdOrderByVersionDesc(String projectId);

    /**
     * 将指定项目下 CURRENT 版本改为 ARCHIVED（版本切换前的归档步骤，契约 §8.2、§21）。
     * partial unique index uk_workspace_memory_current 保证操作后至多一条 CURRENT。
     *
     * @param projectId 项目 id
     * @param current   CURRENT 状态值
     * @param archived  ARCHIVED 状态值
     * @return 更新条数
     */
    @Query("UPDATE WorkspaceMemory w SET w.status = :archived, w.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE w.projectId = :projectId AND w.status = :current")
    @org.springframework.data.jpa.repository.Modifying
    int archiveCurrent(@Param("projectId") String projectId,
                       @Param("current") WorkspaceMemoryStatus current,
                       @Param("archived") WorkspaceMemoryStatus archived);
}