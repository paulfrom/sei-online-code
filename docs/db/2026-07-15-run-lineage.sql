-- Run lineage and terminal reason fields.
-- Apply before deploying code that validates the oc_run schema.

ALTER TABLE oc_run
    ADD COLUMN IF NOT EXISTS run_type VARCHAR(20) NOT NULL DEFAULT 'AGENT',
    ADD COLUMN IF NOT EXISTS parent_run_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS compensates_run_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS attempt_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS terminal_reason VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_run_parent
    ON oc_run (parent_run_id);

CREATE INDEX IF NOT EXISTS idx_run_compensates
    ON oc_run (compensates_run_id);

CREATE INDEX IF NOT EXISTS idx_run_coding_task_state_created
    ON oc_run (coding_task_id, state, created_date DESC);

CREATE INDEX IF NOT EXISTS idx_run_requirement_loop_state_created
    ON oc_run (requirement_id, loop_id, state, created_date DESC);
