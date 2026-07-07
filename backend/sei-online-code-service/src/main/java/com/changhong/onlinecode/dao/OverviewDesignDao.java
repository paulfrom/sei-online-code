package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.OverviewDesign;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

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
}
