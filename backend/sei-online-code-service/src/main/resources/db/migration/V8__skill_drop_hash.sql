-- Phase 8: 弃持久化 skill hash——删除 oc_skill.computed_hash 列与索引。
-- 决策（Phase 3）：导入去重改以 name 为键（uk_skill_name 唯一约束 + service 层 ConflictException 409），
-- 不再按 hash 幂等。computedHash 改为 Skill 实体 @Transient 运行时按 §6 recipe 计算
-- （SkillHasher.compute(source,name,description,content)），供 materializer .lock 复现标记与 DTO 返回。
-- V3/V6 种子 INSERT 中硬编码的 computed_hash 值随列删除失效（迁移按序执行，V3/V6 时列仍存在，INSERT 合法）。
ALTER TABLE oc_skill DROP COLUMN IF EXISTS computed_hash;
DROP INDEX IF EXISTS idx_skill_hash;
