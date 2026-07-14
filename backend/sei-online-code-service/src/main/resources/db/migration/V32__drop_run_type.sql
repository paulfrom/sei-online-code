-- 移除 oc_run.run_type：Agent run 的类型改由 agent_name 快照区分，
-- 非 Agent run（交付、验证命令）靠 trigger_source 标识。
ALTER TABLE oc_run DROP COLUMN IF EXISTS run_type;
