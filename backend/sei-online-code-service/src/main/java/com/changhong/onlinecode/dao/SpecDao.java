package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Spec;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spec DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * @author sei-online-code
 */
@Repository
public interface SpecDao extends BaseEntityDao<Spec> {

    /**
     * 按项目 id 查询 Spec，版本倒序（用于确定下一个增量版本号）。
     *
     * @param projectId 项目 id
     * @return Spec 列表
     */
    List<Spec> findByProjectIdOrderByVersionDesc(String projectId);

    /**
     * 按项目 id 查询 Spec，版本升序（Spec 版本历史，ep #30）。
     *
     * @param projectId 项目 id
     * @return Spec 列表
     */
    List<Spec> findByProjectIdOrderByVersionAsc(String projectId);

    List<Spec> findByProjectId(String projectId);

    List<Spec> findByStateOrderByCreatedDateAsc(com.changhong.onlinecode.dto.enums.SpecState state);
}
