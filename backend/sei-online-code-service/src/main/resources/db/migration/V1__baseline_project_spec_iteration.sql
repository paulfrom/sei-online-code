-- Phase 1 baseline: project / spec / iteration (PostgreSQL)
-- 契约 §2 域实体。String-UUID 主键（IdGenerator.nextIdStr，36 位），审计字段由 BaseAuditableEntity 提供。
-- 生命周期状态见契约 §4，Spec 状态见契约 §2.2。

-- ============================ oc_project ============================
CREATE TABLE oc_project (
    id                   VARCHAR(36)  NOT NULL,
    name                 VARCHAR(200) NOT NULL,
    design               TEXT,
    state                VARCHAR(20)  NOT NULL,
    current_spec_id      VARCHAR(36),
    current_iteration_id VARCHAR(36),
    -- 审计字段（BaseAuditableEntity）
    creator_id           VARCHAR(36),
    creator_account      VARCHAR(100),
    creator_name         VARCHAR(100),
    created_date         TIMESTAMP,
    last_editor_id       VARCHAR(36),
    last_editor_account  VARCHAR(100),
    last_editor_name     VARCHAR(100),
    last_edited_date     TIMESTAMP,
    CONSTRAINT pk_oc_project PRIMARY KEY (id)
);
CREATE INDEX idx_project_state ON oc_project (state);

-- ============================ oc_spec ============================
CREATE TABLE oc_spec (
    id                  VARCHAR(36) NOT NULL,
    project_id          VARCHAR(36) NOT NULL,
    version             INTEGER     NOT NULL,
    state               VARCHAR(20) NOT NULL,
    -- 自由结构以 JSON（TEXT）列持久化
    pages               TEXT,
    components          TEXT,
    entities            TEXT,
    api_contract        TEXT,
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_spec PRIMARY KEY (id)
);
CREATE INDEX idx_spec_project ON oc_spec (project_id);
CREATE INDEX idx_spec_state ON oc_spec (state);

-- ============================ oc_iteration ============================
CREATE TABLE oc_iteration (
    id                  VARCHAR(36) NOT NULL,
    project_id          VARCHAR(36) NOT NULL,
    spec_id             VARCHAR(36) NOT NULL,
    spec_version        INTEGER,
    state               VARCHAR(20) NOT NULL,
    preview_url         VARCHAR(500),
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_iteration PRIMARY KEY (id)
);
CREATE INDEX idx_iteration_project ON oc_iteration (project_id);
CREATE INDEX idx_iteration_spec ON oc_iteration (spec_id);
