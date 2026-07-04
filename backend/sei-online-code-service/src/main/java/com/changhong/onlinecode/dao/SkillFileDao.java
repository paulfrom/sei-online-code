package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.SkillFile;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 技能辅助文件 DAO。Spring Data 派生查询。
 *
 * <p>删技能由 FK ON DELETE CASCADE 兜底，故无 {@code deleteBySkillId}（最小化）。</p>
 *
 * @author sei-online-code
 */
@Repository
public interface SkillFileDao extends BaseEntityDao<SkillFile> {

    /** 按技能查全部辅助文件（SkillService.findOne populate 用）。 */
    List<SkillFile> findBySkillId(String skillId);

    /** 批量按技能查（findByPage 列表 populate 用，单次 IN 查询避免 N+1）。 */
    List<SkillFile> findBySkillIdIn(Collection<String> skillIds);
}
