-- --------------------------------------------------------
-- Flyway V5 迁移脚本（任务：BE-001 增量加固）：统一社会信用代码 ASCII 字符集 CHECK
-- 前置：V1__create_important_enterprise_table.sql、V2__add_important_enterprise_audit_temporal_check.sql、
--   V3__add_important_enterprise_uscc_uppercase_check.sql、V4__add_important_enterprise_delete_temporal_check.sql
--   均已入 HEAD/工作区，按 Flyway 校验和约定视为不可变（README.md / BE-001-decisions.md
--   「历史脚本不可变、变更追加新版本」）。本脚本不改动 V1/V2/V3/V4，仅以新版本脚本增量加固——
--   这正是本目录既定的 schema 演进方式（V4 头注亦写明「演进请追加 V5」）。
-- 动机（DB 层数据质量兜底，与 V1 五条 + V2/V3/V4 各一条 CHECK 同族，补齐 USCC 字符集的 DB 层防线）：
--   PRD 6.1.2 规定统一社会信用代码字符集严格为 [0-9A-Z]（数字与 大写 拉丁字母，纯 ASCII）。
--   V1 已约束长度恒为 18（chk_important_enterprises_uscc_len），V3 已约束大写存储
--   （chk_important_enterprises_uscc_uppercase）；但二者均无法识别 多字节 非法字符——
--   一个全角数字（如 `９`，utf8mb4 下 3 字节）或汉字混入的 18 字符 USCC：CHAR_LENGTH 仍为 18（V1 通过）、
--   UPPER(全角/汉字)=自身故 BINARY 比较相等（V3 通过），却显然非法。本脚本补齐该 DB 层防线：
--   要求 USCC 的 字节长度 等于 字符长度，即每个字符均为单字节（ASCII）——多字节字符会使 LENGTH > CHAR_LENGTH 而被拒绝。
--   完整的 GB 32100-2015 校验位与字符集精细判定仍由应用层 BE-004 UnifiedSocialCreditCodeUtils 承担；
--   DB 层刻意不引入 REGEXP（见 V1 头注第 6 条关于刻意不引入 REGEXP 的说明），本约束以 LENGTH/CHAR_LENGTH
--   这一对确定性函数覆盖「中文系统中最常见的高概率误录形态」（全角数字/汉字混入），ASCII 标点等低概率残留形态由应用层兜底。
-- 确定性：LENGTH() 与 CHAR_LENGTH() 均为确定性函数（非 REGEXP、非非确定性函数），MySQL 8.0.16+ CHECK 可安全执行，
--   与 V1/V2/V3/V4 各 CHECK 同属确定性判定。
-- 行为保持：合法写入下永不触发——合法 USCC 为 [0-9A-Z] 纯 ASCII，每字符单字节，LENGTH 恒等于 CHAR_LENGTH（=18）。
--   故本约束仅拒绝多字节脏数据，不影响任何合法路径，对既有 BE-005 行为零影响。
-- 唯一性边界说明：本约束职责与唯一索引 uk_important_enterprises_uscc（落在 active_uscc 生成列）正交，仅约束字符集形态。
-- 空表应用：important_enterprises 为全新表（PRD 4.1 从零建设、无历史数据），ALTER ADD CHECK 即时生效、
--   无既有数据校验风险（与 V2/V3/V4 同）。
-- 执行约定：Flyway 每个版本脚本仅执行一次（由 flyway_schema_history 保证）；本脚本非幂等
--   （重复 ADD CONSTRAINT 会报 duplicate key），勿手动重跑。V 系列于 V5 收口，不再追加 V6+
--   （残余 GB 32100-2015 校验位/各位置字符规则归应用层 BE-004，见 BE-001-status.md「约束」）。
-- --------------------------------------------------------

ALTER TABLE important_enterprises
    ADD CONSTRAINT chk_important_enterprises_uscc_ascii
        CHECK (LENGTH(unified_social_credit_code) = CHAR_LENGTH(unified_social_credit_code));
