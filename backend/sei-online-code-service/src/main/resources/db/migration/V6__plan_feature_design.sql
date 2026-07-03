-- Phase 6: 编码前交互（规划书 + 功能设计）。契约 §8/§10、修订记录 v3。
-- String-UUID 主键（IdGenerator.nextIdStr，36 位），审计字段由 BaseAuditableEntity 提供。
-- 版本历史：单表多行 + is_latest（D15）；content=TEXT（非 JSONB，与 SpecPageListConverter 一致）。
-- partial unique index WHERE is_latest=TRUE（PG）。
-- 新增内置 agent：planning-agent / feature-design-agent / dev-agent（D3，dev-agent 修复 DispatchService 悬空引用）。
-- 新增 LOCAL skill：project-planning / feature-design（pointer stub，同 V3 suid/eadp-backend）。
-- skill computed_hash 由 SkillHasher §6 recipe 计算（length-prefixed sha256 over v1|source|name|description|content）。

-- ============================ oc_plan ============================
CREATE TABLE oc_plan (
    id                  VARCHAR(36)  NOT NULL,
    project_id          VARCHAR(36)  NOT NULL,
    version             INT          NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    content             TEXT,
    modify_hint         TEXT,
    is_latest           BOOLEAN      NOT NULL DEFAULT TRUE,
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_plan PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_plan_proj_ver    ON oc_plan (project_id, version);
CREATE UNIQUE INDEX uk_plan_proj_latest ON oc_plan (project_id) WHERE is_latest = TRUE;
CREATE INDEX idx_plan_project           ON oc_plan (project_id);

-- ============================ oc_feature_design ============================
CREATE TABLE oc_feature_design (
    id                  VARCHAR(36)  NOT NULL,
    project_id          VARCHAR(36)  NOT NULL,
    feature_id          VARCHAR(128) NOT NULL,
    version             INT          NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    build_status        VARCHAR(32)  NOT NULL DEFAULT 'IDLE',
    content             TEXT,
    modify_hint         TEXT,
    is_latest           BOOLEAN      NOT NULL DEFAULT TRUE,
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_feature_design PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_fd_proj_feat_ver    ON oc_feature_design (project_id, feature_id, version);
CREATE UNIQUE INDEX uk_fd_proj_feat_latest ON oc_feature_design (project_id, feature_id) WHERE is_latest = TRUE;
CREATE INDEX idx_fd_project                ON oc_feature_design (project_id);

-- ============================ oc_task 新增列（D8）============================
-- Task.java 现无 feature_design_id 字段；FeatureDesignBuildService 创建 Task 时回填，建立 Run→Task→FeatureDesign 关联。
ALTER TABLE oc_task ADD COLUMN feature_design_id VARCHAR(36);
CREATE INDEX idx_task_feature_design ON oc_task (feature_design_id);

-- ============================ 种子：LOCAL 内置技能（契约 §10）============================
INSERT INTO oc_skill (id, name, description, source, source_type, content, computed_hash, created_date) VALUES
('SKILL_SEED_PROJECT_PLANNING_001', 'project-planning',
 '规划书生成 skill', 'local:project-planning', 'LOCAL',
 '# project-planning Skill (pointer stub)

完整技能位于操作机 ~/.claude/skills/project-planning。此为 LOCAL 指针占位。
强制输出 Plan JSON 骨架：summary/techAssumptions/features[featureId,title,outline]/nonGoals。
',
 'sha256:b2fa49f3c4764816a2f1e69317ee22f2b6a3e12fe944245103bbd51559d3d1ce', CURRENT_TIMESTAMP),
('SKILL_SEED_FEATURE_DESIGN_001', 'feature-design',
 '功能设计生成 skill', 'local:feature-design', 'LOCAL',
 '# feature-design Skill (pointer stub)

完整技能位于操作机 ~/.claude/skills/feature-design。此为 LOCAL 指针占位。
从 outline 展开 goal/design/acceptance[]/fileScope，骨架固定，粒度自定。
',
 'sha256:4b435d2d895a1805090ca29b25ae30d473ba5774399f2ce29019f2db6fd87fb6', CURRENT_TIMESTAMP);

-- ============================ 种子：内置 agent（契约 §10，builtin=true 不可删除）============
INSERT INTO oc_agent (id, name, description, instructions, model, builtin, skill_ids, created_date) VALUES
('AGENT_SEED_PLANNING_001', 'planning-agent',
 '内置规划 agent：项目描述 → 规划书 JSON',
 'You produce a project Plan JSON from the project description. Use the project-planning skill.', '', TRUE, '["SKILL_SEED_PROJECT_PLANNING_001"]', CURRENT_TIMESTAMP),
('AGENT_SEED_FEATURE_DESIGN_001', 'feature-design-agent',
 '内置功能设计 agent：规划书 + feature outline → 功能设计 JSON',
 'You produce one FeatureDesign JSON from the plan and a feature outline. Use the feature-design skill.', '', TRUE, '["SKILL_SEED_FEATURE_DESIGN_001"]', CURRENT_TIMESTAMP),
('AGENT_SEED_DEV_AGENT_001', 'dev-agent',
 '内置编码执行 agent：按功能设计 fileScope 执行编码（写代码），不产设计 JSON',
 'You implement code for one confirmed FeatureDesign. Write files within the fileScope. Do not produce design JSON.', '', TRUE, NULL, CURRENT_TIMESTAMP);
