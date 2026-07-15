-- --------------------------------------------------------
-- Flyway V2 迁移脚本（任务：BE-001 增量加固）：审计时间一致性 CHECK
-- 前置：V1__create_important_enterprise_table.sql 已随 a9530a6 入 HEAD，按 Flyway 校验和约定视为不可变
--   （README.md / BE-001-decisions.md「历史脚本不可变、变更追加新版本」）。本脚本不改动 V1，
--   仅以新版本脚本增量加固——这正是本目录既定的 schema 演进方式。
-- 动机（DB 层数据质量兜底，与 V1 五条 CHECK 同族）：
--   PRD 7.4「审计字段 created_at/updated_at 由服务层自动维护」隐含一条不变量——最后更新时间不得早于
--   创建时间（一条记录不可能在创建之前被编辑）。V1 已对 is_deleted↔deleted_at 做业务规则一致性兜底
--   （chk_important_enterprises_delete_consistency），本脚本补齐审计时间方向的同类兜底：
--   防止绕过应用层（BE-005 sei-core 审计拦截器）直写、或数据导入/时钟回拨产生
--   last_edited_date < created_date 的逻辑不可能审计状态（规范 database.md 第 6 条：业务规则完整性须显式）。
-- 确定性：`>=` 为确定性比较运算（非 REGEXP、非非确定性函数），MySQL 8.0.16+ CHECK 可安全执行，
--   与 V1 各 CHECK 同属确定性判定（见 V1 头注第 6 条关于刻意不引入 REGEXP 的说明）。
-- 行为保持：合法写入下永不触发——INSERT 时 created_date/last_edited_date 同取 CURRENT_TIMESTAMP
--   （相等，`>=` 成立）；UPDATE 时 sei-core 将 last_edited_date 置为当前时刻（≥ 创建时间）。
--   故本约束仅拒绝脏数据，不影响任何合法路径，对既有 BE-005 行为零影响。
-- 空表应用：important_enterprises 为全新表（PRD 4.1 从零建设、无历史数据），ALTER ADD CHECK 即时生效、
--   无既有数据校验风险。
-- 执行约定：Flyway 每个版本脚本仅执行一次（由 flyway_schema_history 保证）；本脚本非幂等
--   （重复 ADD CONSTRAINT 会报 duplicate key），勿手动重跑，演进请追加 V3。
-- --------------------------------------------------------

ALTER TABLE important_enterprises
    ADD CONSTRAINT chk_important_enterprises_audit_temporal
        CHECK (last_edited_date >= created_date);
