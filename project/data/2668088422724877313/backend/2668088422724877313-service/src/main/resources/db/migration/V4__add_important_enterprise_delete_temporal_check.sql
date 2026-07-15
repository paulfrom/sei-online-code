-- --------------------------------------------------------
-- Flyway V4 迁移脚本（任务：BE-001 增量加固）：逻辑删除时间一致性 CHECK
-- 前置：V1__create_important_enterprise_table.sql、V2__add_important_enterprise_audit_temporal_check.sql、
--   V3__add_important_enterprise_uscc_uppercase_check.sql 均已入 HEAD/工作区，按 Flyway 校验和约定视为不可变
--   （README.md / BE-001-decisions.md「历史脚本不可变、变更追加新版本」）。本脚本不改动 V1/V2/V3，
--   仅以新版本脚本增量加固——这正是本目录既定的 schema 演进方式。
-- 动机（DB 层数据质量兜底，与 V1 五条 + V2/V3 各一条 CHECK 同族，补齐审计时间链的最后一环）：
--   PRD 7.4「审计字段 created_at/updated_at 由服务层自动维护」与 D-1「逻辑删除保留审计轨迹」共同隐含
--   一条时间不变量——逻辑删除时间不得早于最后更新时间（一条记录不可能在最后编辑之前被删除）。
--   V2 已保证 last_edited_date >= created_date（创建→编辑方向）；V1 的 chk_important_enterprises_delete_consistency
--   保证 is_deleted↔deleted_at 标记/时间一致（是否删除方向）；本脚本补齐删除时间方向：
--   未删除时 deleted_at 必为 NULL（已由 V1 delete_consistency 兜底），已删除时 deleted_at >= last_edited_date。
--   三者合一致：created_date <= last_edited_date <= deleted_at，构成完整审计时间链
--   （规范 database.md 第 6 条：业务规则完整性须显式）。
--   防止绕过应用层（BE-005 软删除逻辑）直写、或数据导入/手工修复/时钟回拨产生
--   deleted_at < last_edited_date 的逻辑不可能删除状态。
-- 确定性：`>=` 与 `IS NULL` 为确定性比较/判定（非 REGEXP、非非确定性函数），MySQL 8.0.16+ CHECK 可安全执行，
--   与 V1/V2 各 CHECK 同属确定性判定（见 V1 头注第 6 条关于刻意不引入 REGEXP 的说明）。
-- 行为保持：合法写入下永不触发——未删除记录 deleted_at 为 NULL（约束前半段短路通过）；
--   软删除时业务层（BE-005）将 deleted_at 置为当前时刻，而 last_edited_date 至多为当前时刻
--   （sei-core 审计拦截器在同一 UPDATE 内将其置为当前时刻，与 deleted_at 同语句同时间戳，相等满足 >=；
--   若软删除不触碰 last_edited_date 则其停留在更早的最后内容编辑时刻，deleted_at(now) > last_edited_date）。
--   故本约束仅拒绝脏数据，不影响任何合法软删除路径，对既有 BE-005 行为零影响。
-- 空表应用：important_enterprises 为全新表（PRD 4.1 从零建设、无历史数据），ALTER ADD CHECK 即时生效、
--   无既有数据校验风险（与 V2/V3 同）。
-- 执行约定：Flyway 每个版本脚本仅执行一次（由 flyway_schema_history 保证）；本脚本非幂等
--   （重复 ADD CONSTRAINT 会报 duplicate key），勿手动重跑，演进请追加 V5。
-- --------------------------------------------------------

ALTER TABLE important_enterprises
    ADD CONSTRAINT chk_important_enterprises_delete_temporal
        CHECK (deleted_at IS NULL OR deleted_at >= last_edited_date);
