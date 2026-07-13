-- V28: PRD comment driven agent loop.

CREATE TABLE IF NOT EXISTS oc_requirement_comment (
    id                  VARCHAR(36)  NOT NULL,
    requirement_id      VARCHAR(36)  NOT NULL,
    loop_id             VARCHAR(64),
    author_type         VARCHAR(32)  NOT NULL,
    author_name         VARCHAR(100),
    comment_type        VARCHAR(64)  NOT NULL,
    content             TEXT,
    metadata_json       TEXT,
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_requirement_comment PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_req_comment_requirement ON oc_requirement_comment (requirement_id);
CREATE INDEX IF NOT EXISTS idx_req_comment_loop ON oc_requirement_comment (loop_id);
CREATE INDEX IF NOT EXISTS idx_req_comment_type ON oc_requirement_comment (comment_type);

CREATE TABLE IF NOT EXISTS oc_execution_plan (
    id                  VARCHAR(36)  NOT NULL,
    requirement_id      VARCHAR(36)  NOT NULL,
    loop_id             VARCHAR(64)  NOT NULL,
    version             INTEGER      NOT NULL,
    plan_type           VARCHAR(32)  NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    plan_json           TEXT,
    summary             TEXT,
    created_by_agent    VARCHAR(100),
    memory_context_id   VARCHAR(36),
    workspace_memory_id VARCHAR(36),
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_execution_plan PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_execution_plan_requirement ON oc_execution_plan (requirement_id);
CREATE INDEX IF NOT EXISTS idx_execution_plan_loop ON oc_execution_plan (loop_id);
CREATE INDEX IF NOT EXISTS idx_execution_plan_status ON oc_execution_plan (status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_execution_plan_requirement_version
    ON oc_execution_plan (requirement_id, version);

ALTER TABLE oc_requirement
    ADD COLUMN IF NOT EXISTS automation_status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS accepted_by_agent VARCHAR(100),
    ADD COLUMN IF NOT EXISTS delivery_branch VARCHAR(200),
    ADD COLUMN IF NOT EXISTS delivery_commit_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS delivery_mr_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS delivery_target_branch VARCHAR(200);
CREATE INDEX IF NOT EXISTS idx_requirement_automation_status ON oc_requirement (automation_status);

ALTER TABLE oc_run
    ADD COLUMN IF NOT EXISTS requirement_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS loop_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS invalidated_by_comment_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS memory_context_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS workspace_memory_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_run_requirement ON oc_run (requirement_id);
CREATE INDEX IF NOT EXISTS idx_run_loop ON oc_run (loop_id);
CREATE INDEX IF NOT EXISTS idx_run_type ON oc_run (run_type);

ALTER TABLE oc_platform_config
    ADD COLUMN IF NOT EXISTS gitlab_api_base_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS gitlab_token VARCHAR(500),
    ADD COLUMN IF NOT EXISTS gitlab_project_id VARCHAR(300),
    ADD COLUMN IF NOT EXISTS gitlab_target_branch VARCHAR(200);

INSERT INTO oc_agent (id, name, description, instructions, model, builtin, created_date)
SELECT 'AGENTSEEDPM00000000000000000000001', 'pm-agent',
       '内置 PM agent：创建执行计划、验收结果并生成补救任务',
       'You create execution plans from PRDs, review validation facts, and decide acceptance or remediation.',
       '', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM oc_agent WHERE name = 'pm-agent');

INSERT INTO oc_agent (id, name, description, instructions, model, builtin, created_date)
SELECT 'AGENTSEEDFRONTENDDEV000000000001', 'frontend-dev-agent',
       '内置前端开发 agent：按 PM 执行计划实现前端任务',
       'You implement frontend tasks using the repository frontend conventions and the builtin:suid skill.',
       '', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM oc_agent WHERE name = 'frontend-dev-agent');

INSERT INTO oc_agent (id, name, description, instructions, model, builtin, created_date)
SELECT 'AGENTSEEDBACKENDDEV0000000000001', 'backend-dev-agent',
       '内置后端开发 agent：按 PM 执行计划实现后端任务',
       'You implement backend tasks using the repository backend conventions and the builtin:eadp-backend skill.',
       '', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM oc_agent WHERE name = 'backend-dev-agent');

INSERT INTO oc_agent (id, name, description, instructions, model, builtin, created_date)
SELECT 'AGENTSEEDTEST0000000000000000001', 'test-agent',
       '内置测试 agent：解释验证命令结果并形成验证报告',
       'You interpret validation command facts and write concise task-level or plan-level validation reports.',
       '', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM oc_agent WHERE name = 'test-agent');

INSERT INTO oc_agent_skill (id, agent_id, skill_id, created_date)
SELECT 'AGENT_SKILL_FRONTEND_DEV_SUID', a.id, 'builtin:suid', CURRENT_TIMESTAMP
FROM oc_agent a
WHERE a.name = 'frontend-dev-agent'
  AND NOT EXISTS (SELECT 1 FROM oc_agent_skill s WHERE s.agent_id = a.id AND s.skill_id = 'builtin:suid');

INSERT INTO oc_agent_skill (id, agent_id, skill_id, created_date)
SELECT 'AGENT_SKILL_BACKEND_DEV_EADP', a.id, 'builtin:eadp-backend', CURRENT_TIMESTAMP
FROM oc_agent a
WHERE a.name = 'backend-dev-agent'
  AND NOT EXISTS (SELECT 1 FROM oc_agent_skill s WHERE s.agent_id = a.id AND s.skill_id = 'builtin:eadp-backend');
