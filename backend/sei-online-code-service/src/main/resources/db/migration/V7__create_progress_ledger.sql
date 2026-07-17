-- Run / Execution 进度账本：核心数据表。ADR-001 / docs/architecture/run-execution-progress-data-model.md。
--
-- 实现说明：
-- 1. 方言 PostgreSQL（application.yaml: jdbc:postgresql + PostgreSQLDialect；ddl-auto=validate）。
-- 2. 主键与审计字段沿用 BaseAuditableEntity 默认映射（id / creator_* / created_date / last_editor_* / last_edited_date）。
--    本批审计表不引入 ISoftDelete（checkpoint/effect/observation 只追加，不软删）。
-- 3. 跨聚合只用扁平字符串 ID 引用，不建物理外键（避免 workspace→execution→workspace 循环插入依赖）。
-- 4. 部分唯一索引（WHERE col IS NOT NULL）用于可空业务唯一键，与数据模型 §4/§5 一致。
-- 5. 可空可观测字段、证据 JSON 均以独立列或 TEXT 承载；JSON 内字段本期不建索引。
--
-- 回滚：本迁移仅 CREATE，回滚按逆序 DROP TABLE（见文件尾注释）。

-- ============================ oc_requirement_workspace ============================
-- 每个项目需求唯一的 worktree、feature branch 和写 owner（数据模型 §3）。
CREATE TABLE oc_requirement_workspace (
    id                  VARCHAR(36) PRIMARY KEY,
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(50),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(50),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,

    project_id          VARCHAR(36)  NOT NULL,
    requirement_id      VARCHAR(36)  NOT NULL,
    workspace_path      VARCHAR(500) NOT NULL,
    branch_name         VARCHAR(200) NOT NULL,
    base_commit         VARCHAR(64)  NOT NULL,
    current_head        VARCHAR(64)  NOT NULL,
    active_loop_id      VARCHAR(64),
    owner_run_id        VARCHAR(36),
    owner_execution_id  VARCHAR(36),
    lease_expires_at    TIMESTAMP,
    fencing_token       BIGINT       NOT NULL DEFAULT 0,
    snapshot_version    BIGINT       NOT NULL DEFAULT 0,
    state               VARCHAR(32)  NOT NULL,
    last_progress_at    TIMESTAMP,
    retention_until     TIMESTAMP,

    CONSTRAINT oc_req_ws_token_chk   CHECK (fencing_token >= 0),
    CONSTRAINT oc_req_ws_snapshot_chk CHECK (snapshot_version >= 0)
);
CREATE UNIQUE INDEX uk_req_ws_project_requirement ON oc_requirement_workspace (project_id, requirement_id);
CREATE UNIQUE INDEX uk_req_ws_project_branch      ON oc_requirement_workspace (project_id, branch_name);
CREATE INDEX idx_req_ws_owner_run                 ON oc_requirement_workspace (owner_run_id);
CREATE INDEX idx_req_ws_state_retention           ON oc_requirement_workspace (state, retention_until);
COMMENT ON TABLE oc_requirement_workspace IS 'Requirement 唯一工作区：worktree、feature branch 与写 owner';

-- ============================ oc_task_execution ============================
-- 同一业务任务与输入快照的一次逻辑执行（数据模型 §4）。重复 Run 通过 execution_key 共享。
CREATE TABLE oc_task_execution (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(50),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(50),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,

    execution_key            VARCHAR(128) NOT NULL,
    task_type                VARCHAR(32)  NOT NULL,
    business_task_id         VARCHAR(36)  NOT NULL,
    coding_task_id           VARCHAR(36),
    requirement_id           VARCHAR(36)  NOT NULL,
    loop_id                  VARCHAR(64)  NOT NULL,
    input_hash               VARCHAR(64)  NOT NULL,
    plan_version             INTEGER      NOT NULL,
    status                   VARCHAR(32)  NOT NULL,
    requirement_workspace_id VARCHAR(36)  NOT NULL,
    base_commit              VARCHAR(64)  NOT NULL,
    latest_head              VARCHAR(64),
    active_run_id            VARCHAR(36),
    last_progress_at         TIMESTAMP,
    settlement_key           VARCHAR(128),
    version                  BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT oc_task_exec_planver_chk CHECK (plan_version > 0),
    CONSTRAINT oc_task_exec_version_chk CHECK (version >= 0)
);
CREATE UNIQUE INDEX uk_task_exec_execution_key ON oc_task_execution (execution_key);
CREATE UNIQUE INDEX uk_task_exec_settlement_key ON oc_task_execution (settlement_key) WHERE settlement_key IS NOT NULL;
CREATE INDEX idx_task_exec_req_loop_status     ON oc_task_execution (requirement_id, loop_id, status);
CREATE INDEX idx_task_exec_biz_input           ON oc_task_execution (business_task_id, input_hash);
CREATE INDEX idx_task_exec_workspace           ON oc_task_execution (requirement_workspace_id);
COMMENT ON TABLE oc_task_execution IS '逻辑执行根：重复 Run 共享同一 Execution 进度账本';

