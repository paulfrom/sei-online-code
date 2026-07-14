-- --------------------------------------------------------
-- Flyway V1 迁移脚本（任务：BE-001）：重要企业管理台账表
-- 需求：在股权台账系统中维护需要重点跟踪的企业基础信息
-- 引擎下限：MySQL 8.0.16+。本脚本下方的 CHECK 约束自 8.0.16 起方被强制执行；
--   低于该版本（如 MySQL 5.7）CHECK 仅被解析、静默忽略，会使域/业务规则完整性兜底全部失效而无任何报错。
--   STORED 生成列亦属 8.0 合法语法。部署前须确认目标实例 ≥ 8.0.16
--   （实测基线为 mysql:8.0.18，见 BE-001-decisions.md；规范 database.md 第 9 条：维护可追溯的 schema 工件）。
-- 说明：
--   1. 审计字段命名遵循 SEI 平台基类 com.changhong.sei.core.entity.BaseAuditableEntity 的物理列名
--      （creator_id / creator_account / creator_name / created_date / last_editor_id / last_editor_account /
--       last_editor_name / last_edited_date），与兄弟服务（sei-online-code-service 全部 oc_* 表）完全一致。
--      BE-002 实体必须 `extends BaseAuditableEntity`（CLAUDE.md：后端须遵循 sei-core 分层架构），
--      故审计列必须使用平台物理命名，否则 JPA 会映射到不存在的列导致运行期失败。
--      PRD 6.1.1 的概念字段 created_by / updated_by / created_at / updated_at 分别对应
--      creator_id / last_editor_id / created_date / last_edited_date（账户与姓名为平台冗余列，由 sei-core 审计拦截器自动填充）。
--   2. 引用完整性决策（AC-3 已在代码评审前确认）：经核查当前工作区
--      backend/2668088422724877313-service 下无企业用户/员工实体及用户表（Java 源码仅
--      HelloController/DistributedLockController/HelloService 及空 package-info 占位，
--      entity 包仅含 package-info.java），asset_manager_id 以 VARCHAR(36) 字符串保存用户标识，
--      故本期显式不建立外键约束（referential integrity 由应用层 BE-005 校验存在性兜底）；
--      详见同目录 BE-001-decisions.md 的 AC-3 小节。后续企业用户表建成后可通过新增 Flyway
--      版本脚本 ALTER TABLE 添加外键约束（PRD 决策 D-1/D-4，已以 TODO(BE-001-follow-up) 标记）。
--   3. 企业名称与统一社会信用代码需在“未删除”记录范围内唯一。
--      通过 active_name / active_uscc 生成列实现：删除后对应生成列为 NULL，
--      MySQL 唯一索引允许多个 NULL，从而释放名称/代码供后续复用。
--   4. 逻辑删除：PRD D-1 采用逻辑删除。sei-core 的 ISoftDelete（单列 deleted BIGINT）在兄弟服务
--      未启用，本模块沿用 PRD 约定的 is_deleted（TINYINT 标记）+ deleted_at（删除时间戳）双列，
--      保留完整审计轨迹。
--   5. 索引覆盖 PRD 要求的 name、unified_social_credit_code、asset_manager_id、
--      category、deleted_at；同时保留 is_deleted 索引以支持按删除状态查询。
--   6. DB 层兜底 CHECK 约束共五条（均为确定性函数/判定，MySQL 8.0.16+ CHECK 可安全执行；
--      字符集与 GB 32100-2015 校验位校验仍由应用层 BE-004 UnifiedSocialCreditCodeUtils 负责，
--      DB 层刻意不引入 REGEXP 类约束以规避 MySQL CHECK 对函数确定性判定的执行风险）：
--      ① uscc_len：统一社会信用代码长度恒为 18（PRD 6.1.2 / AC-6）；
--      ② name_nonempty：企业名称非空/非纯空白（PRD 6.1.1）；
--      ③ category_domain：category 取值限于预定义枚举（PRD 6.1.1，可扩展，见 §7.5）；
--      ④ asset_manager_nonempty：资产管理人标识非空/非纯空白（PRD 6.1.1 必填 / D-4；存在性由应用层 BE-005 校验，DB 层仅兜底非空，与 ② 同属必填字段域完整性兜底）；
--      ⑤ delete_consistency：is_deleted 与 deleted_at 必须一致（规范 database.md 业务规则完整性）。
--   7. active_name / active_uscc 为 STORED 生成列（由 MySQL 按 is_deleted 自动维护，应用不可写入）：
--      BE-002 JPA 实体不得将二者映射为可写字段，否则 INSERT/UPDATE 会运行期报错
--      “The value specified for generated column ... is not allowed”；如需只读映射须显式声明
--      @Column(insertable = false, updatable = false)（防 BE-002 写入生成列失败，跨任务约束）。
-- TODO(BE-001-follow-up): 已确认当前工作区无企业用户表，asset_manager_id 本期暂以 VARCHAR(36) 保存；待用户表建成后改为外键或补充外键约束。
-- --------------------------------------------------------

