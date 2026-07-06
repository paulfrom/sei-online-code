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

## PR1.1（本轮）已完成 — codex 基座加固

### 范围（闭环 PR1 四个开放 TODO）
- **`-o` 替代 NDJSON 猜解**：`CodexRunner.buildArgs(prompt, model, outputFile)` 加 `--json` + `-o <tempfile>`；`runBlocking` 末尾经 `readResultFile` 读文件、删文件。删除 PR1 的 `extractResultJson`（NDJSON `item.text` 猜解）+ `objectMapper`/jackson 依赖。`--json` 保留仅作 stdout 事件流可见性，不再参与结果解析。
- **`-m`/`--model` 注入**：`CliRunner.execute` 两重载加 `String model` 尾参；`CodexRunner` 注入 `-m <model>`、`ClaudeRunner` 注入 `--model <model>`（均当 model 非空时）。3 个调用方（`DispatchService`/`PlanAgentService`×2/`FeatureDesignBuildService`）传 `agent.getModel()`（agent 可空时传 null）。
- **real-codex e2e**：新建 `CodexRunnerRealCodexTest`（env-gated，对齐 `ClaudeRunnerRealClaudeTest`）：`codex --version` 探测 + `git init` 临时 cwd（镜像生产 git workspace，避免注入 `--skip-git-repo-check`）+ 确定性 echo prompt 断言。
- **fake-executable 集成测试（额外）**：`CodexRunnerFakeExecutableTest` 用 fake 脚本替身离线钉死 `-o` 落盘→读取→剥围栏 + `-m` 透传 + 失败返 null 的 Java 接线端到端。WHY：real-codex 依赖 OpenAI 网络（受区域限制），fake 测试在 CI/受限网络也能跑，闭环「codex 路径从未对任何类 codex 程序跑过」这一 PR1 最大风险（Java 侧）。
- **代理 env 文档化**：`CodexRunner` 类 javadoc 显式说明 ProcessBuilder 默认继承 JVM env（`HTTPS_PROXY`/`HTTP_PROXY`/`NO_PROXY` 自动透传），shell alias 不进 JVM——启动器（`dev-start.sh`/systemd）须 export。

### 实证发现（2026-07-04，本机 codex 0.141.0）
- `codex exec --json` 真实 NDJSON 事件 schema 已观测：`{"type":"thread.started"}` / `{"type":"turn.started"}` / `{"type":"item.completed","item":{...}}` / `{"type":"turn.failed","error":{...}}`。**PR1 猜测的 `item.type=="agentMessage" && item.phase=="final_answer"` 与实际不符**——但 PR1.1 走 `-o`，不解析 NDJSON，故无影响。
- **`-o` 成功路径未能在线实证**：本机代理 `127.0.0.1:20171` 出口到 OpenAI 封锁区域（`403 unsupported_country_region_territory`，cf-ray HKG），codex token 刷新失败 → `turn.failed` → exit 1 → 无 `-o` 文件。失败路径（无文件→`readResultFile` 返 null→调用方 fallback）已由 `CodexRunnerFakeExecutableTest` + `CodexRunnerTest` 钉死；成功路径（codex 正常产出最终消息→`-o` 写文件）依赖 codex CLI 行为契约，待可访问 OpenAI 的环境实证（`CodexRunnerRealCodexTest` 在该环境会自动启用）。
- `-o` 失败时不创建文件——印证 `readResultFile` 对缺失文件返 null 的契约正确。

### 验证状态（PR1.1）— 已通过
- `compileTestJava`：绿。
- 单测：`CodexRunnerTest`（重写：buildArgs `-o`/`-m` + `readResultFile`）/`CodexRunnerFakeExecutableTest`（4 用例 0 skip）/`CodexSandboxConfigTest`/`CliRunnerRegistryTest`/`PlanAgentServiceTest` 全过。
- mock arity 同步：`PlanAgentServiceTest`（5 处 3-arg→4-arg）、`FeatureDesignBuildServiceTest`（1 处 5-arg→6-arg，`@Disabled` 但编译过）、`ClaudeRunnerRealClaudeTest`（3-arg→4-arg null model）。
- **未跑**：`CodexRunnerRealCodexTest`（本机网络封锁，跑了会 403；env-gated，可访问 OpenAI 时自动启用）、`ClaudeRunnerRealClaudeTest`（按既有 on-demand 模式）。

## PR2（本轮）已完成 — codex execenv 对齐（首批 #1–#4）

