-- Phase 5: 技能辅助文件子表（multica 维度 e，兑现契约 Phase 3 §1.1 deferred 的 per-file FileRef[]）。
-- 每行 = .claude/skills/<skill.name>/<path> 下的一个文件，由 SkillMaterializer 随 SKILL.md 一并写入 worktree。
-- 决策：skill_id 加 FK + ON DELETE CASCADE（真实 oc_skill 子行；与 oc_agent_skill.skill_id 为 builtin: synthetic id
--   预留而不加 FK 不同）；(skill_id, path) 唯一避免同技能重复路径。无 seed——现有 4 行技能无辅助文件；
--   内置技能 references/** 留 PR6 vendor 时填。

-- ============================ oc_skill_file ============================
CREATE TABLE oc_skill_file (
    id                  VARCHAR(36)  NOT NULL,
    skill_id            VARCHAR(36)  NOT NULL,
    path                VARCHAR(500) NOT NULL,
    content             TEXT,
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_skill_file PRIMARY KEY (id)
);
-- (skill_id, path) 唯一；其前缀已覆盖 skill_id 维度查询，无需另建 idx_skill_file_agent 风格索引
CREATE UNIQUE INDEX uk_skill_file      ON oc_skill_file (skill_id, path);
CREATE INDEX        idx_skill_file_skill ON oc_skill_file (skill_id);
ALTER TABLE oc_skill_file
    ADD CONSTRAINT fk_skill_file_skill FOREIGN KEY (skill_id)
    REFERENCES oc_skill (id) ON DELETE CASCADE;
