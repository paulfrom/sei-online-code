-- oc_run 扩展：Execution 绑定、invocation 幂等、thread/turn、heartbeat、恢复点与验证字段。
-- ADR-001 §1/§4；数据模型 §5。
--
-- 前置：本 ALTER 假定 oc_run 已存在（其建表 DDL 由基线 schema 提供；当前列以 Run 实体为准）。
-- 兼容：所有新增列均可空，历史 Run 保持可读（execution_id 迁移期允许为空，新 Run 必填，见数据模型 §13）。
-- 回滚：手动 ALTER TABLE oc_run DROP COLUMN <各列>（见文件尾注释）。

ALTER TABLE oc_run
    ADD COLUMN execution_id            VARCHAR(36),
    ADD COLUMN invocation_key          VARCHAR(128),
    ADD COLUMN executor_id             VARCHAR(100),
    ADD COLUMN thread_id               VARCHAR(128),
    ADD COLUMN turn_id                 VARCHAR(128),
    ADD COLUMN heartbeat_at            TIMESTAMP,
    ADD COLUMN observed_plan_version   INTEGER,
    ADD COLUMN resume_from_checkpoint_id VARCHAR(36),
    ADD COLUMN latest_observation_id   VARCHAR(36),
    ADD COLUMN verification_status     VARCHAR(32);

-- 相同 invocation_key 重入返回同一 Run；新调度 attempt 用新 invocation key 但命中同一 Execution。
CREATE UNIQUE INDEX uk_run_invocation_key ON oc_run (invocation_key) WHERE invocation_key IS NOT NULL;
CREATE INDEX idx_run_exec_created         ON oc_run (execution_id, created_date DESC);
CREATE INDEX idx_run_req_state_heartbeat  ON oc_run (requirement_id, state, heartbeat_at);
CREATE INDEX idx_run_thread               ON oc_run (thread_id);

COMMENT ON COLUMN oc_run.execution_id   IS '关联 TaskExecution；迁移期可空，新 Run 必填';
COMMENT ON COLUMN oc_run.invocation_key IS '调度调用幂等键；相同 key 重入返回同一 Run';

-- 回滚（手动执行）：
-- DROP INDEX IF EXISTS idx_run_thread;
-- DROP INDEX IF EXISTS idx_run_req_state_heartbeat;
-- DROP INDEX IF EXISTS idx_run_exec_created;
-- DROP INDEX IF EXISTS uk_run_invocation_key;
-- ALTER TABLE oc_run
--     DROP COLUMN IF EXISTS verification_status,
--     DROP COLUMN IF EXISTS latest_observation_id,
--     DROP COLUMN IF EXISTS resume_from_checkpoint_id,
--     DROP COLUMN IF EXISTS observed_plan_version,
--     DROP COLUMN IF EXISTS heartbeat_at,
--     DROP COLUMN IF EXISTS turn_id,
--     DROP COLUMN IF EXISTS thread_id,
--     DROP COLUMN IF EXISTS executor_id,
--     DROP COLUMN IF EXISTS invocation_key,
--     DROP COLUMN IF EXISTS execution_id;
