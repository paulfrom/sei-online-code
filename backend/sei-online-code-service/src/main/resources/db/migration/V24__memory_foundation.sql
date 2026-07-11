-- V24: Workspace Memory 基础设施。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8、§9、§21。
-- 新增四张表（oc_memory_seed_template / oc_workspace_memory / oc_requirement_design_context / oc_memory_job），
-- 为 oc_project 增加 memory_seed_template_id，
-- 为 oc_requirement / oc_overview_design / oc_detailed_design 增加 design_context_id / memory_validation_status / memory_validation_result_json。
-- 主键 String-UUID(36)，审计字段由 BaseAuditableEntity 提供。
-- 每个 project_id 仅一个 CURRENT WorkspaceMemory、每个 requirement_id 仅一个 CURRENT RequirementDesignContext，
-- 由 PostgreSQL partial unique index 兜底（仅约束 status='CURRENT' 的行，不限制多个 ARCHIVED/FAILED），
-- 并由 service 在同一事务中归档旧 CURRENT 后写入新 CURRENT（§21.10 注意事项）。
-- 全局仅一个 ACTIVE + is_default=true 的 seed 模板，由 partial unique index 兜底，由 service 切换事务维护（§6.1.0）。

-- ============================ oc_memory_seed_template ============================
CREATE TABLE IF NOT EXISTS oc_memory_seed_template (
    id                        VARCHAR(36)  NOT NULL,
    code                      VARCHAR(128) NOT NULL,
    name                      VARCHAR(200) NOT NULL,
    description               VARCHAR(1000),
    version                   INTEGER      NOT NULL,
    status                    VARCHAR(32)  NOT NULL,
    is_default                BOOLEAN      NOT NULL DEFAULT FALSE,
    source_type               VARCHAR(32)  NOT NULL,
    project_memory_template   TEXT,
    memory_rules_template     TEXT,
    decisions_template        TEXT,
    modules_template          TEXT,
    published_at              TIMESTAMP,
    archived_at               TIMESTAMP,
    creator_id                VARCHAR(36),
    creator_account           VARCHAR(100),
    creator_name              VARCHAR(100),
    created_date              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_editor_id            VARCHAR(36),
    last_editor_account       VARCHAR(100),
    last_editor_name          VARCHAR(100),
    last_edited_date          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oc_memory_seed_template PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_memory_seed_template_code ON oc_memory_seed_template (code);
CREATE INDEX IF NOT EXISTS idx_memory_seed_template_status ON oc_memory_seed_template (status);
CREATE INDEX IF NOT EXISTS idx_memory_seed_template_default ON oc_memory_seed_template (is_default, status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_seed_template_code_version ON oc_memory_seed_template (code, version);
-- 全局仅一个 ACTIVE + is_default=true 默认模板（§6.1.0 / §21.7）
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_seed_template_active_default
    ON oc_memory_seed_template (is_default) WHERE is_default = TRUE AND status = 'ACTIVE';

-- ============================ oc_workspace_memory ============================
CREATE TABLE IF NOT EXISTS oc_workspace_memory (
    id                          VARCHAR(36)  NOT NULL,
    project_id                  VARCHAR(36)  NOT NULL,
    version                     INTEGER      NOT NULL,
    status                      VARCHAR(32)  NOT NULL,
    freshness                   VARCHAR(48)  NOT NULL DEFAULT 'FRESH',
    memory_spec_version         INTEGER      NOT NULL DEFAULT 1,
    memory_seed_template_id     VARCHAR(36),
    agent_memory_seed_version   INTEGER,
    workspace_path              VARCHAR(500),
    agent_memory_fingerprint    VARCHAR(128),
    agent_memory_markdown       TEXT,
    project_rule_fingerprint    VARCHAR(128),
    project_rule_markdown       TEXT,
    source_fingerprints_json    TEXT,
    norm_claims_json            TEXT,
    reality_claims_json         TEXT,
    conflict_findings_json      TEXT,
    workspace_norms_json        TEXT,
    workspace_snapshot_json     TEXT,
    failure_summary             TEXT,
    failure_detail              TEXT,
    generated_at                TIMESTAMP,
    creator_id                  VARCHAR(36),
    creator_account             VARCHAR(100),
    creator_name                VARCHAR(100),
    created_date                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_editor_id              VARCHAR(36),
    last_editor_account         VARCHAR(100),
    last_editor_name            VARCHAR(100),
    last_edited_date            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oc_workspace_memory PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_workspace_memory_project ON oc_workspace_memory (project_id);
CREATE INDEX IF NOT EXISTS idx_workspace_memory_project_status ON oc_workspace_memory (project_id, status);
-- 每个 project_id 仅一个 CURRENT（§21.10）
CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_memory_current
    ON oc_workspace_memory (project_id) WHERE status = 'CURRENT';

-- ============================ oc_requirement_design_context ============================
CREATE TABLE IF NOT EXISTS oc_requirement_design_context (
    id                                    VARCHAR(36)  NOT NULL,
    project_id                            VARCHAR(36)  NOT NULL,
    requirement_id                        VARCHAR(36)  NOT NULL,
    workspace_memory_id                   VARCHAR(36),
    version                               INTEGER      NOT NULL DEFAULT 1,
    status                                VARCHAR(32)  NOT NULL,
    context_status                        VARCHAR(32)  NOT NULL,
    requirement_fingerprint               VARCHAR(128),
    requirement_related_snapshot_json     TEXT,
    requirement_conflict_report_json     TEXT,
    design_basis                          TEXT,
    validation_result_json                TEXT,
    source_fingerprints_json              TEXT,
    failure_summary                       TEXT,
    failure_detail                        TEXT,
    generated_at                          TIMESTAMP,
    creator_id                            VARCHAR(36),
    creator_account                       VARCHAR(100),
    creator_name                          VARCHAR(100),
    created_date                          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_editor_id                        VARCHAR(36),
    last_editor_account                   VARCHAR(100),
    last_editor_name                      VARCHAR(100),
    last_edited_date                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oc_requirement_design_context PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_req_design_ctx_project ON oc_requirement_design_context (project_id);
CREATE INDEX IF NOT EXISTS idx_req_design_ctx_requirement ON oc_requirement_design_context (requirement_id);
CREATE INDEX IF NOT EXISTS idx_req_design_ctx_requirement_status ON oc_requirement_design_context (requirement_id, status);
-- 每个 requirement_id 仅一个 CURRENT（§21.10）
CREATE UNIQUE INDEX IF NOT EXISTS uk_req_design_ctx_current
    ON oc_requirement_design_context (requirement_id) WHERE status = 'CURRENT';

-- ============================ oc_memory_job ============================
CREATE TABLE IF NOT EXISTS oc_memory_job (
    id                              VARCHAR(36)  NOT NULL,
    project_id                      VARCHAR(36)  NOT NULL,
    requirement_id                  VARCHAR(36),
    coding_task_id                  VARCHAR(36),
    run_id                          VARCHAR(36),
    job_type                        VARCHAR(48)  NOT NULL,
    status                          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    trigger_source                  VARCHAR(32)  NOT NULL,
    previous_workspace_memory_id    VARCHAR(36),
    new_workspace_memory_id         VARCHAR(36),
    base_workspace_memory_id        VARCHAR(36),
    idempotency_key                 VARCHAR(200) NOT NULL,
    priority                        INTEGER      NOT NULL DEFAULT 0,
    retry_count                     INTEGER      NOT NULL DEFAULT 0,
    max_retry_count                 INTEGER      NOT NULL DEFAULT 3,
    next_retry_at                   TIMESTAMP,
    started_at                      TIMESTAMP,
    finished_at                     TIMESTAMP,
    failure_summary                 TEXT,
    failure_detail                  TEXT,
    creator_id                      VARCHAR(36),
    creator_account                 VARCHAR(100),
    creator_name                    VARCHAR(100),
    created_date                    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_editor_id                  VARCHAR(36),
    last_editor_account             VARCHAR(100),
    last_editor_name                VARCHAR(100),
    last_edited_date                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oc_memory_job PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_memory_job_project_status ON oc_memory_job (project_id, status);
CREATE INDEX IF NOT EXISTS idx_memory_job_next_retry ON oc_memory_job (status, next_retry_at);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_job_idempotency ON oc_memory_job (idempotency_key);
-- 多实例调度时由数据库保证同一 project 最多一个 RUNNING job，消除跨节点 write-skew。
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_job_running_project
    ON oc_memory_job (project_id) WHERE status = 'RUNNING';

-- ============================ 现有表加列 ============================

-- CodingTask 开始前 Git HEAD，供成功后的增量记忆回写覆盖已提交变更。
ALTER TABLE oc_run ADD COLUMN IF NOT EXISTS base_commit VARCHAR(64);

-- 9.1 Project 增加 memory_seed_template_id
ALTER TABLE oc_project
    ADD COLUMN IF NOT EXISTS memory_seed_template_id VARCHAR(36);

-- 9.2 Requirement 增加设计上下文与记忆校验字段
ALTER TABLE oc_requirement
    ADD COLUMN IF NOT EXISTS design_context_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS memory_validation_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS memory_validation_result_json TEXT;

-- 9.3 OverviewDesign 增加设计上下文与记忆校验字段
ALTER TABLE oc_overview_design
    ADD COLUMN IF NOT EXISTS design_context_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS memory_validation_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS memory_validation_result_json TEXT;

-- 9.4 DetailedDesign 增加设计上下文与记忆校验字段
ALTER TABLE oc_detailed_design
    ADD COLUMN IF NOT EXISTS design_context_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS memory_validation_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS memory_validation_result_json TEXT;
