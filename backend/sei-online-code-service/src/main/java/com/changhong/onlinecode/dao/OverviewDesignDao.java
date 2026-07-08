package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.OverviewDesign;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * OverviewDesign DAO。
 *
 * @author sei-online-code
 */
@Repository
public interface OverviewDesignDao extends BaseEntityDao<OverviewDesign> {

    /**
     * 按需求 ID 查询当前概览设计。
     *
     * @param requirementId 需求 ID
     * @return 概览设计
     */
    OverviewDesign findByRequirementId(String requirementId);

    /**
     * 按状态查询概览设计。
     *
     * @param status 状态
     * @return 概览设计列表
     */
    List<OverviewDesign> findByStatus(com.changhong.onlinecode.dto.enums.OverviewDesignStatus status);

    /**
     * 仅当概览设计仍处于期望状态时切换状态，用于补偿抢占。
     *
     * @param id 概览设计 ID
     * @param expected 期望旧状态
     * @param target 目标状态
     * @return 更新条数
     */
    @Modifying
    @Query("UPDATE OverviewDesign o SET o.status = :target, o.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE o.id = :id AND o.status = :expected")
    int updateStatusIfMatch(@Param("id") String id,
                            @Param("expected") com.changhong.onlinecode.dto.enums.OverviewDesignStatus expected,
                            @Param("target") com.changhong.onlinecode.dto.enums.OverviewDesignStatus target);
}