### 范围
- **#1 memory 禁用**：`CodexSandboxConfig.buildBlock` 增 `features.memories=false` + `memories.generate_memories=false` + `memories.use_memories=false`（dotted key，置 `[sandbox_workspace_write]` 段头之前，避 TOML 次序约束）。防跨任务记忆泄漏（multica#3130）。
- **#2 multi_agent 禁用**：`buildBlock` 增 `features.multi_agent=false`。防子 agent 未结束即触发 turn/completed。
- **#3 skill_strip**：`CodexSandboxConfig.stripSkillsConfig(content)` 剥用户 config.toml 中 `[[skills.config]]` 数组表段（Codex Desktop 写的 plugin-backed skills 缺 `path`，CLI 0.114 TOML 解析拒）；`write` 流水在 `stripManagedBlock` 后调用。段从 `[[skills.config]]` 行到下一表头/串尾止。
- **#4 user_skills 播种**：`CodexSandboxConfig.seedUserSkills(codexHome, logger)` 递归拷 `~/.codex/skills/` → per-run `<codexHome>/skills/`；源缺失 no-op；best-effort（拷贝失败仅 warn 不抛，不阻断 codex 运行）。`CodexRunner.runBlocking` 在 `write` 后调用。包级重载 `seedUserSkills(codexHome, userHome, logger)` 供测试注入临时家目录。

### 验证状态（PR2）— 已通过
- `compileTestJava`：绿。
- 单测全过（0 skip / 0 fail）：`CodexSandboxConfigTest`（12 用例，新增 6：memory/multi_agent 键存在[linux+darwin]、stripSkillsConfig 直测、write 剥 skills 段、seedUserSkills 拷树+源缺失 no-op）/`CodexRunnerTest`(10)/`CodexRunnerFakeExecutableTest`(4)/`CliRunnerRegistryTest`(6)/`PlanAgentServiceTest`(5)。
- `CodexRunnerFakeExecutableTest` 仍绿——`seedUserSkills` 接线未破坏 fake-executable 路径。

### 残留 / 未做
- **未在线实证**：memory/multi_agent/skill_strip 的实际 codex 行为依赖可访问 OpenAI 的环境跑 `CodexRunnerRealCodexTest`（本机代理出口 403）。Java 侧接线与 config.toml 形态已由离线单测钉死。
- **#5–#7 + app-server + gemini**：见下方「后续 PR」未划除项。

## PR3（本轮）已完成 — codex execenv 对齐 #5 / #6 / #7

### 范围
- **#5 home 符号链接树**：`CodexSandboxConfig.linkSharedHome(codexHome, [sharedHome], logger)`——符号链接共享 `~/.codex/`（或 `$CODEX_HOME`）的 `auth.json`（文件）、`sessions/`、`plugins/cache`（目录）到 per-run CODEX_HOME；隔离拷贝 `instructions.md`、`config.json`（syncCopiedFile 刷新语义，multica MUL-2646）。config.toml 不动（已由 `write` 托管）。符号链接失败文件回退拷贝、目录 warn no-op；`removeLinkOrDir` 不递归进符号链接目标（防误删共享 sessions/）。`CodexRunner.runBlocking` 在 `write` 后调用。对应 multica `codex_home.go` + `codex_home_link.go`。
- **#6 MCP 托管块（全链路）**：`Agent.mcpConfig`（TEXT 列）+ `AgentDto` `@Schema` + V13 迁移 + 前端表单（TextArea JSON）/mock/type；`CodexSandboxConfig.writeMcpBlock(codexHome, mcpConfig, logger)` 移植 multica `ensureCodexMcpConfig`/`renderCodexMcpServersBlock`/`stripCodexUserMcpServerTables`/`jsonValueToCodexTOMLInline`——`[mcp_servers.*]` 托管块（BEGIN/END marker）、stdio server 直渲、http server 归一化（`headers`→`http_headers` + `experimental_use_rmcp_client`）、bare-key 校验、**0o600 chmod**（secrets 走 `mcp_servers.<id>.env` 不入 argv）、托管时剥用户全局 mcp 表（strict，防 TOML 重定义）；`CliRunner.execute` 两重载加 `mcpConfig` 尾参；`CodexRunner` 写块、`ClaudeRunner` 收但忽略（TODO claude `--mcp-config`）；4 调用方传 `agent.getMcpConfig()`。
- **#7 AGENTS.md / CLAUDE.md brief（Codex + Claude parity）**：新 `AgentBriefWriter.writeBrief(workDir, cliTool, name, instructions, logger)`——marker 块（`<!-- BEGIN/END SEI-RUNTIME -->`）幂等注入，保留用户既有内容；codex→`AGENTS.md`、claude（含 null/blank 默认）→`CLAUDE.md`、未知→skip；brief 内容 = agent identity（`## Agent Identity` + name + instructions，对齐 multica）。**service 层 spawn 前调用**（对齐 multica daemon 职责，非 runner）：`PlanAgentService`×2、`DispatchService`、`FeatureDesignBuildService`。**不做 cleanup**（SEI workdir 为临时区/worktree，非用户本地仓库）。

