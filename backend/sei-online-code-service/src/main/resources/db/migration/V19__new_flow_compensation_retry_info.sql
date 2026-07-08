-- V19: 为新流程实体补齐补偿所需的失败/重试信息字段

-- Requirement 已存在重试字段，但缺少失败码、阶段与触发来源
ALTER TABLE oc_requirement
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS failure_stage VARCHAR(32),
    ADD COLUMN IF NOT EXISTS last_trigger_source VARCHAR(32);

-- OverviewDesign 补齐重试与触发来源
ALTER TABLE oc_overview_design
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_retry_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_trigger_source VARCHAR(32);

-- DetailedDesign 补齐重试与触发来源
ALTER TABLE oc_detailed_design
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_retry_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_trigger_source VARCHAR(32);

-- CodingTask 补齐重试与触发来源
ALTER TABLE oc_coding_task
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_retry_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_trigger_source VARCHAR(32);