-- ============================ oc_execution_step ============================
-- Execution 当前态账本（数据模型 §6）。状态迁移仅由服务端命令控制。
CREATE TABLE oc_execution_step (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(50),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(50),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,

    execution_id             VARCHAR(36)  NOT NULL,
    step_key                 VARCHAR(160) NOT NULL,
    phase                    VARCHAR(32)  NOT NULL,
    plan_version             INTEGER      NOT NULL,
    title                    VARCHAR(200) NOT NULL,
    description              TEXT,
    input_hash               VARCHAR(64)  NOT NULL,
    required_step            BOOLEAN      NOT NULL DEFAULT TRUE,
    status                   VARCHAR(32)  NOT NULL,
    owner_run_id             VARCHAR(36),
    claim_token              VARCHAR(64),
    workspace_fencing_token  BIGINT,
    lease_expires_at         TIMESTAMP,
    attempt_count            INTEGER      NOT NULL DEFAULT 0,
    progress_percent         INTEGER,
    latest_checkpoint_id     VARCHAR(36),
    checkpoint_data          TEXT,
    evidence_data            TEXT,
    last_heartbeat_at        TIMESTAMP,
    started_at               TIMESTAMP,
    applied_at               TIMESTAMP,
    completed_at             TIMESTAMP,
    version                  BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT oc_step_planver_chk    CHECK (plan_version > 0),
    CONSTRAINT oc_step_attempt_chk    CHECK (attempt_count >= 0),
    CONSTRAINT oc_step_version_chk    CHECK (version >= 0),
    CONSTRAINT oc_step_progress_chk   CHECK (progress_percent IS NULL OR progress_percent BETWEEN 0 AND 100)
);
CREATE UNIQUE INDEX uk_step_exec_key_planver ON oc_execution_step (execution_id, step_key, plan_version);
CREATE INDEX idx_step_exec_planver_status    ON oc_execution_step (execution_id, plan_version, status);
CREATE INDEX idx_step_owner_lease            ON oc_execution_step (owner_run_id, lease_expires_at);
COMMENT ON TABLE oc_execution_step IS 'Execution 步骤账本；VERIFIED 不可原地回退';

-- ============================ oc_execution_checkpoint ============================
-- 不可变进度 journal（数据模型 §7）。只允许 INSERT。
CREATE TABLE oc_execution_checkpoint (
    id                       VARCHAR(36) PRIMARY KEY,
    creator_id               VARCHAR(36),
    creator_account          VARCHAR(50),
    creator_name             VARCHAR(100),
    created_date             TIMESTAMP,
    last_editor_id           VARCHAR(36),
    last_editor_account      VARCHAR(50),
    last_editor_name         VARCHAR(100),
    last_edited_date         TIMESTAMP,

    execution_id             VARCHAR(36) NOT NULL,
    step_id                  VARCHAR(36),
    run_id                   VARCHAR(36) NOT NULL,
    sequence_no              BIGINT      NOT NULL,
    checkpoint_type          VARCHAR(32) NOT NULL,
    claim_token              VARCHAR(64),
    workspace_fencing_token  BIGINT      NOT NULL,
    payload                  TEXT,
    evidence_digest          VARCHAR(64),
    git_head                 VARCHAR(64),
    parent_git_head          VARCHAR(64),

    CONSTRAINT oc_checkpoint_seq_chk CHECK (sequence_no >= 0)
);
CREATE UNIQUE INDEX uk_checkpoint_exec_seq ON oc_execution_checkpoint (execution_id, sequence_no);
CREATE INDEX idx_checkpoint_step_seq      ON oc_execution_checkpoint (step_id, sequence_no);
CREATE INDEX idx_checkpoint_run_seq       ON oc_execution_checkpoint (run_id, sequence_no);
CREATE INDEX idx_checkpoint_git_head      ON oc_execution_checkpoint (git_head);
COMMENT ON TABLE oc_execution_checkpoint IS '不可变进度 journal；只允许 INSERT';

