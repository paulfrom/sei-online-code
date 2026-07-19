DO $$
BEGIN
    IF to_regclass('public.oc_requirement') IS NOT NULL THEN
        ALTER TABLE oc_requirement
            ADD COLUMN IF NOT EXISTS requirement_no VARCHAR(32);

        UPDATE oc_requirement
        SET requirement_no = 'REQ-' || upper(substr(id, 1, 8))
        WHERE requirement_no IS NULL OR requirement_no = '';

        ALTER TABLE oc_requirement
            ALTER COLUMN requirement_no SET NOT NULL;

        CREATE UNIQUE INDEX IF NOT EXISTS uk_requirement_project_no
            ON oc_requirement (project_id, requirement_no);
    END IF;
END $$;
