package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.dto.enums.MemorySeedTemplateStatus;
import com.changhong.onlinecode.entity.MemorySeedTemplate;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MemorySeedTemplate DAO。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.1。
 *
 * @author sei-online-code
 */
@Repository
public interface MemorySeedTemplateDao extends BaseEntityDao<MemorySeedTemplate> {

    /**
     * 查询当前 ACTIVE + is_default=true 的全局默认模板。
     *
     * @return 默认模板；不存在返回 null
     */
    @Query("SELECT t FROM MemorySeedTemplate t WHERE t.isDefault = true AND t.status = :status")
    MemorySeedTemplate findActiveDefault(@Param("status") MemorySeedTemplateStatus status);

    /**
     * 查询所有 ACTIVE 模板，供项目选择。
     *
     * @param status ACTIVE
     * @return ACTIVE 模板列表
     */
    List<MemorySeedTemplate> findByStatus(MemorySeedTemplateStatus status);

    /**
     * 查询同 code 全部版本。
     *
     * @param code 模板编码
     * @return 同 code 全部版本
     */
    List<MemorySeedTemplate> findByCode(String code);
}