-- ============================ oc_execution_effect ============================
-- 平台受控副作用及首次结果快照（数据模型 §8）。
CREATE TABLE oc_execution_effect (
    id                  VARCHAR(36) PRIMARY KEY,
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(50),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(50),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,

    effect_key          VARCHAR(200) NOT NULL,
    execution_id        VARCHAR(36)  NOT NULL,
    step_id             VARCHAR(36)  NOT NULL,
    effect_type         VARCHAR(32)  NOT NULL,
    request_hash        VARCHAR(64)  NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    fencing_token       BIGINT       NOT NULL,
    request_snapshot    TEXT,
    result_snapshot     TEXT,
    external_reference  VARCHAR(500),
    prepared_at         TIMESTAMP    NOT NULL,
    applied_at          TIMESTAMP,
    confirmed_at        TIMESTAMP,
    last_reconciled_at  TIMESTAMP,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT oc_effect_version_chk CHECK (version >= 0)
);
CREATE UNIQUE INDEX uk_effect_effect_key        ON oc_execution_effect (effect_key);
CREATE INDEX idx_effect_exec_status            ON oc_execution_effect (execution_id, status);
CREATE INDEX idx_effect_step_status            ON oc_execution_effect (step_id, status);
CREATE INDEX idx_effect_type_reference         ON oc_execution_effect (effect_type, external_reference);
COMMENT ON TABLE oc_execution_effect IS '受控副作用账本；相同 effectKey+requestHash 返回首次结果';

-- ============================ oc_run_observation ============================
-- Run 追加式备注/验证/对账时间线（数据模型 §9）。只允许 INSERT；更正用 supersedes_observation_id。
CREATE TABLE oc_run_observation (
    id                        VARCHAR(36) PRIMARY KEY,
    creator_id                VARCHAR(36),
    creator_account           VARCHAR(50),
    creator_name              VARCHAR(100),
    created_date              TIMESTAMP,
    last_editor_id            VARCHAR(36),
    last_editor_account       VARCHAR(50),
    last_editor_name          VARCHAR(100),
    last_edited_date          TIMESTAMP,

    run_id                    VARCHAR(36)  NOT NULL,
    sequence_no               BIGINT       NOT NULL,
    observation_type          VARCHAR(32)  NOT NULL,
    verification_status       VARCHAR(32)  NOT NULL,
    source_type               VARCHAR(32)  NOT NULL,
    source_id                 VARCHAR(100),
    summary                   VARCHAR(500),
    detail                    TEXT,
    step_id                   VARCHAR(36),
    checkpoint_id             VARCHAR(36),
    evidence_data             TEXT,
    supersedes_observation_id VARCHAR(36),
    observed_at               TIMESTAMP    NOT NULL,

    CONSTRAINT oc_observation_seq_chk CHECK (sequence_no >= 0)
);
CREATE UNIQUE INDEX uk_observation_run_seq   ON oc_run_observation (run_id, sequence_no);
CREATE INDEX idx_observation_run_observed    ON oc_run_observation (run_id, observed_at DESC);
CREATE INDEX idx_observation_step_observed   ON oc_run_observation (step_id, observed_at DESC);
CREATE INDEX idx_observation_status_observed ON oc_run_observation (verification_status, observed_at DESC);
COMMENT ON TABLE oc_run_observation IS 'Run 追加式 observation 时间线；只允许 INSERT';

-- 回滚（手动执行，不纳入 Flyway 自动 down）：
-- DROP TABLE IF EXISTS oc_run_observation;
-- DROP TABLE IF EXISTS oc_execution_effect;
-- DROP TABLE IF EXISTS oc_execution_checkpoint;
-- DROP TABLE IF EXISTS oc_execution_step;
-- DROP TABLE IF EXISTS oc_task_execution;
-- DROP TABLE IF EXISTS oc_requirement_workspace;
