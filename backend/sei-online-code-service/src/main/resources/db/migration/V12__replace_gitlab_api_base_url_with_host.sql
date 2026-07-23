ALTER TABLE IF EXISTS oc_platform_config
    DROP COLUMN IF EXISTS gitlab_api_base_url;

ALTER TABLE IF EXISTS oc_platform_config
    ADD COLUMN IF NOT EXISTS gitlab_host VARCHAR(500);
