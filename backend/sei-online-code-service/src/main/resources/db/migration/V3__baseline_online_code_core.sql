-- Current core baseline for sei-online-code entities that predate the progress ledger.
--
-- Why this exists:
-- - V7/V8 create and extend the progress ledger, but a fresh database also needs the
--   business tables referenced by current JPA entities: project/spec/task/run,
--   requirement, plan, coding task, skill/agent, config, compensation and memory tables.
-- - The historical branch that contains those tables has conflicting V7/V8 numbers, so
--   this consolidated baseline is intentionally versioned before the progress ledger.
-- - Progress-ledger tables remain owned by V7/V8.

-- Shared columns for all BaseAuditableEntity tables:
-- id, creator_id/account/name, created_date, last_editor_id/account/name, last_edited_date.

CREATE TABLE IF NOT EXISTS oc_project (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    name                     VARCHAR(200) NOT NULL,
    design                   TEXT,
    state                    VARCHAR(20) NOT NULL,
    current_spec_id          VARCHAR(36),
    git_url                  VARCHAR(500),
    project_code             VARCHAR(100),
    project_version          VARCHAR(50),
    package_name             VARCHAR(200),
    workspace_path           VARCHAR(500),
    validation_config        TEXT,
    memory_seed_template_id  VARCHAR(36)
);
CREATE INDEX IF NOT EXISTS idx_project_state ON oc_project (state);
CREATE INDEX IF NOT EXISTS idx_project_workspace_path ON oc_project (workspace_path);

CREATE TABLE IF NOT EXISTS oc_spec (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    project_id               VARCHAR(36) NOT NULL,
    version                  INTEGER NOT NULL,
    state                    VARCHAR(20) NOT NULL,
    module_id                VARCHAR(128),
    module_title             VARCHAR(200),
    module_summary           TEXT,
    pages                    TEXT,
    components               TEXT,
    entities                 TEXT,
    api_contract             TEXT,
    modify_hint              TEXT,
    failure_code             VARCHAR(64),
    failure_stage            VARCHAR(32),
    failure_summary          TEXT,
    failure_detail           TEXT,
    last_failed_at           TIMESTAMP,
    last_retry_at            TIMESTAMP,
    retry_count              INTEGER NOT NULL DEFAULT 0,
    next_retry_at            TIMESTAMP,
    last_trigger_source      VARCHAR(32)
);
CREATE INDEX IF NOT EXISTS idx_spec_project ON oc_spec (project_id);
CREATE INDEX IF NOT EXISTS idx_spec_state ON oc_spec (state);
CREATE INDEX IF NOT EXISTS idx_spec_module ON oc_spec (project_id, module_id);

CREATE TABLE IF NOT EXISTS oc_task (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    feature_design_id        VARCHAR(36),
    title                    VARCHAR(200),
    description              TEXT,
    file_scope               TEXT,
    assigned_agent           VARCHAR(100),
    state                    VARCHAR(20) NOT NULL,
    worktree_branch          VARCHAR(200),
    seq                      INTEGER
);
CREATE INDEX IF NOT EXISTS idx_task_feature_design ON oc_task (feature_design_id);

