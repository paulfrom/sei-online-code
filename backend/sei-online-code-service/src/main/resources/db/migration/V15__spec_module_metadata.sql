ALTER TABLE oc_spec
    ADD COLUMN module_id VARCHAR(64),
    ADD COLUMN module_title VARCHAR(200),
    ADD COLUMN module_summary TEXT;

CREATE INDEX idx_spec_module ON oc_spec (project_id, module_id);
