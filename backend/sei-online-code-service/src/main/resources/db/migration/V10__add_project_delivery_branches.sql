ALTER TABLE oc_project
    ADD COLUMN IF NOT EXISTS workspace_base_branch VARCHAR(200),
    ADD COLUMN IF NOT EXISTS delivery_target_branch VARCHAR(200);

UPDATE oc_project
SET workspace_base_branch = 'main'
WHERE workspace_base_branch IS NULL OR BTRIM(workspace_base_branch) = '';
