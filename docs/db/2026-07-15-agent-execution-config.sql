-- Agent execution configuration fields.
-- These fields let runtime behavior come from oc_agent configuration instead of service-side hardcoding.

ALTER TABLE oc_agent
    ADD COLUMN IF NOT EXISTS prompt_template TEXT,
    ADD COLUMN IF NOT EXISTS execution_policy TEXT,
    ADD COLUMN IF NOT EXISTS scope_policy TEXT,
    ADD COLUMN IF NOT EXISTS output_schema TEXT;
