-- Phase 3: skill / agent (PostgreSQL). 契约 Phase 3 §1、§4。
-- String-UUID 主键（IdGenerator.nextIdStr，36 位），审计字段由 BaseAuditableEntity 提供。
--
-- Agent↔Skill 关联模型决策：采用 oc_agent.skill_ids JSON（TEXT）列，而非独立 join 表。
--   理由：本阶段绑定关系简单（一个 agent 持有一组 skillId），无关联属性、无跨 agent 反查需求；
--   JSON 列避免多一张表与额外 DAO，前端也只感知 skillIds[]（契约 §1.2）。
--   若未来出现关联元数据或反查需求，再迁移为 join 表。
--
-- computed_hash 为技能内容锁：sha256 over length-prefixed (v1|source|name|description|content)（§6）。
-- 下方种子 hash 与 SkillHasher 的 §6 recipe 对齐，故对相同内容的再次导入按 hash 幂等。

-- ============================ oc_skill ============================
CREATE TABLE oc_skill (
    id                  VARCHAR(36)  NOT NULL,
    name                VARCHAR(64)  NOT NULL,
    description         VARCHAR(500),
    source              VARCHAR(500),
    source_type         VARCHAR(20)  NOT NULL,
    content             TEXT,
    computed_hash       VARCHAR(80)  NOT NULL,
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_skill PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_skill_name ON oc_skill (name);
CREATE INDEX idx_skill_hash ON oc_skill (computed_hash);

-- ============================ oc_agent ============================
CREATE TABLE oc_agent (
    id                  VARCHAR(36)  NOT NULL,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(500),
    instructions        TEXT,
    model               VARCHAR(100),
    builtin             BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 绑定技能 id 列表，以 JSON（TEXT）列持久化（见文件头关联模型决策）
    skill_ids           TEXT,
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_agent PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_agent_name ON oc_agent (name);

-- ============================ 种子：LOCAL 内置技能（契约 §4）============================
-- suid + eadp-backend 作为 LOCAL 指针占位（完整技能在操作机 ~/.claude/skills/<name>），
-- content 为短指针 stub，不把整包 vendored 进 DB。computed_hash 与 §6 recipe 对齐。
INSERT INTO oc_skill (id, name, description, source, source_type, content, computed_hash, created_date) VALUES
('SKILLSEEDSUID0000000000000000000001', 'suid',
 '@ead/suid component library skill', 'local:suid', 'LOCAL',
 '# suid Skill (pointer stub)

完整技能位于操作机 ~/.claude/skills/suid。此为 LOCAL 指针占位。
',
 'sha256:f1d2ff649cd606bd0701d6c6e9594ef50bf5333af0de51803eeb51038b13a48f', CURRENT_TIMESTAMP),
('SKILLSEEDEADPBACKEND00000000000001', 'eadp-backend',
 'EADP/SEI Java Spring Boot backend skill', 'local:eadp-backend', 'LOCAL',
 '# eadp-backend Skill (pointer stub)

完整技能位于操作机 ~/.claude/skills/eadp-backend。此为 LOCAL 指针占位。
',
 'sha256:5a2491b4025fb95ff1daa618f40c5707783266b35a028d8e6a6b30e531d41b01', CURRENT_TIMESTAMP);

-- ============================ 种子：内置 agent（契约 §4，builtin=true 不可删除）============
INSERT INTO oc_agent (id, name, description, instructions, model, builtin, skill_ids, created_date) VALUES
('AGENTSEEDREQUIREMENT0000000000001', 'requirement-agent',
 '内置需求 agent：将 Project Design 精炼为 Spec',
 'You refine the project design into a structured Spec.', '', TRUE, NULL, CURRENT_TIMESTAMP),
('AGENTSEEDDISPATCH000000000000001', 'dispatch-agent',
 '内置派发 agent：将确认后的 Spec 切分为非重叠 Task 并 fan-out',
 'You split the confirmed Spec into non-overlapping tasks and fan out execution.', '', TRUE, NULL, CURRENT_TIMESTAMP),
('AGENTSEEDDEPLOY00000000000000001', 'deploy-agent',
 '内置部署 agent：合并后构建并发布预览',
 'You build and publish the preview after merge.', '', TRUE, NULL, CURRENT_TIMESTAMP);
