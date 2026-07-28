-- Run evidence, replayable logs, authoritative remediation feedback and scoped
-- Agent behavior memory. Run execution state remains independent from evidence
-- completeness.

ALTER TABLE oc_coding_task
    ADD COLUMN IF NOT EXISTS acceptance_criteria TEXT;

ALTER TABLE oc_task_delivery_review
    ADD COLUMN IF NOT EXISTS evidence_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS remediation_brief_json TEXT,
    ADD COLUMN IF NOT EXISTS feedback_applied_run_id VARCHAR(36);

CREATE TABLE IF NOT EXISTS oc_run_artifact (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    run_id                   VARCHAR(36) NOT NULL,
    artifact_type            VARCHAR(32) NOT NULL,
    state                    VARCHAR(32) NOT NULL,
    completeness             VARCHAR(32) NOT NULL,
    content_type             VARCHAR(100),
    encoding                 VARCHAR(32),
    byte_length              BIGINT,
    chunk_count              INTEGER,
    sha256                   VARCHAR(64),
    redacted                 BOOLEAN NOT NULL DEFAULT TRUE,
    metadata_json            TEXT,
    completed_at             TIMESTAMP,
    expires_at               TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_run_artifact_run_type
    ON oc_run_artifact (run_id, artifact_type);
CREATE INDEX IF NOT EXISTS idx_run_artifact_expires
    ON oc_run_artifact (expires_at);

CREATE TABLE IF NOT EXISTS oc_run_artifact_chunk (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    artifact_id              VARCHAR(36) NOT NULL,
    sequence_no              BIGINT NOT NULL,
    first_log_sequence       BIGINT,
    last_log_sequence        BIGINT,
    uncompressed_length      INTEGER NOT NULL,
    compressed_data          BYTEA NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_run_artifact_chunk_sequence
    ON oc_run_artifact_chunk (artifact_id, sequence_no);
CREATE INDEX IF NOT EXISTS idx_run_artifact_chunk_artifact
    ON oc_run_artifact_chunk (artifact_id, sequence_no);

CREATE TABLE IF NOT EXISTS oc_run_evidence (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    run_id                   VARCHAR(36) NOT NULL,
    evidence_version         INTEGER NOT NULL,
    status                   VARCHAR(32) NOT NULL,
    completeness             VARCHAR(32) NOT NULL,
    process_exit_code        INTEGER,
    git_base_commit          VARCHAR(64),
    git_head_commit          VARCHAR(64),
    summary                  TEXT,
    command_results_json     TEXT,
    acceptance_criteria_json TEXT,
    findings_json            TEXT,
    artifact_refs_json       TEXT,
    captured_at              TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_run_evidence_version
    ON oc_run_evidence (run_id, evidence_version);
CREATE INDEX IF NOT EXISTS idx_run_evidence_run
    ON oc_run_evidence (run_id, captured_at);

CREATE TABLE IF NOT EXISTS oc_agent_behavior_memory (
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
    agent_name               VARCHAR(100),
    area                     VARCHAR(100),
    scope_key                VARCHAR(200) NOT NULL,
    rule_text                TEXT NOT NULL,
    rationale                TEXT,
    source_review_id         VARCHAR(36),
    source_evidence_id       VARCHAR(36),
    status                   VARCHAR(32) NOT NULL,
    occurrence_count         INTEGER NOT NULL DEFAULT 1,
    helpful_count            INTEGER NOT NULL DEFAULT 0,
    ineffective_count        INTEGER NOT NULL DEFAULT 0,
    last_outcome             VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    last_applied_at          TIMESTAMP,
    expires_at               TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_behavior_memory_lookup
    ON oc_agent_behavior_memory (project_id, agent_name, area, status);
CREATE INDEX IF NOT EXISTS idx_behavior_memory_scope
    ON oc_agent_behavior_memory (scope_key, status);

CREATE TABLE IF NOT EXISTS oc_run_behavior_memory (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(100),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(100),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,
    run_id                   VARCHAR(36) NOT NULL,
    behavior_memory_id       VARCHAR(36) NOT NULL,
    outcome                  VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    evaluation_evidence_id   VARCHAR(36)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_run_behavior_memory
    ON oc_run_behavior_memory (run_id, behavior_memory_id);
CREATE INDEX IF NOT EXISTS idx_run_behavior_memory_run
    ON oc_run_behavior_memory (run_id);

CREATE INDEX IF NOT EXISTS idx_tdr_evidence
    ON oc_task_delivery_review (evidence_id);
CREATE INDEX IF NOT EXISTS idx_tdr_feedback_applied_run
    ON oc_task_delivery_review (feedback_applied_run_id);

COMMENT ON TABLE oc_run_artifact IS
    'Run 原始材料元数据；内容脱敏、压缩并按序保存在 oc_run_artifact_chunk';
COMMENT ON TABLE oc_run_evidence IS
    '版本化结构化证据包；完整度不改变 Run 执行状态';
COMMENT ON TABLE oc_agent_behavior_memory IS
    'PM 从证据中归纳并经效果验证的 Agent 行为改进记忆';
