-- Phase 4: Full Build Loop (PostgreSQL). 契约 Phase 4 §1.1。
-- 迭代升级为一等时间线记录：追加回合溯源（round / parent_iteration_id / feedback）与终态落定时间。
-- round + parent_iteration_id 构成回合父链（第 1 回合 parent 为 null）；feedback 记录进入本回合的用户诉求。

-- ============================ oc_iteration 增列 ============================
ALTER TABLE oc_iteration ADD COLUMN round INTEGER;
ALTER TABLE oc_iteration ADD COLUMN parent_iteration_id VARCHAR(36);
ALTER TABLE oc_iteration ADD COLUMN feedback TEXT;
ALTER TABLE oc_iteration ADD COLUMN finished_date TIMESTAMP;

-- 时间线按 (project_id, round) 排序检索（ep #27 findByPage order by round）
CREATE INDEX idx_iteration_project_round ON oc_iteration (project_id, round);
