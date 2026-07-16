-- iteration 概念清理：oc_task 删除冗余列、oc_run 按真实语义重命名为 log_stream_key。
-- 应用前先部署对应实体改动（Task 删除 iterationId；Run 的 iteration_id→log_stream_key）。
-- 实际数据库 PostgreSQL（application.yaml: jdbc:postgresql）；Hibernate ddl-auto=validate
-- 要求列名/索引名与实体 @Column/@Index 严格匹配，否则启动期校验失败。
--
-- oc_task.iteration_id 此前恒等于 feature_design_id（FeatureDesignBuildService 在下一行赋同值），
-- 为纯冗余列，且其查询路径（findByIterationIdOrderBySeqAsc）无任何生产调用方，直接删除。
ALTER TABLE oc_task DROP COLUMN IF EXISTS iteration_id;
DROP INDEX IF EXISTS idx_task_iteration;

-- oc_run.iteration_id 实为 WS 日志流订阅键（/ws/run/{log_stream_key}），并兼作 spec/plan/
-- feature-design 等无更细 FK 时的 runNo 分组兜底。按真实语义重命名（值不变，行为保持）。
ALTER TABLE oc_run RENAME COLUMN iteration_id TO log_stream_key;
ALTER INDEX IF EXISTS idx_run_iteration RENAME TO idx_run_log_stream;
