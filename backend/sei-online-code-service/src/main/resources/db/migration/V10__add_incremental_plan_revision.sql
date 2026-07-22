-- Incremental plan revision within an existing automation loop.
--
-- This migration is additive: existing loops and task history remain unchanged.
-- revision_seq = 0 denotes records created before incremental revision support.

ALTER TABLE oc_requirement
    ADD COLUMN IF NOT EXISTS revision_seq BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS applied_revision_seq BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS revision_state VARCHAR(32) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS revision_trigger_comment_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS revision_failure_reason TEXT;

ALTER TABLE oc_execution_plan
    ADD COLUMN IF NOT EXISTS base_plan_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS trigger_comment_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS revision_seq BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS change_set_json TEXT;

ALTER TABLE oc_coding_task
    ADD COLUMN IF NOT EXISTS revision_seq BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS supersedes_task_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS disposition_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_requirement_revision_state
    ON oc_requirement (revision_state, revision_seq);
CREATE INDEX IF NOT EXISTS idx_execution_plan_requirement_revision
    ON oc_execution_plan (requirement_id, loop_id, revision_seq);
-- Historical plans in the same loop are all revision 0. Uniqueness starts with
-- actual incremental revisions so existing installations can upgrade safely.
CREATE UNIQUE INDEX IF NOT EXISTS uk_execution_plan_revision
    ON oc_execution_plan (requirement_id, loop_id, revision_seq)
    WHERE revision_seq > 0;
CREATE INDEX IF NOT EXISTS idx_coding_task_requirement_revision
    ON oc_coding_task (requirement_id, loop_id, revision_seq);
CREATE INDEX IF NOT EXISTS idx_coding_task_supersedes
    ON oc_coding_task (supersedes_task_id);

CREATE TABLE IF NOT EXISTS oc_task_handoff_snapshot (
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
    coding_task_id           VARCHAR(36) NOT NULL,
    run_id                   VARCHAR(36),
    revision_seq             BIGINT NOT NULL,
    trigger_comment_id       VARCHAR(36) NOT NULL,
    head_commit              VARCHAR(64),
    base_commit              VARCHAR(64),
    changed_files_json       TEXT,
    diff_stat                TEXT,
    diff_summary             TEXT,
    progress_snapshot_json   TEXT,
    run_summary              TEXT,
    CONSTRAINT oc_task_handoff_revision_chk CHECK (revision_seq > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_handoff_task_revision
    ON oc_task_handoff_snapshot (coding_task_id, revision_seq);
CREATE INDEX IF NOT EXISTS idx_task_handoff_requirement_revision
    ON oc_task_handoff_snapshot (requirement_id, revision_seq);
CREATE INDEX IF NOT EXISTS idx_task_handoff_run
    ON oc_task_handoff_snapshot (run_id);

COMMENT ON TABLE oc_task_handoff_snapshot IS
    '计划修订时保存编码任务的 Git 工作区、进度账本和 Run 成果摘要';
