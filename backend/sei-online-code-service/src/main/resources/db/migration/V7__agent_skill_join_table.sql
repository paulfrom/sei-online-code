-- Phase 7: Agent↔Skill 关联模型迁移——oc_agent.skill_ids JSON 列 → 独立 join 表 oc_agent_skill
-- 对齐 multica 维度 a。决策：skill_id 不加 FK（为 Phase 6 内置技能 builtin:<name> synthetic id 预留）；
-- agent_id 保留 FK + ON DELETE CASCADE。先迁移 V6 种子 agent 的 JSON 绑定为 join 行，再删 skill_ids 列。

-- ============================ oc_agent_skill ============================
CREATE TABLE oc_agent_skill (
    id                  VARCHAR(36)  NOT NULL,
    agent_id            VARCHAR(36)  NOT NULL,
    skill_id            VARCHAR(36)  NOT NULL,
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_agent_skill PRIMARY KEY (id)
);
-- (agent_id, skill_id) 唯一；其前缀已覆盖 agent_id 维度查询，无需另建 idx_agent_skill_agent
CREATE UNIQUE INDEX uk_agent_skill      ON oc_agent_skill (agent_id, skill_id);
CREATE INDEX        idx_agent_skill_skill ON oc_agent_skill (skill_id);
ALTER TABLE oc_agent_skill
    ADD CONSTRAINT fk_agent_skill_agent FOREIGN KEY (agent_id)
    REFERENCES oc_agent (id) ON DELETE CASCADE;

-- ============================ 迁移 V6 种子 agent 的 skill_ids JSON 绑定 ============================
-- planning-agent 原 skill_ids='["SKILL_SEED_PROJECT_PLANNING_001"]' → join 行
INSERT INTO oc_agent_skill (id, agent_id, skill_id, created_date)
SELECT 'AGENT_SKILL_PLANNING_001', a.id, 'SKILL_SEED_PROJECT_PLANNING_001', CURRENT_TIMESTAMP
FROM oc_agent a
WHERE a.name = 'planning-agent'
  AND NOT EXISTS (SELECT 1 FROM oc_agent_skill x WHERE x.agent_id = a.id AND x.skill_id = 'SKILL_SEED_PROJECT_PLANNING_001');

-- feature-design-agent 原 skill_ids='["SKILL_SEED_FEATURE_DESIGN_001"]' → join 行
INSERT INTO oc_agent_skill (id, agent_id, skill_id, created_date)
SELECT 'AGENT_SKILL_FEATDESIGN_001', a.id, 'SKILL_SEED_FEATURE_DESIGN_001', CURRENT_TIMESTAMP
FROM oc_agent a
WHERE a.name = 'feature-design-agent'
  AND NOT EXISTS (SELECT 1 FROM oc_agent_skill x WHERE x.agent_id = a.id AND x.skill_id = 'SKILL_SEED_FEATURE_DESIGN_001');

-- ============================ 删除 oc_agent.skill_ids JSON 列 ============================
ALTER TABLE oc_agent DROP COLUMN IF EXISTS skill_ids;
