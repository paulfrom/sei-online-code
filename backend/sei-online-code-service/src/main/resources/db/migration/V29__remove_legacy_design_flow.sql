-- PRD comment-driven loop fully replaces OverviewDesign/DetailedDesign.

ALTER TABLE oc_project
    ADD COLUMN IF NOT EXISTS validation_config TEXT;

ALTER TABLE oc_memory_job
    ADD COLUMN IF NOT EXISTS payload_json TEXT,
    ADD COLUMN IF NOT EXISTS loop_id VARCHAR(64);

ALTER TABLE oc_project
    DROP COLUMN IF EXISTS auto_run_coding_task;

ALTER TABLE oc_coding_task
    DROP COLUMN IF EXISTS detailed_design_id;

ALTER TABLE oc_coding_task
    DROP COLUMN IF EXISTS detailed_design_version;

DROP TABLE IF EXISTS oc_detailed_design;
DROP TABLE IF EXISTS oc_overview_design;

DELETE FROM oc_agent_skill
WHERE agent_id IN (
    SELECT id FROM oc_agent WHERE name IN ('overview-design-agent', 'detailed-design-agent')
);

DELETE FROM oc_agent
WHERE name IN ('overview-design-agent', 'detailed-design-agent');
