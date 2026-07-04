-- Agent.cliTool（multica 维度：per-agent CLI 工具选择，对齐 runtime profile protocol_family）
-- null/空 → 默认 claude（CliRunnerRegistry.DEFAULT_TOOL），向后兼容既有 agent。
ALTER TABLE oc_agent ADD COLUMN cli_tool VARCHAR(50);