### 验证状态（PR3）— 已通过
- V13 迁移：已应用本地 pg17（`oc_agent.mcp_config TEXT` nullable 存在；本库不用 Flyway 跟踪表，同 V12 手动 apply）。
- `compileTestJava`：绿。
- 单测全过（0 skip / 0 fail）：`CodexSandboxConfigTest`（28 用例：PR2 12 + #5 linkSharedHome 6 + #6 MCP 10）/`CodexRunnerFakeExecutableTest`(5，+1 MCP 配置文件快照)/`CliRunnerRegistryTest`(6)/`PlanAgentServiceTest`(5)/`AgentBriefWriterTest`(新建 10)/`AgentServiceTest`。mock arity 同步：`PlanAgentServiceTest`(5-arg→6-arg)、`FeatureDesignBuildServiceTest`(7-arg→8-arg，`@Disabled` 但编译过)、`ClaudeRunnerRealClaudeTest`/`CodexRunnerRealCodexTest`(4-arg→5-arg null mcpConfig)。
- 前端 `pnpm build`：通过。
- **未跑**：`CodexRunnerRealCodexTest`（本机网络封锁 403；env-gated）、`ClaudeRunnerRealClaudeTest`（on-demand）。

### 残留 / 未做
- **未在线实证**：#5 符号链接树 / #6 MCP 块 / #7 brief 的实际 codex 行为依赖可访问 OpenAI 的环境跑 `CodexRunnerRealCodexTest`（本机代理出口 403）。Java 侧接线与 config.toml/AGENTS.md 形态已由离线单测 + fake-executable 测试钉死。
- **claude MCP**：~~`ClaudeRunner` 收 `mcpConfig` 但忽略~~ **PR4 已完成**（`--mcp-config` 临时文件 + `--strict-mcp-config`）。
- **brief cleanup**：未实现（SEI workdir 生命周期不需要；若未来引入 local_directory 流再加，对齐 multica `CleanupRuntimeConfig`）。
- **brief 富 SEI runtime header**：~~当前仅 agent identity~~ **PR4 已完成**（`## Runtime Context`：CLI tool / Model / MCP config）。

## PR4（本轮）已完成 — codex app-server + claude MCP + brief 富化

### 范围
- **#8 codex app-server JSON-RPC**：`CodexRunner` 改 `codex app-server --listen stdio://` 长连接，删除一次性 `exec --json -o`。新增包级 `CodexAppServerClient`（JSON-RPC 请求/响应匹配、server request 自动放行 `item/commandExecution/requestApproval` / `item/fileChange/requestApproval` / `item/permissions/requestApproval` / `mcpServer/elicitation/request` 等、未知 request fail-closed `-32601`）+ `CodexAppServerEvents`（聚合 `item/agentMessage/delta`、跟踪 `turn/completed` 与 failed/errored/cancelled 状态）。lifecycle：initialize → initialized → thread/start → turn/start → 等 turn/completed（30 min 超时）。保留 per-run `CODEX_HOME` 与 `stripFences`。
- **claude MCP 接线**：`ClaudeRunner` 收 `mcpConfig` 后写临时文件（校验 JSON，blank/非法 → no-op），注入 `--mcp-config <file> --strict-mcp-config`，finally 删文件。包级 `ClaudeRunner(String executable)` 构造供 fake-executable 测试注入。
- **brief 富 runtime context**：`AgentBriefWriter.writeBrief(...,model,hasMcpConfig,logger)` 7 参重载，brief 增 `## Runtime Context`（CLI tool / Model / MCP config）；旧 5 参重载委托（null/false）。4 调用方（`PlanAgentService`×2、`DispatchService`、`FeatureDesignBuildService`）传 `agent.getModel()` + `agent.getMcpConfig()` 非空。

