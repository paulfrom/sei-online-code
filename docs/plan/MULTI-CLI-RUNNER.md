# 多 CLI Runner 路线图（参考 multica）

> 参考 multica `server/pkg/agent/{agent.go,codex.go}` + `server/internal/daemon/execenv/codex_*.go`。
> 目标：SEI 后端按 `Agent.cliTool` 分流到不同 CLI 工具（claude/codex/...），逐步对齐 multica 的 per-vendor execenv 模型。

## vendor 范围决策

- **claude**（既有）：`claude -p <prompt> --output-format json` 一次性。
- **codex**（PR1 新增）：`codex exec <prompt> --json` 一次性；per-run `CODEX_HOME/config.toml` 写 sandbox 托管块。
- **DeepSeek**：剔除。DeepSeek 无官方 agentic CLI（仅 OpenAI 兼容 HTTP API）；multica 亦不 spawn deepseek 进程，仅作 model 经 opencode/openclaw 路由。若未来需要，走 HTTP API runner 或经 opencode 路由，另立设计。
- **gemini 等**：未列入。multica runtime_profile 有 `protocol_family=gemini`（曾标 unsupported），后续可按需补。

## PR1（本轮）已完成

### 范围
- `CliRunner` 接口（`tool()` + 2 个 `execute` 重载）。
- `ClaudeRunner implements CliRunner`（`tool()="claude"`，既有 spawn 逻辑不动）。
- `CodexRunner implements CliRunner`（`tool()="codex"`，`codex exec --json` 一次性，per-run `CODEX_HOME` + sandbox 配置，NDJSON 防御性解析）。
- `CodexSandboxConfig`：per-run `config.toml` 写 `# BEGIN sei-managed` 托管块；非-darwin→`workspace-write`+`network_access=true`，darwin→`danger-full-access`（对齐 multica codex_sandbox.go + openai/codex#10390）。
- `CliRunnerRegistry`：注入 `List<CliRunner>`，`resolve(tool)` 按 `tool()` 查表，null/blank/未知→默认 claude。
- 3 个调用方（`DispatchService`/`PlanAgentService`/`FeatureDesignBuildService`）ctor：`ClaudeRunner`→`CliRunnerRegistry`，spawn 前 `registry.resolve(agent.getCliTool())`。
- `Agent.cliTool` 端到端：entity `@Column(cli_tool)` + DTO `@Schema` + V12 迁移 + 前端 type/form(Select)/mock。
- 测试：`CodexSandboxConfigTest`/`CodexRunnerTest`/`CliRunnerRegistryTest` 新建；`PlanAgentServiceTest`/`FeatureDesignBuildServiceTest` ctor mock 同步。

### 已知缺口（PR1 主动暴露）
- `codex exec --json` 的精确 NDJSON 事件字段未在线核实（web search 本会话不可用）。当前按 multica app-server 已核实 schema（`item.type=="agentMessage" && item.phase=="final_answer"` → `item.text`）防御性解析，回退最后非空行。标 `TODO(oma-deferred): 对照 codex exec --help / 真实运行核实`。
- `--model` 未注入（runner 不感知 `agent.model`）；codex 走 config.toml 默认。
- real-codex e2e 测试未加（env-gated，同 `ClaudeRunnerRealClaudeTest` 模式，标 TODO）。

## 后续 PR（未做）

按 multica execenv 对齐优先级排序：

1. **codex memory 禁用**：`config.toml` 写 `features.memories=false` + `memories.generate_memories/use_memories=false`（防跨任务记忆泄漏，multica#3130）。对应 multica `codex_memory.go`。
2. **codex multi_agent 禁用**：`features.multi_agent=false`（防子 agent 未结束即触发 turn/completed）。对应 `codex_multi_agent.go`。
3. **codex skill_strip**：剥 `[[skills.config]]`（Codex Desktop 写的 plugin-backed skills 缺 path，CLI 0.114 TOML 解析拒）。对应 `codex_skill_strip.go`。
4. **codex user_skills 播种**：`~/.codex/skills/` → per-run `codex-home/skills/`。对应 `codex_user_skills.go`。
5. **codex home 符号链接树**：`auth.json`/`sessions/`/`plugins/cache` 共享 + `instructions.md`/`config.json` 隔离拷贝。对应 `codex_home.go` + `codex_home_link.go`。
6. **codex MCP `[mcp_servers.*]` 托管块**：per-run 写 MCP server 配置（secrets 走 0o600 文件，不入 argv）。对应 `pkg/agent/codex.go:ensureCodexMcpConfig`。
7. **AGENTS.md runtime brief**：per-run workdir 写托管 brief（codex 映射 AGENTS.md，非 CLAUDE.md）。对应 `runtime_config.go`。
8. **codex app-server JSON-RPC 协议**：`codex app-server --listen stdio://` 长连接客户端（~2000 行，支持流式/resumable/MCP）。对应 `pkg/agent/codex.go` 全量。替代一次性 `exec`，需评估收益。
9. **`--model` 注入**：runner 感知 `agent.model`，传 `--model` 或写 config.toml。
10. **real-codex e2e 测试**：env-gated，钉死 spawn + NDJSON 解析端到端。
11. **第二个非 claude vendor**（gemini 等）：若需求出现，按 codex 模式新增 runner + registry 注册。

## 验证状态（PR1）— 已通过

- V12 迁移：已应用本地 pg17，`oc_agent.cli_tool` 列存在（`character varying(50)`，nullable）。
- `compileTestJava`：绿。
- 单测：`CodexSandboxConfigTest`/`CodexRunnerTest`/`CliRunnerRegistryTest`/`PlanAgentServiceTest`/`AgentServiceTest` 全过。
- 前端 `pnpm build`：通过。
- `FeatureDesignBuildServiceTest`：`@Disabled`（既有延期，非本轮引入），保证编译通过。
