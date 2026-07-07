-- V17: 需求驱动新流程数据模型（Project 元数据扩展 + Requirement/OverviewDesign/DetailedDesign/CodingTask）

-- 1. Project 新增元数据字段（兼容已有数据，全部 nullable 或带默认值）
ALTER TABLE oc_project
    ADD COLUMN IF NOT EXISTS git_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS workspace_path VARCHAR(500),
    ADD COLUMN IF NOT EXISTS auto_run_coding_task BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_project_workspace_path ON oc_project (workspace_path);

-- 2. Requirement：需求的 PRD 聚合根
CREATE TABLE IF NOT EXISTS oc_requirement (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    prd_version INTEGER NOT NULL DEFAULT 1,
    prd_content TEXT,
    failure_code VARCHAR(64),
    failure_stage VARCHAR(32),
    failure_summary TEXT,
    failure_detail TEXT,
    last_failed_at TIMESTAMP,
    last_retry_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    last_trigger_source VARCHAR(32),
    creator_id VARCHAR(36),
    creator_account VARCHAR(100),
    creator_name VARCHAR(100),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_editor_id VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name VARCHAR(100),
    last_edited_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_requirement_project ON oc_requirement (project_id);
CREATE INDEX IF NOT EXISTS idx_requirement_status ON oc_requirement (status);

-- 3. OverviewDesign：概览设计，每个 Requirement 一份
CREATE TABLE IF NOT EXISTS oc_overview_design (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    requirement_id VARCHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    content TEXT,
    failure_summary TEXT,
    failure_detail TEXT,
    last_failed_at TIMESTAMP,
    creator_id VARCHAR(36),
    creator_account VARCHAR(100),
    creator_name VARCHAR(100),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_editor_id VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name VARCHAR(100),
    last_edited_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_overview_design_project ON oc_overview_design (project_id);
CREATE INDEX IF NOT EXISTS idx_overview_design_requirement ON oc_overview_design (requirement_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_overview_design_req_ver ON oc_overview_design (requirement_id, version);
CREATE INDEX IF NOT EXISTS idx_overview_design_status ON oc_overview_design (status);

-- 4. DetailedDesign：按 feature 拆分的详细设计
CREATE TABLE IF NOT EXISTS oc_detailed_design (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    requirement_id VARCHAR(36) NOT NULL,
    overview_design_id VARCHAR(36) NOT NULL,
    module_id VARCHAR(128) NOT NULL,
    module_title VARCHAR(200),
    feature_id VARCHAR(128) NOT NULL,
    feature_title VARCHAR(200),
    status VARCHAR(32) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    content TEXT,
    failure_summary TEXT,
    failure_detail TEXT,
    last_failed_at TIMESTAMP,
    creator_id VARCHAR(36),
    creator_account VARCHAR(100),
    creator_name VARCHAR(100),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_editor_id VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name VARCHAR(100),
    last_edited_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_detailed_design_project ON oc_detailed_design (project_id);
CREATE INDEX IF NOT EXISTS idx_detailed_design_requirement ON oc_detailed_design (requirement_id);
CREATE INDEX IF NOT EXISTS idx_detailed_design_overview ON oc_detailed_design (overview_design_id);
CREATE INDEX IF NOT EXISTS idx_detailed_design_status ON oc_detailed_design (status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_detailed_design_overview_feature_ver ON oc_detailed_design (overview_design_id, feature_id, version);

-- 5. CodingTask：编码任务
CREATE TABLE IF NOT EXISTS oc_coding_task (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    requirement_id VARCHAR(36) NOT NULL,
    detailed_design_id VARCHAR(36) NOT NULL,
    detailed_design_version INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(200),
    description TEXT,
    file_scope TEXT,
    failure_summary TEXT,
    failure_detail TEXT,
    last_failed_at TIMESTAMP,
    creator_id VARCHAR(36),
    creator_account VARCHAR(100),
    creator_name VARCHAR(100),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_editor_id VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name VARCHAR(100),
    last_edited_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_coding_task_project ON oc_coding_task (project_id);
CREATE INDEX IF NOT EXISTS idx_coding_task_requirement ON oc_coding_task (requirement_id);
CREATE INDEX IF NOT EXISTS idx_coding_task_detailed_design ON oc_coding_task (detailed_design_id);
CREATE INDEX IF NOT EXISTS idx_coding_task_status ON oc_coding_task (status);

-- 6. Run：扩展支持 codingTaskId、runNo 等（兼容旧 task_id）
ALTER TABLE oc_run
    ADD COLUMN IF NOT EXISTS coding_task_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS run_no INTEGER,
    ADD COLUMN IF NOT EXISTS user_prompt TEXT,
    ADD COLUMN IF NOT EXISTS failure_summary TEXT,
    ADD COLUMN IF NOT EXISTS failure_reason TEXT,
    ADD COLUMN IF NOT EXISTS trigger_source VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_run_coding_task ON oc_run (coding_task_id);