### 验证状态（PR4）— 已通过
- `compileTestJava`：绿。
- 单测全过：`CodexAppServerClientTest`（event 累积 + JSON-RPC 匹配 + 审批放行 + 未知 request fail-closed）/`CodexRunnerTest`（buildArgs app-server stdio）/`CodexRunnerFakeExecutableTest`（app-server fake 脚本：delta 聚合 / 审批 / 早退返 null / MCP 块快照）/`ClaudeRunnerTest`（MCP 临时文件透传 + blank 不注 flag）/`AgentBriefWriterTest`（runtime context + blank model 省略）/`PlanAgentServiceTest`。
- **未跑**：`CodexRunnerRealCodexTest`（本机网络封锁 403；env-gated，待可访问 OpenAI 的环境实证 app-server 成功路径）。

### 残留 / 未做
- **real codex app-server e2e**：依赖可访问 OpenAI 的环境跑 `CodexRunnerRealCodexTest`（本机代理出口 403）。Java 侧 app-server 接线已由离线单测 + fake-executable 测试钉死。
- **第二个非 claude vendor**（gemini 等）：若产品需求出现，按 codex app-server 模式新增 runner + registry 注册。

## 后续 PR（未做）

按 multica execenv 对齐优先级排序：

1. ~~**codex memory 禁用**：`config.toml` 写 `features.memories=false` + `memories.generate_memories/use_memories=false`（防跨任务记忆泄漏，multica#3130）。对应 multica `codex_memory.go`。~~ **PR2 已完成**。
2. ~~**codex multi_agent 禁用**：`features.multi_agent=false`（防子 agent 未结束即触发 turn/completed）。对应 `codex_multi_agent.go`。~~ **PR2 已完成**。
3. ~~**codex skill_strip**：剥 `[[skills.config]]`（Codex Desktop 写的 plugin-backed skills 缺 path，CLI 0.114 TOML 解析拒）。对应 `codex_skill_strip.go`。~~ **PR2 已完成**。
4. ~~**codex user_skills 播种**：`~/.codex/skills/` → per-run `codex-home/skills/`。对应 `codex_user_skills.go`。~~ **PR2 已完成**。
5. ~~**codex home 符号链接树**：`auth.json`/`sessions/`/`plugins/cache` 共享 + `instructions.md`/`config.json` 隔离拷贝。对应 `codex_home.go` + `codex_home_link.go`。~~ **PR3 已完成**。
6. ~~**codex MCP `[mcp_servers.*]` 托管块**：per-run 写 MCP server 配置（secrets 走 0o600 文件，不入 argv）。对应 `pkg/agent/codex.go:ensureCodexMcpConfig`。~~ **PR3 已完成**（全链路：Agent.mcpConfig + DTO + V13 + 前端 + writeMcpBlock）。
7. ~~**AGENTS.md runtime brief**：per-run workdir 写托管 brief（codex 映射 AGENTS.md，非 CLAUDE.md）。对应 `runtime_config.go`。~~ **PR3 已完成**（AgentBriefWriter，codex+claude parity）。
8. ~~**codex app-server JSON-RPC 协议**：`codex app-server --listen stdio://` 长连接客户端（支持流式/resumable/MCP）。对应 `pkg/agent/codex.go` 全量。替代一次性 `exec`。~~ **PR4 已完成**。
9. ~~**`--model` 注入**：runner 感知 `agent.model`，传 `--model` 或写 config.toml。~~ **PR1.1 已完成**（`execute` 签名加 model 尾参；claude `--model` / codex `-m`）。
10. ~~**real-codex e2e 测试**：env-gated，钉死 spawn + NDJSON 解析端到端。~~ **PR1.1 已完成**（`CodexRunnerRealCodexTest` + 离线 `CodexRunnerFakeExecutableTest`；解析改 `-o` 文件，非 NDJSON）。
11. **第二个非 claude vendor**（gemini 等）：若需求出现，按 codex 模式新增 runner + registry 注册。

## 验证状态（PR1）— 已通过

- V12 迁移：已应用本地 pg17，`oc_agent.cli_tool` 列存在（`character varying(50)`，nullable）。
- `compileTestJava`：绿。
- 单测：`CodexSandboxConfigTest`/`CodexRunnerTest`/`CliRunnerRegistryTest`/`PlanAgentServiceTest`/`AgentServiceTest` 全过。
- 前端 `pnpm build`：通过。
- `FeatureDesignBuildServiceTest`：`@Disabled`（既有延期，非本轮引入），保证编译通过。

