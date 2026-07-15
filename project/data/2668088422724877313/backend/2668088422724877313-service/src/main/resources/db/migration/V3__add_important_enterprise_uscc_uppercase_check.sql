-- --------------------------------------------------------
-- Flyway V3 迁移脚本（任务：BE-001 增量加固）：统一社会信用代码大写存储 CHECK
-- 前置：V1__create_important_enterprise_table.sql、V2__add_important_enterprise_audit_temporal_check.sql
--   均已入 HEAD，按 Flyway 校验和约定视为不可变（README.md / BE-001-decisions.md
--   「历史脚本不可变、变更追加新版本」）。本脚本不改动 V1/V2，仅以新版本脚本增量加固——
--   这正是本目录既定的 schema 演进方式。
-- 动机（DB 层数据质量兜底，与 V1 五条 + V2 一条 CHECK 同族，补齐最后一个仅由应用层维护的不变量）：
--   PRD 决策 D-2「统一社会信用代码存储转大写——保证唯一性比较大小写不敏感」。
--   V1 已对 USCC 长度（chk_important_enterprises_uscc_len）、名称/资产管理人非空、类别域、
--   删除一致性做兜底，V2 补审计时间一致性；唯独 D-2 的「存储大写」当前仅由应用层
--   （BE-004/BE-005 写入前 toUpperCase()）维护，绕过应用层直写（数据导入/手工修复/后续迁移）
--   可写入小写 USCC。本脚本在 DB 层补齐该兜底，使 D-2 与其它六个不变量一致地拥有 DB 层防线
--   （规范 database.md 第 6/8 条：域完整性与允许值须显式）。
-- 大小写判定的实现选择：列排序规则为 utf8mb4_unicode_ci（大小写不敏感），故
--   `unified_social_credit_code = UPPER(unified_social_credit_code)` 在 _ci 下恒为真（无法识别小写）。
--   因此用 BINARY 显式转换为字节序比较（大小写敏感）：`BINARY col = BINARY UPPER(col)`。
--   BINARY 转换与 UPPER() 均为确定性函数/运算（非 REGEXP），MySQL 8.0.16+ CHECK 可安全执行，
--   与 V1 各 CHECK 同属确定性判定（见 V1 头注第 6 条关于刻意不引入 REGEXP 的说明）。
-- 行为保持：合法写入下永不触发——应用层（BE-004/BE-005）存储前已 toUpperCase()，全数字 USCC 的
--   UPPER 亦等于自身，故本约束仅拒绝绕过应用层的小写脏数据，对既有 BE-005 行为零影响。
-- 唯一性边界说明：本约束不替代唯一索引（uk_important_enterprises_uscc 落在 active_uscc 生成列、
--   在 _ci 排序规则下已大小写不敏感），仅保证存储形态一致（D-2 的「转大写」存储归一化），
--   与唯一索引职责正交。
-- 空表应用：important_enterprises 为全新表（PRD 4.1 从零建设、无历史数据），ALTER ADD CHECK 即时生效、
--   无既有数据校验风险（与 V2 同）。
-- 执行约定：Flyway 每个版本脚本仅执行一次（由 flyway_schema_history 保证）；本脚本非幂等
--   （重复 ADD CONSTRAINT 会报 duplicate key），勿手动重跑，演进请追加 V4。
-- 运行期验证：已在 mysql:8.0.18 上实测——V1→V2→V3 顺序应用成功、INSERT 大写 USCC 通过、
--   INSERT 小写 USCC 被 CHECK 拒绝、全数字 USCC 通过（见 BE-001-decisions.md V3 小节）。
-- --------------------------------------------------------

ALTER TABLE important_enterprises
    ADD CONSTRAINT chk_important_enterprises_uscc_uppercase
        CHECK (BINARY unified_social_credit_code = BINARY UPPER(unified_social_credit_code));
