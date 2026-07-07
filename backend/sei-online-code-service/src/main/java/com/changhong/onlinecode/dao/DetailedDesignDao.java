package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.DetailedDesign;
import com.changhong.sei.core.dao.BaseEntityDao;
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
}
