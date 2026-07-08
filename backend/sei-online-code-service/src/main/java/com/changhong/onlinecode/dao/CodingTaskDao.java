package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * CodingTask DAO。
 *
 * @author sei-online-code
 */
@Repository
public interface CodingTaskDao extends BaseEntityDao<CodingTask> {

    /**
     * 按需求 ID 查询所有编码任务。
     *
     * @param requirementId 需求 ID
     * @return 编码任务列表
     */
    List<CodingTask> findByRequirementId(String requirementId);

    /**
     * 按详细设计 ID 查询所有编码任务。
     *
     * @param detailedDesignId 详细设计 ID
     * @return 编码任务列表
     */
    List<CodingTask> findByDetailedDesignId(String detailedDesignId);

    /**
     * 按状态查询编码任务。
     *
     * @param status 状态
     * @return 编码任务列表
     */
    List<CodingTask> findByStatus(com.changhong.onlinecode.dto.enums.CodingTaskStatus status);
}