## 交接给下一会话（2026-07-04，PR1.1 后）

### 当前状态
- PR1 已合并 main（commit `4db3a30`）；PR1.1 在 `feat/align-skill-multica` 完成（未提交）。
- 多 CLI runner 抽象 + PR1.1 加固就位：`CliRunner.execute(...,model)` 两重载；`CodexRunner`（`codex exec --json -o <file> [-m <model>]`，结果从 `-o` 文件读）+ `ClaudeRunner`（`--model`）+ `CodexSandboxConfig` + `CliRunnerRegistry`；`Agent.cliTool`/`Agent.model` 端到端打通。
- 验证：`compileTestJava` 绿；`CodexRunnerTest`/`CodexRunnerFakeExecutableTest`/`CodexSandboxConfigTest`/`CliRunnerRegistryTest`/`PlanAgentServiceTest` 全过。**Java 侧 `-o`/`-m` 接线已由 fake-executable 测试离线闭环。**

### 残留风险（须在可访问 OpenAI 的环境闭环）
- **`-o` 成功路径未在线实证**：本机代理出口到 OpenAI 封锁区域（`403 unsupported_country_region_territory`），codex token 刷新失败 → `turn.failed`。`CodexRunnerRealCodexTest` 在本机会 403（不 skip，因 codex 已装）；在可访问 OpenAI 的环境会自动启用并实证成功路径。
- 代理 env：`CodexRunner` javadoc 已文档化 ProcessBuilder 继承 JVM env；启动器须 export `HTTPS_PROXY/HTTP_PROXY`。`dev-start.sh` 未改（避免硬编码代理地址；按 rule 11，代理地址属部署环境配置）。

### `codex exec --help` 实证（2026-07-04，codex 0.141.0）
- `--json` ✓ "Print events to stdout as JSONL"。
- **`-o, --output-last-message <FILE>`** ✓ → PR1.1 用作结果来源（替代 NDJSON 猜解）。
- `-m, --model <MODEL>` ✓ → PR1.1 注入。
- `-s, --sandbox` ✓（与 `CodexSandboxConfig` 一致）；`-c key=value`、`--ignore-user-config`、`--ephemeral`、`--skip-git-repo-check`（PR1.1 未用，生产 cwd 为 git workspace）。
- 真实 NDJSON schema 已观测：`thread.started`/`turn.started`/`item.completed`/`turn.failed`（与 PR1 猜测不符，但已不解析）。

### 推荐下一步：PR4 后续
见上方「后续 PR（未做）」未划除项，按优先级：
1. ~~codex app-server JSON-RPC 协议（#8）~~ **PR4 已完成**。
2. ~~claude MCP 接线（`--mcp-config`）~~ **PR4 已完成**。
3. ~~brief 富 SEI runtime header~~ **PR4 已完成**（runtime context：CLI tool / Model / MCP config）。brief cleanup 仍延后（local_directory 流引入时）。
4. 第二个非 claude vendor（gemini 等，按需；#11）— 残留。

### 恢复命令（下一会话）
```bash
cd /home/paul/project/sei-online-code
git checkout feat/align-skill-multica
# 验证 PR3 基线（离线，不依赖 OpenAI）
cd backend && ./gradlew :sei-online-code-service:test --console=plain \
  --tests "*CodexRunnerTest" --tests "*CodexRunnerFakeExecutableTest" \
  --tests "*CodexSandboxConfigTest" --tests "*CliRunnerRegistryTest" \
  --tests "*PlanAgentServiceTest" --tests "*AgentBriefWriterTest"
# 可访问 OpenAI 时实证 -o 成功路径（本机代理出口被封会 403）
codex exec "Reply with exactly the word PONG and nothing else." --json \
  -o /tmp/codex-out.txt --skip-git-repo-check && cat /tmp/codex-out.txt
```

### 开放 TODO
- real codex app-server e2e 在线实证（待可访问 OpenAI 的环境；`CodexRunnerRealCodexTest` 届时自动启用，会顺带实证 #5 home-link / #6 MCP 块 / #7 brief 的真实 codex 行为）。
- ~~claude MCP 接线~~ **PR4 已完成**。
- ~~brief 富 SEI runtime header~~ **PR4 已完成**。brief cleanup 仍延后（local_directory 流引入时）。
- ~~codex app-server JSON-RPC 协议（#8）~~ **PR4 已完成**。
- 第二个非 claude vendor（#11）— 残留，按产品需求。
