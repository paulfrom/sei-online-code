package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * RequirementComment DAO。
 */
@Repository
public interface RequirementCommentDao extends BaseEntityDao<RequirementComment> {

    List<RequirementComment> findByRequirementIdOrderByCreatedDateAsc(String requirementId);

    List<RequirementComment> findByRequirementIdAndLoopIdOrderByCreatedDateAsc(String requirementId, String loopId);
}
