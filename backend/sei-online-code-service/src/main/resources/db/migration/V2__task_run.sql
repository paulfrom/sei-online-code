-- Phase 2: task / run (PostgreSQL). 契约 Phase 2 §1。
-- String-UUID 主键（IdGenerator.nextIdStr，36 位），审计字段由 BaseAuditableEntity 提供。
-- Task/Run 状态见契约 Phase 2 §4；fileScope 以 JSON（TEXT）列持久化。

-- ============================ oc_task ============================
CREATE TABLE oc_task (
    id                  VARCHAR(36)  NOT NULL,
    iteration_id        VARCHAR(36)  NOT NULL,
    title               VARCHAR(200),
    description         TEXT,
    -- fileScope 文件边界声明，以 JSON（TEXT）列持久化
    file_scope          TEXT,
    assigned_agent      VARCHAR(100),
    state               VARCHAR(20)  NOT NULL,
    worktree_branch     VARCHAR(200),
    seq                 INTEGER,
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_task PRIMARY KEY (id)
);
CREATE INDEX idx_task_iteration ON oc_task (iteration_id);

-- ============================ oc_run ============================
CREATE TABLE oc_run (
    id                  VARCHAR(36)  NOT NULL,
    task_id             VARCHAR(36)  NOT NULL,
    iteration_id        VARCHAR(36)  NOT NULL,
    state               VARCHAR(20)  NOT NULL,
    worktree_path       VARCHAR(500),
    exit_code           INTEGER,
    started_date        TIMESTAMP,
    finished_date       TIMESTAMP,
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_run PRIMARY KEY (id)
);
CREATE INDEX idx_run_iteration ON oc_run (iteration_id);
CREATE INDEX idx_run_task ON oc_run (task_id);
