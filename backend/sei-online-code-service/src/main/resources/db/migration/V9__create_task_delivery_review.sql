-- ============================================================
-- V9: 任务交付审阅表（方案 runtime-agent-failure-pm-review §4.2）
--
-- 记录 pm-agent 对单次交付 Run 的审阅生命周期。执行状态与审阅状态分离，
-- 避免继续扩张 CodingTaskStatus 的含义。
--
-- 幂等保证：(coding_task_id, delivery_run_id) 唯一约束，确保重复完成事件
-- 不会创建两次 PM 审阅。
--
-- 注意：本仓库 ddl-auto=validate，Hibernate 启动时校验实体与列定义一致；
-- 列名/类型/可空性必须与 TaskDeliveryReview 实体 @Column 完全匹配。
-- ============================================================

CREATE TABLE IF NOT EXISTS oc_task_delivery_review (
    -- 主键（BaseEntity: String UUID, length=36, 不可更新）
    id                       VARCHAR(36) PRIMARY KEY,

    -- 审计字段（BaseAuditableEntity / IAuditable）
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(50),
    creator_name             VARCHAR(50),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(50),
    last_editor_name         VARCHAR(50),
    last_edited_date         TIMESTAMP,

    -- 业务字段
    requirement_id           VARCHAR(36) NOT NULL,
    execution_plan_id        VARCHAR(36),
    coding_task_id           VARCHAR(36) NOT NULL,
    delivery_run_id          VARCHAR(36) NOT NULL,
    review_run_id            VARCHAR(36),
    loop_id                  VARCHAR(64),

    -- TaskDeliveryReviewStatus / TaskDeliveryReviewDecision / DeliveryFailureCategory
    -- 均以 @Enumerated(STRING) 存储
    status                   VARCHAR(32) NOT NULL,
    decision                 VARCHAR(32),
    summary                  TEXT,
    decision_json            TEXT,
    failure_category         VARCHAR(32),

    -- 交付是否成功（交付时记录，用于决策校验：FAILED+APPROVE 必须被拒绝）
    delivery_succeeded       BOOLEAN,
    reviewed_date            TIMESTAMP
);

-- 幂等约束：同一任务同一交付 Run 只能有一条审阅记录（方案 §4.2 / §11 风险控制）
ALTER TABLE oc_task_delivery_review
    ADD CONSTRAINT uk_tdr_task_run UNIQUE (coding_task_id, delivery_run_id);

-- 查询索引：门禁（hasOpenReview by requirement）、补偿（findFirstByCodingTaskId）、
-- 依赖条件（isApproved / latestDecisionForCodingTask）
CREATE INDEX IF NOT EXISTS idx_tdr_requirement   ON oc_task_delivery_review (requirement_id);
CREATE INDEX IF NOT EXISTS idx_tdr_plan          ON oc_task_delivery_review (execution_plan_id);
CREATE INDEX IF NOT EXISTS idx_tdr_coding_task   ON oc_task_delivery_review (coding_task_id);
CREATE INDEX IF NOT EXISTS idx_tdr_status        ON oc_task_delivery_review (status);
