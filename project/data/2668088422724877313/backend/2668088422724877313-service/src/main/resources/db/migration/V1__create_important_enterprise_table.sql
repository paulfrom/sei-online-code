-- --------------------------------------------------------
-- Flyway V1 迁移脚本（任务：BE-001）：重要企业管理台账表
-- 需求：在股权台账系统中维护需要重点跟踪的企业基础信息
-- 说明：
--   1. 经核查当前工作区 backend/2668088422724877313-service 下无企业用户/员工实体及用户表，
--      asset_manager_id 暂以 VARCHAR(36) 字符串保存用户标识，不建立外键约束；
--      后续企业用户表建成后可通过 ALTER TABLE 添加外键约束（PRD 决策 D-1/D-4）。
--   2. 企业名称与统一社会信用代码需在“未删除”记录范围内唯一。
--      通过 active_name / active_uscc 生成列实现：删除后对应生成列为 NULL，
--      MySQL 唯一索引允许多个 NULL，从而释放名称/代码供后续复用。
--   3. 审计字段与软删除字段使用 PRD 约定的命名：
--      created_by / updated_by / created_at / updated_at / deleted_at / is_deleted。
--   4. 索引覆盖 PRD 要求的 name、unified_social_credit_code、asset_manager_id、
--      category、deleted_at；同时保留 is_deleted 索引以支持按删除状态查询。
-- TODO(BE-001-follow-up): 已确认当前工作区无企业用户表，asset_manager_id 本期暂以 VARCHAR(36) 保存；待用户表建成后改为外键或补充外键约束。
-- --------------------------------------------------------

CREATE TABLE IF NOT EXISTS important_enterprises
(
    id                         VARCHAR(36)   NOT NULL COMMENT '主键，UUID',
    name                       VARCHAR(200)  NOT NULL COMMENT '企业名称，未删除记录内唯一',
    category                   VARCHAR(50)   NOT NULL COMMENT '企业类别：IMPORTANT_SUBSIDIARY（重要子公司）、HOLDING_COMPANY（控股公司）',
    unified_social_credit_code VARCHAR(18)   NOT NULL COMMENT '统一社会信用代码，18位，未删除记录内唯一',
    asset_manager_id           VARCHAR(36)   NOT NULL COMMENT '资产管理人（企业用户）标识；暂以字符串保存，待用户表建成后迁移为外键',
    created_by                 VARCHAR(36)   NOT NULL COMMENT '创建人ID',
    updated_by                 VARCHAR(36)   NOT NULL COMMENT '最后更新人ID',
    created_at                 DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at                 DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    is_deleted                 TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0 表示未删除，1 表示已删除',
    deleted_at                 DATETIME(3)       NULL COMMENT '逻辑删除时间，删除时由业务层写入，保留审计轨迹',
    active_name                VARCHAR(200)  AS (CASE WHEN is_deleted = 0 THEN name ELSE NULL END) STORED COMMENT '未删除时等于 name，删除后为 NULL，用于唯一性校验',
    active_uscc                VARCHAR(18)   AS (CASE WHEN is_deleted = 0 THEN unified_social_credit_code ELSE NULL END) STORED COMMENT '未删除时等于 unified_social_credit_code，删除后为 NULL，用于唯一性校验',
    PRIMARY KEY (id),
    UNIQUE KEY uk_important_enterprises_name (active_name),
    UNIQUE KEY uk_important_enterprises_uscc (active_uscc),
    KEY idx_important_enterprises_name (name),
    KEY idx_important_enterprises_uscc (unified_social_credit_code),
    KEY idx_important_enterprises_asset_manager_id (asset_manager_id),
    KEY idx_important_enterprises_category (category),
    KEY idx_important_enterprises_deleted_at (deleted_at),
    KEY idx_important_enterprises_is_deleted (is_deleted)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='重要企业台账';
