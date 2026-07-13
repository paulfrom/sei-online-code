-- V27: CodingTask 调度器所需字段
-- 支持按 DAG 依赖、fileScope 冲突、前后端 lane 并发限制执行任务。

ALTER TABLE oc_requirement
    ADD COLUMN IF NOT EXISTS active_loop_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_requirement_active_loop ON oc_requirement (active_loop_id);

ALTER TABLE oc_coding_task
    ALTER COLUMN detailed_design_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS area VARCHAR(32),
    ADD COLUMN IF NOT EXISTS depends_on TEXT,
    ADD COLUMN IF NOT EXISTS execution_plan_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS plan_task_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS assigned_agent VARCHAR(64),
    ADD COLUMN IF NOT EXISTS loop_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_coding_task_area ON oc_coding_task (area);
CREATE INDEX IF NOT EXISTS idx_coding_task_execution_plan ON oc_coding_task (execution_plan_id);
CREATE INDEX IF NOT EXISTS idx_coding_task_loop ON oc_coding_task (loop_id);
CREATE INDEX IF NOT EXISTS idx_coding_task_plan_task_key ON oc_coding_task (plan_task_key);

ALTER TABLE oc_run
    ADD COLUMN IF NOT EXISTS run_type VARCHAR(32);
