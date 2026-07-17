package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.RequirementWorkspace;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * RequirementWorkspace DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * <p>唯一键并发 insert 冲突时，调用方捕获约束异常后用 findByProjectIdAndRequirementId 回读唯一记录。</p>
 *
 * @author sei-online-code
 */
@Repository
public interface RequirementWorkspaceDao extends BaseEntityDao<RequirementWorkspace> {

    /**
     * 按项目 + 需求唯一查找（uk_req_ws_project_requirement）。
     *
     * @param projectId   项目 ID
     * @param requirementId 需求 ID
     * @return 工作区（存在时）
     */
    Optional<RequirementWorkspace> findByProjectIdAndRequirementId(String projectId, String requirementId);

    /**
     * 按项目 + 分支名唯一查找（uk_req_ws_project_branch）。
     *
     * @param projectId  项目 ID
     * @param branchName 分支名
     * @return 工作区（存在时）
     */
    Optional<RequirementWorkspace> findByProjectIdAndBranchName(String projectId, String branchName);
}
