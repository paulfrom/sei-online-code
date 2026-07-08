-- V20: 放宽 Run 旧流程字段非空约束，兼容 requirement-driven 新流程（codingTaskId 可为空）

ALTER TABLE oc_run ALTER COLUMN task_id DROP NOT NULL;
ALTER TABLE oc_run ALTER COLUMN iteration_id DROP NOT NULL;