CREATE TABLE IF NOT EXISTS oc_run (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    task_id                  VARCHAR(36),
    coding_task_id           VARCHAR(36),
    requirement_id           VARCHAR(36),
    run_no                   INTEGER,
    run_type                 VARCHAR(20) NOT NULL,
    parent_run_id            VARCHAR(36),
    compensates_run_id       VARCHAR(36),
    attempt_no               INTEGER,
    trigger_source           VARCHAR(32),
    loop_id                  VARCHAR(64),
    cancel_requested         BOOLEAN NOT NULL DEFAULT FALSE,
    invalidated_by_comment_id VARCHAR(36),
    memory_context_id        VARCHAR(36),
    workspace_memory_id      VARCHAR(36),
    user_prompt              TEXT,
    summary                  TEXT,
    failure_reason           TEXT,
    terminal_reason          VARCHAR(32),
    log_stream_key           VARCHAR(36),
    state                    VARCHAR(20) NOT NULL,
    worktree_path            VARCHAR(500),
    base_commit              VARCHAR(64),
    agent_id                 VARCHAR(36),
    agent_name               VARCHAR(100),
    cli_tool                 VARCHAR(32),
    model                    VARCHAR(100),
    input_tokens             BIGINT,
    output_tokens            BIGINT,
    cache_read_tokens        BIGINT,
    cache_write_tokens       BIGINT,
    total_tokens             BIGINT,
    usage_status             VARCHAR(20) NOT NULL DEFAULT 'UNAVAILABLE',
    raw_usage_json           TEXT,
    exit_code                INTEGER,
    started_date             TIMESTAMP,
    finished_date            TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_run_log_stream ON oc_run (log_stream_key);
CREATE INDEX IF NOT EXISTS idx_run_task ON oc_run (task_id);
CREATE INDEX IF NOT EXISTS idx_run_coding_task ON oc_run (coding_task_id);

CREATE TABLE IF NOT EXISTS oc_skill (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    name                     VARCHAR(64) NOT NULL,
    description              VARCHAR(500),
    config                   TEXT,
    content                  TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_skill_name ON oc_skill (name);

CREATE TABLE IF NOT EXISTS oc_skill_file (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    skill_id                 VARCHAR(36) NOT NULL,
    path                     VARCHAR(500) NOT NULL,
    content                  TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_skill_file ON oc_skill_file (skill_id, path);
CREATE INDEX IF NOT EXISTS idx_skill_file_skill ON oc_skill_file (skill_id);

CREATE TABLE IF NOT EXISTS oc_agent (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    name                     VARCHAR(100) NOT NULL,
    description              VARCHAR(500),
    instructions             TEXT,
    prompt_template          TEXT,
    execution_policy         TEXT,
    scope_policy             TEXT,
    output_schema            TEXT,
    model                    VARCHAR(100),
    cli_tool                 VARCHAR(50),
    mcp_config               TEXT,
    builtin                  BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_name ON oc_agent (name);

CREATE TABLE IF NOT EXISTS oc_agent_skill (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    agent_id                 VARCHAR(36) NOT NULL,
    skill_id                 VARCHAR(36) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_skill ON oc_agent_skill (agent_id, skill_id);
CREATE INDEX IF NOT EXISTS idx_agent_skill_skill ON oc_agent_skill (skill_id);

CREATE TABLE IF NOT EXISTS oc_platform_config (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    workspace_root           VARCHAR(500),
    template_gitlab_url      VARCHAR(500),
    gitlab_api_base_url      VARCHAR(500),
    gitlab_token             VARCHAR(500),
    gitlab_project_id        VARCHAR(200),
    gitlab_target_branch     VARCHAR(200)
);
INSERT INTO oc_platform_config (id, template_gitlab_url, created_date)
SELECT 'CONFIG', '', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM oc_platform_config WHERE id = 'CONFIG');

CREATE TABLE IF NOT EXISTS oc_plan (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    project_id               VARCHAR(36) NOT NULL,
    version                  INTEGER NOT NULL,
    status                   VARCHAR(32) NOT NULL,
    content                  TEXT,
    modify_hint              TEXT,
    is_latest                BOOLEAN NOT NULL DEFAULT TRUE,
    failure_code             VARCHAR(64),
    failure_stage            VARCHAR(32),
    failure_summary          TEXT,
    failure_detail           TEXT,
    last_failed_at           TIMESTAMP,
    last_retry_at            TIMESTAMP,
    retry_count              INTEGER NOT NULL DEFAULT 0,
    next_retry_at            TIMESTAMP,
    last_trigger_source      VARCHAR(32),
    generation_token         VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_plan_proj_ver ON oc_plan (project_id, version);
CREATE UNIQUE INDEX IF NOT EXISTS uk_plan_proj_latest ON oc_plan (project_id) WHERE is_latest = TRUE;
CREATE INDEX IF NOT EXISTS idx_plan_project ON oc_plan (project_id);

CREATE TABLE IF NOT EXISTS oc_feature_design (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    project_id               VARCHAR(36) NOT NULL,
    feature_id               VARCHAR(128) NOT NULL,
    version                  INTEGER NOT NULL,
    status                   VARCHAR(32) NOT NULL,
    build_status             VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    content                  TEXT,
    modify_hint              TEXT,
    is_latest                BOOLEAN NOT NULL DEFAULT TRUE,
    failure_code             VARCHAR(64),
    failure_stage            VARCHAR(32),
    failure_summary          TEXT,
    failure_detail           TEXT,
    last_failed_at           TIMESTAMP,
    last_retry_at            TIMESTAMP,
    retry_count              INTEGER NOT NULL DEFAULT 0,
    next_retry_at            TIMESTAMP,
    last_trigger_source      VARCHAR(32)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_fd_proj_feat_ver ON oc_feature_design (project_id, feature_id, version);
CREATE UNIQUE INDEX IF NOT EXISTS uk_fd_proj_feat_latest ON oc_feature_design (project_id, feature_id) WHERE is_latest = TRUE;
CREATE INDEX IF NOT EXISTS idx_fd_project ON oc_feature_design (project_id);

CREATE TABLE IF NOT EXISTS oc_coding_task (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    project_id               VARCHAR(36) NOT NULL,
    requirement_id           VARCHAR(36) NOT NULL,
    status                   VARCHAR(32) NOT NULL,
    title                    VARCHAR(200),
    description              TEXT,
    file_scope               TEXT,
    area                     VARCHAR(32),
    depends_on               TEXT,
    execution_plan_id        VARCHAR(36),
    plan_task_key            VARCHAR(64),
    assigned_agent           VARCHAR(64),
    loop_id                  VARCHAR(64),
    failure_summary          TEXT,
    failure_detail           TEXT,
    last_failed_at           TIMESTAMP,
    last_retry_at            TIMESTAMP,
    retry_count              INTEGER NOT NULL DEFAULT 0,
    next_retry_at            TIMESTAMP,
    last_trigger_source      VARCHAR(32)
);
CREATE INDEX IF NOT EXISTS idx_coding_task_project ON oc_coding_task (project_id);
CREATE INDEX IF NOT EXISTS idx_coding_task_requirement ON oc_coding_task (requirement_id);
CREATE INDEX IF NOT EXISTS idx_coding_task_status ON oc_coding_task (status);

CREATE TABLE IF NOT EXISTS oc_compensation_log (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    entity_type              VARCHAR(32) NOT NULL,
    entity_id                VARCHAR(36),
    action                   VARCHAR(64) NOT NULL,
    success                  BOOLEAN NOT NULL,
    message                  TEXT,
    detail                   TEXT,
    trigger_source           VARCHAR(32)
);
CREATE INDEX IF NOT EXISTS idx_comp_log_entity ON oc_compensation_log (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_comp_log_created ON oc_compensation_log (created_date);

CREATE TABLE IF NOT EXISTS oc_requirement (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    project_id               VARCHAR(36) NOT NULL,
    requirement_no           VARCHAR(32) NOT NULL,
    title                    VARCHAR(200) NOT NULL,
    description              TEXT,
    status                   VARCHAR(32) NOT NULL,
    prd_version              INTEGER NOT NULL DEFAULT 1,
    prd_content              TEXT,
    failure_code             VARCHAR(64),
    failure_stage            VARCHAR(32),
    failure_summary          TEXT,
    failure_detail           TEXT,
    last_failed_at           TIMESTAMP,
    last_retry_at            TIMESTAMP,
    retry_count              INTEGER NOT NULL DEFAULT 0,
    next_retry_at            TIMESTAMP,
    last_trigger_source      VARCHAR(32),
    generation_token         VARCHAR(64),
    automation_status        VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    active_loop_id           VARCHAR(64),
    accepted_at              TIMESTAMP,
    accepted_by_agent        VARCHAR(100),
    delivery_branch          VARCHAR(200),
    delivery_commit_hash     VARCHAR(64),
    delivery_mr_url          VARCHAR(500),
    delivery_target_branch   VARCHAR(200),
    design_context_id        VARCHAR(36),
    memory_validation_status VARCHAR(32),
    memory_validation_result_json TEXT
);
CREATE INDEX IF NOT EXISTS idx_requirement_project ON oc_requirement (project_id);
CREATE INDEX IF NOT EXISTS idx_requirement_status ON oc_requirement (status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_requirement_project_no ON oc_requirement (project_id, requirement_no);

CREATE TABLE IF NOT EXISTS oc_requirement_comment (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    requirement_id           VARCHAR(36) NOT NULL,
    loop_id                  VARCHAR(64),
    author_type              VARCHAR(32) NOT NULL,
    author_name              VARCHAR(100),
    comment_type             VARCHAR(32) NOT NULL,
    content                  TEXT,
    metadata_json            TEXT
);
CREATE INDEX IF NOT EXISTS idx_req_comment_requirement ON oc_requirement_comment (requirement_id);
CREATE INDEX IF NOT EXISTS idx_req_comment_loop ON oc_requirement_comment (loop_id);
CREATE INDEX IF NOT EXISTS idx_req_comment_created ON oc_requirement_comment (created_date);

CREATE TABLE IF NOT EXISTS oc_execution_plan (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    requirement_id           VARCHAR(36) NOT NULL,
    loop_id                  VARCHAR(64) NOT NULL,
    version                  INTEGER NOT NULL,
    plan_type                VARCHAR(32) NOT NULL,
    status                   VARCHAR(32) NOT NULL,
    plan_json                TEXT,
    summary                  TEXT,
    created_by_agent         VARCHAR(100),
    memory_context_id        VARCHAR(36),
    workspace_memory_id      VARCHAR(36)
);
CREATE INDEX IF NOT EXISTS idx_execution_plan_requirement ON oc_execution_plan (requirement_id);
CREATE INDEX IF NOT EXISTS idx_execution_plan_loop ON oc_execution_plan (loop_id);
CREATE INDEX IF NOT EXISTS idx_execution_plan_status ON oc_execution_plan (status);

CREATE TABLE IF NOT EXISTS oc_memory_seed_template (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    code                     VARCHAR(128) NOT NULL,
    name                     VARCHAR(200) NOT NULL,
    description              VARCHAR(1000),
    version                  INTEGER NOT NULL,
    status                   VARCHAR(32) NOT NULL,
    is_default               BOOLEAN NOT NULL DEFAULT FALSE,
    source_type              VARCHAR(32) NOT NULL,
    project_memory_template  TEXT,
    memory_rules_template    TEXT,
    decisions_template       TEXT,
    modules_template         TEXT,
    published_at             TIMESTAMP,
    archived_at              TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_memory_seed_template_code ON oc_memory_seed_template (code);
CREATE INDEX IF NOT EXISTS idx_memory_seed_template_status ON oc_memory_seed_template (status);
CREATE INDEX IF NOT EXISTS idx_memory_seed_template_default ON oc_memory_seed_template (is_default, status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_seed_template_code_version ON oc_memory_seed_template (code, version);

CREATE TABLE IF NOT EXISTS oc_workspace_memory (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    project_id               VARCHAR(36) NOT NULL,
    version                  INTEGER NOT NULL DEFAULT 1,
    status                   VARCHAR(32) NOT NULL,
    freshness                VARCHAR(48) NOT NULL DEFAULT 'FRESH',
    memory_spec_version      INTEGER NOT NULL DEFAULT 1,
    memory_seed_template_id  VARCHAR(36),
    agent_memory_seed_version INTEGER,
    workspace_path           VARCHAR(500),
    agent_memory_fingerprint VARCHAR(128),
    agent_memory_markdown    TEXT,
    project_rule_fingerprint VARCHAR(128),
    project_rule_markdown    TEXT,
    source_fingerprints_json TEXT,
    norm_claims_json         TEXT,
    reality_claims_json      TEXT,
    conflict_findings_json   TEXT,
    workspace_norms_json     TEXT,
    workspace_snapshot_json  TEXT,
    failure_summary          TEXT,
    failure_detail           TEXT,
    generated_at             TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_workspace_memory_project ON oc_workspace_memory (project_id);
CREATE INDEX IF NOT EXISTS idx_workspace_memory_project_status ON oc_workspace_memory (project_id, status);

CREATE TABLE IF NOT EXISTS oc_requirement_design_context (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    project_id               VARCHAR(36) NOT NULL,
    requirement_id           VARCHAR(36) NOT NULL,
    workspace_memory_id      VARCHAR(36) NOT NULL,
    version                  INTEGER NOT NULL,
    status                   VARCHAR(32) NOT NULL,
    context_status           VARCHAR(32) NOT NULL,
    requirement_fingerprint  VARCHAR(128),
    requirement_related_snapshot_json TEXT,
    requirement_conflict_report_json TEXT,
    design_basis             TEXT,
    validation_result_json   TEXT,
    source_fingerprints_json TEXT,
    failure_summary          TEXT,
    failure_detail           TEXT,
    generated_at             TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_req_design_context_requirement ON oc_requirement_design_context (requirement_id);
CREATE INDEX IF NOT EXISTS idx_req_design_context_project ON oc_requirement_design_context (project_id);
CREATE INDEX IF NOT EXISTS idx_req_design_context_workspace ON oc_requirement_design_context (workspace_memory_id);

CREATE TABLE IF NOT EXISTS oc_memory_job (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    project_id               VARCHAR(36) NOT NULL,
    requirement_id           VARCHAR(36),
    loop_id                  VARCHAR(64),
    coding_task_id           VARCHAR(36),
    run_id                   VARCHAR(36),
    job_type                 VARCHAR(48) NOT NULL,
    status                   VARCHAR(32) NOT NULL,
    trigger_source           VARCHAR(32) NOT NULL,
    previous_workspace_memory_id VARCHAR(36),
    new_workspace_memory_id  VARCHAR(36),
    base_workspace_memory_id VARCHAR(36),
    payload_json             TEXT,
    idempotency_key          VARCHAR(200) NOT NULL,
    priority                 INTEGER NOT NULL DEFAULT 0,
    retry_count              INTEGER NOT NULL DEFAULT 0,
    max_retry_count          INTEGER NOT NULL DEFAULT 3,
    next_retry_at            TIMESTAMP,
    started_at               TIMESTAMP,
    finished_at              TIMESTAMP,
    failure_summary          TEXT,
    failure_detail           TEXT
);
CREATE INDEX IF NOT EXISTS idx_memory_job_project_status ON oc_memory_job (project_id, status);
CREATE INDEX IF NOT EXISTS idx_memory_job_next_retry ON oc_memory_job (status, next_retry_at);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_job_idempotency ON oc_memory_job (idempotency_key);

-- The progress-ledger migration V7 depends on these two tables and then creates
-- oc_requirement_workspace, oc_task_execution, oc_execution_step,
-- oc_execution_checkpoint, oc_execution_effect and oc_run_observation.