CREATE TABLE IF NOT EXISTS important_enterprises
(
    id                         VARCHAR(36)   NOT NULL COMMENT '主键，UUID（AbstractEntity<String>）',
    name                       VARCHAR(200)  NOT NULL COMMENT '企业名称，未删除记录内唯一',
    category                   VARCHAR(50)   NOT NULL COMMENT '企业类别：IMPORTANT_SUBSIDIARY（重要子公司）、HOLDING_COMPANY（控股公司）',
    unified_social_credit_code VARCHAR(18)   NOT NULL COMMENT '统一社会信用代码，18位，未删除记录内唯一',
    asset_manager_id           VARCHAR(36)   NOT NULL COMMENT '资产管理人（企业用户）标识；暂以字符串保存，待用户表建成后迁移为外键',
    -- 审计字段：SEI 平台 BaseAuditableEntity 物理命名，由 sei-core 审计拦截器自动填充（对齐兄弟服务 oc_* 表，nullable 与平台一致）
    creator_id                 VARCHAR(36)       NULL COMMENT '创建人ID（PRD created_by）',
    creator_account            VARCHAR(100)      NULL COMMENT '创建人账号',
    creator_name               VARCHAR(100)      NULL COMMENT '创建人姓名',
    created_date               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（PRD created_at）',
    last_editor_id             VARCHAR(36)       NULL COMMENT '最后更新人ID（PRD updated_by）',
    last_editor_account        VARCHAR(100)      NULL COMMENT '最后更新人账号',
    last_editor_name           VARCHAR(100)      NULL COMMENT '最后更新人姓名',
    last_edited_date           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后更新时间（PRD updated_at，由应用层写入）',
    -- 逻辑删除（PRD D-1；本模块自定义，非 sei-core ISoftDelete）
    is_deleted                 TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0 表示未删除，1 表示已删除',
    deleted_at                 TIMESTAMP        NULL COMMENT '逻辑删除时间，删除时由业务层写入，保留审计轨迹',
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
    KEY idx_important_enterprises_is_deleted (is_deleted),
    -- DB 层数据质量兜底：统一社会信用代码固定 18 位（PRD 6.1.2 硬性规则“长度必须为 18 位”）。
    -- VARCHAR(18) 仅约束上限，需 CHECK 强制恰好 18 位，防止绕过应用层直写短码脏数据。
    -- 应用层 BE-004 承担字符集与 GB 32100-2015 校验位校验；CHAR_LENGTH 为确定性函数，MySQL CHECK 可安全执行。
    CONSTRAINT chk_important_enterprises_uscc_len CHECK (CHAR_LENGTH(unified_social_credit_code) = 18),
    -- DB 层数据质量兜底：企业名称不得为空/纯空白（PRD 6.1.1：name 必填且全系统唯一）。
    -- NOT NULL 仅拒绝 NULL，无法拒绝空串/纯空白名；CHAR_LENGTH+TRIM 均为确定性函数，MySQL CHECK 可安全执行，
    -- 防止绕过应用层（BE-005）直写空名称脏数据，与统一信用代码长度 CHECK 同属 DB 层兜底防线。
    CONSTRAINT chk_important_enterprises_name_nonempty CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    -- DB 层域完整性兜底：企业类别必须为预定义枚举值（PRD 6.1.1：category 为枚举 IMPORTANT_SUBSIDIARY / HOLDING_COMPANY）。
    -- 与 USCC 长度 CHECK 同理，防止绕过应用层（BE-005）直写非法类别脏数据；`IN (...)` 为确定性判定（非 REGEXP），MySQL CHECK 可安全执行。
    -- 不阻塞可扩展性：后续新增类别（如参股公司、联营企业，PRD §7.5）时，追加新版本迁移脚本 ALTER 本约束扩展取值列表即可（规范 database.md 第 6/8 条：域完整性与允许值须显式）。
    CONSTRAINT chk_important_enterprises_category_domain CHECK (category IN ('IMPORTANT_SUBSIDIARY', 'HOLDING_COMPANY')),
    -- DB 层域完整性兜底：资产管理人标识不得为空串/纯空白（PRD 6.1.1：asset_manager_id 必填；D-4 单选必填；6.2.1/AC-7 须指向有效企业用户）。
    -- 与 chk_important_enterprises_name_nonempty 同理：NOT NULL 仅拒绝 NULL，无法拒绝 ''/空白标识；CHAR_LENGTH+TRIM 为确定性函数，MySQL CHECK 可安全执行。
    -- 用户存在性校验由应用层（BE-005）承担（本期无企业用户表、未建外键，见 AC-3），DB 层仅兜底非空，防止绕过应用层直写空资产管理人脏数据（规范 database.md 第 6/8 条：域完整性与允许值须显式）。
    CONSTRAINT chk_important_enterprises_asset_manager_nonempty CHECK (CHAR_LENGTH(TRIM(asset_manager_id)) > 0),
    -- 业务规则完整性兜底：逻辑删除标记与删除时间必须一致——未删除（is_deleted=0）时 deleted_at 必为 NULL，已删除（is_deleted=1）时 deleted_at 必非 NULL。
    -- 防止业务层（BE-005）软删除时只改标记不写时间、或误留孤立删除时间（规范 database.md 第 6 条：业务规则完整性须显式）。
    CONSTRAINT chk_important_enterprises_delete_consistency
        CHECK ((is_deleted = 0 AND deleted_at IS NULL) OR (is_deleted = 1 AND deleted_at IS NOT NULL))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='重要企业台账';
