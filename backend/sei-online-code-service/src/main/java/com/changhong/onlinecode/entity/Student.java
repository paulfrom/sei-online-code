package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.StudentGender;
import com.changhong.onlinecode.dto.enums.StudentStatus;
import com.changhong.sei.core.entity.BaseAuditableEntity;
import com.changhong.sei.core.entity.ISoftDelete;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 学生主数据实体。契约 PRD §5.1。
 *
 * <p>审计字段由 {@link BaseAuditableEntity} 提供：
 * {@code creatorId/creatorAccount/creatorName/createdDate/lastEditorId/lastEditorAccount/lastEditorName/lastEditedDate}。
 * 字段名对外为 {@code createdDate / lastEditedDate}。</p>
 *
 * <p>枚举字段复用 {@code com.changhong.onlinecode.dto.enums} 包内的共享枚举，遵循既有
 * 约定（api 模块内统一对外枚举），避免在 entity 内嵌套定义重复枚举。</p>
 *
 * <p>软删除遵循 sei-core {@link ISoftDelete} 约定，{@code deleted} 字段保存删除时间戳（epoch ms）；
 * 同时按 PRD §5.1 显式保留 {@code deletedBy} 操作人字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "oc_student", indexes = {
        @Index(name = "idx_student_status", columnList = "status"),
        @Index(name = "idx_student_class_name", columnList = "class_name"),
        @Index(name = "idx_student_birth_date", columnList = "birth_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_no", columnNames = "student_no")
})
@Access(AccessType.FIELD)
public class Student extends BaseAuditableEntity implements ISoftDelete {

    private static final long serialVersionUID = 1L;

    /** 学号，全局唯一。 */
    @Column(name = "student_no", nullable = false, length = 32, unique = true)
    private String studentNo;

    /** 姓名。 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 性别枚举。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 16)
    private StudentGender gender;

    /** 出生日期。 */
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    /** 身份证号；非空时全局唯一，唯一性由 Service 校验，数据库兜底约束见迁移文件部分唯一索引。 */
    @Column(name = "id_card_no", length = 18)
    private String idCardNo;

    /** 手机号；非空时全局唯一，唯一性由 Service 校验，数据库兜底约束见迁移文件部分唯一索引。 */
    @Column(name = "mobile", length = 11)
    private String mobile;

    /** 班级名称字符串，本期不引入班级主数据。 */
    @Column(name = "class_name", length = 64)
    private String className;

    /** 入学日期。 */
    @Column(name = "enrollment_date")
    private LocalDate enrollmentDate;

    /** 启用/停用状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StudentStatus status = StudentStatus.ENABLED;

    /** 头像 URL。 */
    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    /** 备注。 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 多租户预留字段，本期不启用隔离。 */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    /** 软删除操作人；删除时间为 {@link #deleted}（epoch ms）。 */
    @Column(name = "deleted_by", length = 36)
    private Long deletedBy;

    /** sei-core 软删除字段，保存删除时间（epoch ms），{@code 0} 表示未删除。 */
    @Column(name = "deleted_date", nullable = false, columnDefinition = "BIGINT")
    private Long deleted = 0L;

    @Override
    @Transient
    public String getDisplay() {
        return studentNo;
    }
}
