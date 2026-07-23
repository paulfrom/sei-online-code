ALTER TABLE oc_requirement
    ADD COLUMN IF NOT EXISTS delivery_mr_iid BIGINT,
    ADD COLUMN IF NOT EXISTS delivery_mr_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
    ADD COLUMN IF NOT EXISTS delivery_merged_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS delivery_merge_commit_hash VARCHAR(64);

UPDATE oc_requirement
SET delivery_mr_status = CASE
    WHEN delivery_mr_url IS NULL OR BTRIM(delivery_mr_url) = '' THEN 'NOT_SUBMITTED'
    ELSE 'OPEN'
END
WHERE delivery_mr_status = 'NOT_SUBMITTED';
