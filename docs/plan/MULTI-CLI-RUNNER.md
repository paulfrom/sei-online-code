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

## 交接给下一会话（2026-07-04）

### 当前状态
- PR1 已完成并合并到 main（commit `4db3a30`），`feat/align-skill-multica` 与 main 同步。
- 多 CLI runner 抽象已就位：`CliRunner` 接口 + `ClaudeRunner` + `CodexRunner`（`codex exec --json` 一次性）+ `CodexSandboxConfig` + `CliRunnerRegistry`；`Agent.cliTool` 端到端打通。
- 验证：`compileTestJava` 绿、目标单测全过、`pnpm build` 过、V12 已应用 pg17。**但 codex 路径从未对真实 codex 跑过——PR1 最大风险。**

### `codex exec --help` 实证（2026-07-04，本机 codex 已装）
取代 PR1 的假设/TODO：
- `--json` ✓ "Print events to stdout as JSONL"（PR1 用法正确）
- **`-o, --output-last-message <FILE>`** → 把最终消息写文件，**可替代 PR1 的 NDJSON `item.text` 猜解**（绕过未验证风险）
- `-m, --model <MODEL>` → model 注入（PR1 TODO，flag 已确认）
- `-s, --sandbox <read-only|workspace-write|danger-full-access>` ✓（与 `CodexSandboxConfig` 一致）
- `-c key=value` → config 覆盖；`--ignore-user-config` → 不加载用户 config.toml（auth 仍用 CODEX_HOME）；`--ephemeral` → 不持久化 session；`--skip-git-repo-check` → 非 git 仓库可跑
- 代理：codex alias 带 `HTTPS_PROXY/HTTP_PROXY=http://127.0.0.1:20171`；Java ProcessBuilder 不继承 shell alias，后端 JVM 需自带该 env，否则 codex 无法访问 OpenAI。

### 推荐下一步：PR1.1 加固 codex 基座（有界）
1. `CodexRunner.buildArgs` 加 `-o <tempfile>`，结果从文件读（替代 `extractResultJson` 的 NDJSON 猜解）；`runBlocking` 末尾读文件、删文件。
2. `-m <agent.model>` 当 model 非空时注入——需让 runner 感知 `agent.model`（`execute` 签名加 model 参数，或 3 个调用方传 `agent.getModel()` 进 runner）。同步给 `ClaudeRunner` 加 `--model`（既有同样缺口）。
3. real-codex e2e 测试（env-gated，对齐 `ClaudeRunnerRealClaudeTest`）：确定性 echo prompt，断言 future 完成且结果非空。
4. 代理 env：`CodexRunner` 透传 `HTTPS_PROXY/HTTP_PROXY`（ProcessBuilder 默认继承 JVM env，但显式文档化）；或 `dev-start.sh` 注入。
5. 更新本文档把 PR1.1 标完成、挪 TODO。

### 恢复命令（下一会话）
```bash
cd /home/paul/project/sei-online-code
git checkout feat/align-skill-multica          # 与 main 同步，任一皆可
# 验证 PR1 基线
cd backend && ./gradlew :sei-online-code-service:test \
  --tests "*CodexRunnerTest" --tests "*CodexSandboxConfigTest" --tests "*CliRunnerRegistryTest" --console=plain
# 真实 codex 可用性 + -o 输出验证
codex exec --help | head
codex exec "echo PONG" --json -o /tmp/codex-out.txt --skip-git-repo-check && cat /tmp/codex-out.txt
```

### 开放 TODO（PR1 遗留，PR1.1 闭环）
- `codex exec --json` NDJSON 字段未核实 → PR1.1 用 `-o` 绕过
- `--model` 未注入（claude/codex 均缺）→ PR1.1 补
- real-codex e2e 未加 → PR1.1 加
- 代理 env 未透传 → PR1.1 处理

### 后续 PR（PR1.1 之后，按优先级）
见上方"后续 PR（未做）"段：codex memory / multi_agent / skill_strip / user_skills / home-link / MCP / AGENTS.md brief / app-server 协议 / 第二 vendor。
