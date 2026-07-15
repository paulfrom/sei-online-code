package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Student;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

/**
 * 学生主数据 DAO。契约 PRD §5.2。
 *
 * <p>分页查询等通用能力由 sei-core {@link BaseEntityDao} 提供；
 * 唯一性校验（学号 / 身份证号 / 手机号）由 BE-002 {@code StudentService} 显式实现，
 * 本 DAO 不承担业务校验职责，避免与数据库唯一索引在并发路径上的责任混淆。</p>
 */
@Repository
public interface StudentDao extends BaseEntityDao<Student> {
}
