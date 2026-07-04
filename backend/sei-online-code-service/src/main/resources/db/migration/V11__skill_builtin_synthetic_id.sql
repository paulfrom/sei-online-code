-- Phase 8: 内置技能资源化（multica 维度 g）。
-- 内置技能（suid/eadp-backend/project-planning/feature-design）不再以 oc_skill 行存在，
-- 改 vendor 到 classpath skills/<name>/，由 BuiltInSkillRegistry 经 builtin:<name> synthetic id 加载。
-- agent 经 oc_agent_skill 绑定 builtin:<name>；join 表 skill_id 不加 FK（V7 预留），故可直接改值。
--
-- 顺序：先 UPDATE join（把 V7 迁移出的 planning/feature-design 绑定改为 builtin: 值），再 DELETE oc_skill 种子行。
-- oc_skill_file.skill_id 有 FK CASCADE，但内置 4 行无辅助文件 → 级联空操作。
-- suid/eadp-backend 当前无 agent 绑定（V3 内置 agent skill_ids 均为 NULL），仅 DELETE。

-- ============================ join 表绑定改为 builtin:<name> synthetic id ============================
UPDATE oc_agent_skill SET skill_id = 'builtin:project-planning'
    WHERE skill_id = 'SKILL_SEED_PROJECT_PLANNING_001';
UPDATE oc_agent_skill SET skill_id = 'builtin:feature-design'
    WHERE skill_id = 'SKILL_SEED_FEATURE_DESIGN_001';

-- ============================ 删除 oc_skill 内置种子行 ============================
DELETE FROM oc_skill WHERE id IN (
    'SKILLSEEDSUID0000000000000000000001',
    'SKILLSEEDEADPBACKEND00000000000001',
    'SKILL_SEED_PROJECT_PLANNING_001',
    'SKILL_SEED_FEATURE_DESIGN_001');
