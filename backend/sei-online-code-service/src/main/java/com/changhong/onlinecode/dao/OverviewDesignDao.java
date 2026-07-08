package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.OverviewDesign;
import com.changhong.sei.core.dao.BaseEntityDao;
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
}
