-- CodingTask 恢复以执行计划、父 Run 链和原工作区状态为准。
-- resume_from_checkpoint_id 从未参与当前恢复流程，删除该遗留列，避免误导运维和后续开发。
ALTER TABLE IF EXISTS oc_run
    DROP COLUMN IF EXISTS resume_from_checkpoint_id;
