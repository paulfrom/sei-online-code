-- Phase 5: 平台配置单例（PostgreSQL）。契约 Phase 5 §1.1。
-- 整表恒定一行，主键固定为 'CONFIG'。save 幂等 upsert 该行；get 缺失时补建默认行。
--   workspace_root：工作区根目录；空则运行期回退 oc.workspace.root 环境变量，仍空回退 ${java.io.tmpdir}/sei-online-code。
--   template_gitlab_url：模板仓库地址；无默认，空即走脚手架生成路径（day-one 路径）。

-- ============================ oc_platform_config ============================
CREATE TABLE oc_platform_config (
    id                  VARCHAR(36)  NOT NULL,
    workspace_root      VARCHAR(500),
    template_gitlab_url VARCHAR(500),
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_platform_config PRIMARY KEY (id)
);

-- ============================ 种子：默认单例行（契约 §1.1）============================
-- workspace_root 留空 → 运行期 env-fallback 解析默认；template_gitlab_url 留空 → 走脚手架生成路径。
INSERT INTO oc_platform_config (id, workspace_root, template_gitlab_url, created_date)
VALUES ('CONFIG', NULL, '', CURRENT_TIMESTAMP);
