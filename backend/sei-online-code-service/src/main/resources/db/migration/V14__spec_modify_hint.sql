-- Spec 重生/精炼修改提示（对齐 Plan.modify_hint，V6）。
-- SpecState 加 GENERATING/FAILED 为 STRING 枚举新值，无需 schema 变更。
ALTER TABLE oc_spec ADD COLUMN modify_hint TEXT;
COMMENT ON COLUMN oc_spec.modify_hint IS '重生/精炼修改提示（对齐 oc_plan.modify_hint）';
