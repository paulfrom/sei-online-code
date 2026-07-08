package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Requirement;
import com.changhong.sei.core.dao.BaseEntityDao;
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
     * 按状态查询需求。
     *
     * @param status 状态
     * @return 需求列表
     */
    List<Requirement> findByStatus(com.changhong.onlinecode.dto.enums.RequirementStatus status);
}
