package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Plan;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Plan DAO。契约 §5 端点 P2/P13。
 *
 * <p>分页 findByPage 由 {@link BaseEntityDao} 继承提供。本接口仅声明 P2（取最新版）/ P13（历史版本）。</p>
 *
 * <p><b>与计划文档的偏差</b>：计划 File Map 称 "PlanDao + PlanDaoImpl"，但本仓库 DAO 为
 * Spring Data JPA 接口式（见 {@code AgentDao}，无 *DaoImpl），故此处沿用接口式，不新增 Impl。</p>
 *
 * @author sei-online-code
 */
@Repository
public interface PlanDao extends BaseEntityDao<Plan> {

    /**
     * 取项目最新版本 Plan（P2，is_latest=TRUE）。
     * <p>partial unique index uk_plan_proj_latest 保证每项目至多一行 is_latest=TRUE。</p>
     *
     * @param projectId 项目 id
     * @return 最新 Plan，未生成时为 null
     */
    @Query("SELECT p FROM Plan p WHERE p.projectId = :projectId AND p.isLatest = true")
    Plan findLatestByProjectId(@Param("projectId") String projectId);

    /**
     * 取项目全部历史版本（P13），按版本倒序。
     *
     * @param projectId 项目 id
     * @return 全部版本（含非 latest），版本倒序
     */
    List<Plan> findByProjectIdOrderByVersionDesc(String projectId);
}
