-- Phase 4: 来源→config JSONB（multica 维度 d）。oc_skill.source / source_type 双列
-- 收敛为单个 config TEXT 列（JSON 串，经 SkillConfigConverter），仅承载 origin 串；
-- 来源类型由 origin 前缀隐式编码（github:/local:/inline:），不再单列枚举（SkillSourceType 已删）。
-- §6 hash recipe 的 source 部分改取 config.origin（值不变 → hash 不变，无复现破坏）。
--
-- 回填：既有 source 列值包成 {"origin": <source>} JSON 串写入 config；source 为 NULL 的行 config 留空。
-- V3 种子 INSERT 仍引用 source/source_type 列——迁移按序执行，V9 前列仍在，INSERT 合法（同 V8 处理 computed_hash）。
ALTER TABLE oc_skill ADD COLUMN config TEXT;
UPDATE oc_skill SET config = jsonb_build_object('origin', source)::text WHERE source IS NOT NULL;
ALTER TABLE oc_skill DROP COLUMN source;
ALTER TABLE oc_skill DROP COLUMN source_type;
