package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.dto.enums.MemoryRecordStatus;
import com.changhong.onlinecode.dto.enums.RequirementDesignContextStatus;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * RequirementDesignContext DAO。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.3。
 *
 * @author sei-online-code
 */
@Repository
public interface RequirementDesignContextDao extends BaseEntityDao<RequirementDesignContext> {

    /**
     * 查询需求当前 CURRENT RequirementDesignContext。
     *
     * @param requirementId 需求 id
     * @param status        CURRENT
     * @return 当前上下文；不存在返回 null
     */
    RequirementDesignContext findByRequirementIdAndStatus(String requirementId, MemoryRecordStatus status);

    /**
     * 查询指定状态的当前设计上下文，供补偿任务发现需要重生成 PRD 的需求。
     *
     * @param contextStatus 上下文状态
     * @param status        记录状态
     * @return 匹配的设计上下文
     */
    List<RequirementDesignContext> findByContextStatusAndStatus(
            RequirementDesignContextStatus contextStatus, MemoryRecordStatus status);

    /**
     * 查询需求全部历史版本，按版本倒序。
     *
     * @param requirementId 需求 id
     * @return 全部版本
     */
    List<RequirementDesignContext> findByRequirementIdOrderByVersionDesc(String requirementId);

    /**
     * 归档当前 CURRENT。
     *
     * @param requirementId 需求 id
     * @param current       CURRENT
     * @param archived      ARCHIVED
     * @return 更新条数
     */
    @Modifying
    @Query("UPDATE RequirementDesignContext r SET r.status = :archived, r.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE r.requirementId = :requirementId AND r.status = :current")
    int archiveCurrent(@Param("requirementId") String requirementId,
                       @Param("current") MemoryRecordStatus current,
                       @Param("archived") MemoryRecordStatus archived);
}
