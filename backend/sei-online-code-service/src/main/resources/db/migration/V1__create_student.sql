-- 学生主数据表迁移。契约 PRD §5.1。
--
-- 实现说明：
-- 1. 实际数据库 PostgreSQL（application.yaml: jdbc:postgresql），本 DDL 写为 PostgreSQL 方言；
--    若后续切换 MySQL，需要重新评估 VARCHAR/TIMESTAMP/部分唯一索引语法。
-- 2. Hibernate ddl-auto=validate 要求表/列结构与实体严格匹配，否则启动期会校验失败。
-- 3. 审计字段命名沿用 BaseAuditableEntity 默认映射（created_date / last_edited_date），
--    对外字段名为 createdDate / lastEditedDate。
-- 4. 软删除字段 deleted_date 使用 BIGINT 存 epoch ms，对应实体 ISoftDelete#deleted。

CREATE TABLE oc_student (
    -- sei-core BaseEntity 主键
    id                  VARCHAR(36) PRIMARY KEY,

    -- sei-core BaseAuditableEntity 审计字段
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(50),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(50),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,

    -- 业务字段
    student_no          VARCHAR(32)  NOT NULL,
    name                VARCHAR(64)  NOT NULL,
    gender              VARCHAR(16)  NOT NULL,
    birth_date          DATE         NOT NULL,
    id_card_no          VARCHAR(18),
    mobile              VARCHAR(11),
    class_name          VARCHAR(64),
    enrollment_date     DATE,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    avatar_url          VARCHAR(512),
    remark              VARCHAR(500),
    tenant_id           VARCHAR(64),

    -- sei-core ISoftDelete 软删除：deleted_date 保存 epoch ms，0 表示未删除
    deleted_date        BIGINT       NOT NULL DEFAULT 0,
    deleted_by          VARCHAR(36),

    -- 完整约束
    CONSTRAINT oc_student_status_chk CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT oc_student_gender_chk CHECK (gender IN ('MALE', 'FEMALE', 'UNKNOWN')),
    CONSTRAINT oc_student_student_no_chk CHECK (char_length(student_no) BETWEEN 1 AND 32),
    CONSTRAINT oc_student_name_chk CHECK (char_length(name) BETWEEN 1 AND 64)
);

-- PRD §5.1 唯一性约束：
-- uk_student_no        : student_no 全局唯一
-- uk_student_idcard    : id_card_no 非空时全局唯一（PostgreSQL 部分唯一索引）
-- uk_student_mobile    : mobile 非空时全局唯一（PostgreSQL 部分唯一索引）
CREATE UNIQUE INDEX uk_student_no     ON oc_student (student_no);
CREATE UNIQUE INDEX uk_student_idcard ON oc_student (id_card_no) WHERE id_card_no IS NOT NULL;
CREATE UNIQUE INDEX uk_student_mobile ON oc_student (mobile)       WHERE mobile       IS NOT NULL;

-- 列表查询辅助索引（命名与实体 @Index 一致，便于 ddl-auto=validate 通过）
CREATE INDEX idx_student_status      ON oc_student (status);
CREATE INDEX idx_student_class_name  ON oc_student (class_name);
CREATE INDEX idx_student_birth_date  ON oc_student (birth_date);

-- 列表页常用搜索的索引补充（避免后续修复 ALTER）
CREATE INDEX idx_student_name        ON oc_student (name);
CREATE INDEX idx_student_mobile_idx  ON oc_student (mobile);

COMMENT ON TABLE  oc_student                IS '学生主数据';
COMMENT ON COLUMN oc_student.deleted_date   IS 'sei-core ISoftDelete 软删除 epoch ms；0=未删除';
COMMENT ON COLUMN oc_student.deleted_by     IS '软删除操作人 ID';
COMMENT ON COLUMN oc_student.student_no     IS '学号，全局唯一';
COMMENT ON COLUMN oc_student.class_name     IS '班级名称，字符串承载，本期不引入班级主数据';
COMMENT ON COLUMN oc_student.tenant_id      IS '多租户字段，本期预留不启用';
