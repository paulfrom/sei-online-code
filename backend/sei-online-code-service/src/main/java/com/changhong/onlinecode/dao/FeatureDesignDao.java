package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FeatureDesign DAO。契约 §5 端点 P6/P7/P14 + 编码执行互斥（D1）。
 *
 * <p>分页 findByPage 由 {@link BaseEntityDao} 继承提供（P6）。本接口声明 P7（取最新版）、
 * P14（历史版本）、聚合用查询（{@link com.changhong.onlinecode.service.ProjectStateService}），
 * 以及编码互斥的条件 UPDATE（D1）。</p>
 *
 * <p><b>与计划文档的偏差</b>：计划 File Map 称 "FeatureDesignDao + FeatureDesignDaoImpl"，但本仓库
 * DAO 为 Spring Data JPA 接口式（见 {@code AgentDao}，无 *DaoImpl），故此处沿用接口式；
 * 计划要求 DaoImpl 承载的「条件 UPDATE 抢占查询」改以 {@link Modifying} {@link Query} 声明于接口。</p>
 *
 * @author sei-online-code
 */
@Repository
public interface FeatureDesignDao extends BaseEntityDao<FeatureDesign> {

    /**
     * 取项目下所有最新版 FD（is_latest=TRUE），供 ProjectStateService 聚合。
     *
     * @param projectId 项目 id
     * @return 该项目全部最新版 FD
     */
    @Query("SELECT f FROM FeatureDesign f WHERE f.projectId = :projectId AND f.isLatest = true")
    List<FeatureDesign> findLatestByProjectId(@Param("projectId") String projectId);

    /**
     * 取某条 FD 的最新版（P7，按 id 且 is_latest=TRUE）。
     *
     * @param id FD id
     * @return 该 feature 最新版 FD，不存在为 null
     */
    @Query("SELECT f FROM FeatureDesign f WHERE f.id = :id AND f.isLatest = true")
    FeatureDesign findLatestById(@Param("id") String id);

    /**
     * 取某 feature 全部历史版本（P14），按版本倒序。
     *
     * @param featureId feature id（对齐 PlanContent.features[].featureId）
     * @return 该 feature 全部版本，版本倒序
     */
    List<FeatureDesign> findByFeatureIdOrderByVersionDesc(String featureId);

    /**
     * 编码执行互斥抢占（D1）：条件 UPDATE，仅当 build_status != :building 时置为 :building。
     *
     * <p>调用方（FeatureDesignBuildService）传 :building = {@code BUILDING}；
     * 返回 0 表示已在 BUILDING（调用方抛 409 ConflictException）；返回 1 表示抢占成功。</p>
     *
     * <p>{@code clearAutomatically = true}：批量 UPDATE 后清空持久化上下文，避免后续读到 stale 实体。</p>
     *
     * @param id       FD id
     * @param building 目标构建态（BUILDING）
     * @return affected rows（0 或 1）
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE FeatureDesign f SET f.buildStatus = :building WHERE f.id = :id AND f.buildStatus <> :building")
    int tryAcquireBuildLock(@Param("id") String id, @Param("building") FeatureDesignBuildStatus building);

    /**
     * 将项目下某 feature 所有 is_latest=true 的 FD 置为 false（标记非最新）。
     *
     * @param projectId 项目 id
     * @param featureId feature id
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE FeatureDesign f SET f.isLatest = false WHERE f.projectId = :projectId AND f.featureId = :featureId AND f.isLatest = true")
    void markNonLatest(@Param("projectId") String projectId, @Param("featureId") String featureId);

    /**
     * 级联失效（D15）：项目下所有最新版 FD status→STALE，且 build_status∈{BUILT,BUILD_FAILED}→STALE。
     *
     * <p>使用 native SQL 以支持 CASE 表达式。
     *
     * @param projectId 项目 id
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(nativeQuery = true, value = "UPDATE oc_feature_design SET status = 'STALE', build_status = CASE WHEN build_status IN ('BUILT', 'BUILD_FAILED') THEN 'STALE' ELSE build_status END, last_edited_date = NOW() WHERE project_id = :projectId AND is_latest = TRUE")
    void cascadeStale(@Param("projectId") String projectId);
}
