package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Skill;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

/**
 * Skill DAO。分页 findByPage 由 BaseEntityDao 继承提供。
 *
 * @author sei-online-code
 */
@Repository
public interface SkillDao extends BaseEntityDao<Skill> {

    /**
     * 按名称查询技能（导入 name 去重 + 内置技能种子 upsert 用）。
     *
     * @param name 技能名
     * @return 命中的技能，未命中为 null
     */
    Skill findByName(String name);
}
