package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.DetailedDesign;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * DetailedDesign DAO。
 *
 * @author sei-online-code
 */
@Repository
public interface DetailedDesignDao extends BaseEntityDao<DetailedDesign> {

    /**
     * 按概览设计 ID 查询所有详细设计。
     *
     * @param overviewDesignId 概览设计 ID
     * @return 详细设计列表
     */
    List<DetailedDesign> findByOverviewDesignId(String overviewDesignId);

    /**
     * 按状态查询详细设计。
     *
     * @param status 状态
     * @return 详细设计列表
     */
    List<DetailedDesign> findByStatus(com.changhong.onlinecode.dto.enums.DetailedDesignStatus status);

    /**
     * 仅当详细设计仍处于期望状态时切换状态，用于补偿抢占。
     *
     * @param id 详细设计 ID
     * @param expected 期望旧状态
     * @param target 目标状态
     * @return 更新条数
     */
    @Modifying
    @Query("UPDATE DetailedDesign d SET d.status = :target, d.lastEditedDate = CURRENT_TIMESTAMP "
            + "WHERE d.id = :id AND d.status = :expected")
    int updateStatusIfMatch(@Param("id") String id,
                            @Param("expected") com.changhong.onlinecode.dto.enums.DetailedDesignStatus expected,
                            @Param("target") com.changhong.onlinecode.dto.enums.DetailedDesignStatus target);
}
