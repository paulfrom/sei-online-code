package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.PlatformConfig;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

/**
 * 平台配置 DAO（B34）。单例行，标准 findOne/save 由 BaseEntityDao 继承提供。
 *
 * @author sei-online-code
 */
@Repository
public interface PlatformConfigDao extends BaseEntityDao<PlatformConfig> {
}
