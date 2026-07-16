package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Requirement;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
