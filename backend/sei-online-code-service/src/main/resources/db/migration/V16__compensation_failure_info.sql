ALTER TABLE oc_plan
    ADD COLUMN failure_code VARCHAR(64),
    ADD COLUMN failure_stage VARCHAR(32),
    ADD COLUMN failure_summary TEXT,
    ADD COLUMN failure_detail TEXT,
    ADD COLUMN last_failed_at TIMESTAMP,
    ADD COLUMN last_retry_at TIMESTAMP,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMP,
    ADD COLUMN last_trigger_source VARCHAR(32);

ALTER TABLE oc_spec
    ADD COLUMN failure_code VARCHAR(64),
    ADD COLUMN failure_stage VARCHAR(32),
    ADD COLUMN failure_summary TEXT,
    ADD COLUMN failure_detail TEXT,
    ADD COLUMN last_failed_at TIMESTAMP,
    ADD COLUMN last_retry_at TIMESTAMP,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMP,
    ADD COLUMN last_trigger_source VARCHAR(32);

ALTER TABLE oc_feature_design
    ADD COLUMN failure_code VARCHAR(64),
    ADD COLUMN failure_stage VARCHAR(32),
    ADD COLUMN failure_summary TEXT,
    ADD COLUMN failure_detail TEXT,
    ADD COLUMN last_failed_at TIMESTAMP,
    ADD COLUMN last_retry_at TIMESTAMP,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMP,
    ADD COLUMN last_trigger_source VARCHAR(32);

CREATE TABLE oc_compensation_log (
    id                  VARCHAR(36) NOT NULL,
    entity_type         VARCHAR(64) NOT NULL,
    entity_id           VARCHAR(36) NOT NULL,
    action              VARCHAR(128) NOT NULL,
    success             BOOLEAN NOT NULL,
    message             TEXT,
    detail              TEXT,
    trigger_source      VARCHAR(32) NOT NULL,
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_compensation_log PRIMARY KEY (id)
);

CREATE INDEX idx_comp_log_entity ON oc_compensation_log (entity_type, entity_id);
CREATE INDEX idx_comp_log_created ON oc_compensation_log (created_date);
