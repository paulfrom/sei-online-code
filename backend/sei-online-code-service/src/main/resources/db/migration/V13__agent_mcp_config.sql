-- Agent.mcpConfig（multica 维度：per-agent MCP server 托管配置，对齐 codex [mcp_servers.*] 托管块）
-- null/空 → 不托管，沿用 codex CLI 默认 MCP；"{}" → 托管空集（strict，禁用户全局 MCP）。
-- secrets 走 0o600 config.toml 文件，不入 argv。
ALTER TABLE oc_agent ADD COLUMN mcp_config TEXT;
