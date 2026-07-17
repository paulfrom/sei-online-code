# Run / Execution 进度账本编码交接

状态：READY FOR HANDOFF / IMPLEMENTATION NOT STARTED

最后核对时间：2026-07-17（Asia/Shanghai）

计划版本：1

## 1. 接手者先读

按顺序读取：

1. 仓库根目录 `AGENTS.md`；
2. 对应模块的 `backend/AGENTS.md` 或 `frontend/AGENTS.md`；
3. [ADR-001](../architecture/adr-001-run-execution-progress-ledger.md)；
4. [数据模型](../architecture/run-execution-progress-data-model.md)；
5. [实施任务板](./run-execution-reliability-task-board.md)；
6. [机器可读计划](./run-execution-reliability-plan.json)；
7. 本交接文件的“当前执行快照”和“Run 备注”。

不要从聊天摘要推断实施状态。文件、Git commit、测试证据和本文件中的追加记录才是当前 bootstrap
阶段的接手依据。

## 2. 不可重新解释的已批准决策

- Run 可以重复调用；重复 Run 共享稳定 Execution。
- 平台阶段 + Agent 动态步骤。
- 同一 Requirement 只有一个 worktree、一个 feature branch 和一个写 owner。
- Requirement 内步骤串行写入；不同 Requirement 才允许并行。
- 使用 claim、lease 和 fencing token；旧 owner 不能继续写。
- `APPLIED` 与 `VERIFIED` 分离；`UNKNOWN` 必须先对账。
- observation、checkpoint 和 effect 历史追加，不覆盖。
- 最终只 commit、push、创建或更新一个 MR，不自动合并。
- 自动化 `COMPLETED` 表示“MR 已提交”，不表示 MR 已合入。
- 前端以服务端聚合快照为权威，WebSocket 通知刷新，轮询负责收敛。

如实施发现这些决策不可行，停止当前任务并新建 ADR 修订提议；不得在代码中静默改变语义。

## 3. 当前执行快照

| 项目 | 当前值 |
|---|---|
| 当前计划任务 | `EXE-001` |
| 任务状态 | `BLOCKED_PRECHECK` |
| 当前 owner | 未分配 |
| 已观察分支 | `main` |
| 已观察 HEAD | `6751eb1` |
| Requirement feature branch | 尚未创建/记录 |
| Requirement worktree | 尚未创建/记录 |
| 实施 checkpoint commit | 无 |
| eadp-backend skill | 可用（环境已变更；`~/.claude/skills/eadp-backend/SKILL.md` 已完整读取；`suid` 同样可用） |
| 最近完成验证 | preflight：git HEAD/status 与交接一致；eadp-backend 已可用并读取；8 文件 diff 已归类为 FAILED-keyword/supersede 连贯修复 |
| 下一动作 | 解除门禁 2（8 文件归属确认 + 可恢复基线）与门禁 3（本 Requirement 分支/worktree）后 claim `EXE-001` |

该表是当前态镜像。任务状态改变时更新该表，同时在第 9 节追加一条不可覆盖的 Run 备注。

## 4. 当前阻塞与安全边界

### 4.1 必需 skill 尚不可用

项目规则要求：

- 后端任务必须使用 `eadp-backend`；
- 前端任务必须使用 `suid`。

当前可用 skill 清单和本地 skill 目录中均未发现这两个 skill。后续 Agent 不得用名称相近的通用 skill
替代后直接编码。应先由用户/环境提供或明确允许安装；安装完成后完整读取对应 `SKILL.md`。

### 4.2 当前工作区不是干净的 Requirement worktree

当前位于 `main`，存在以下未提交改动：

```text
M  backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/CodingTaskExecutionService.java
M  backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/FeatureDesignBuildService.java
M  backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/PlanAgentService.java
M  backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/RequirementAgentService.java
M  backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/service/CodingTaskExecutionServiceTest.java
M  backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/service/FeatureDesignBuildServiceTest.java
M  backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/service/PlanAgentServiceTest.java
M  backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/service/RequirementAgentServiceTest.java
?? docs/architecture/
?? docs/bugs/
?? docs/plans/
```

其中 `CodingTaskExecutionService.java` 与 EXE-004 scope 直接重叠。现有 diff 包含移除
`result.output().contains("FAILED")` 自然语言误判；相关说明见
[`run-summary-failed-keyword-false-positive.md`](../bugs/run-summary-failed-keyword-false-positive.md)。

这些改动不得 reset、checkout、stash、覆盖或擅自拆分。首位接手 Agent 必须：

1. 重新执行 `git status --short` 和 `git diff --stat`；
2. 阅读全部现有 diff，确认它们属于前序修复还是其他用户工作；
3. 向用户确认归属，或使用已有任务记录证明归属；
4. 在归属明确且验证通过后，形成可恢复的基线 commit；
5. 再创建/切换到唯一 Requirement feature branch/worktree；
6. 把 branch、worktree 路径和 base commit 写回第 3 节。

在此门禁解除前，不得开始 EXE-001 数据库修改。

## 5. Claim 与续作协议（正式账本上线前）

正式 ProgressService 尚未实现，暂用本文件和 Git checkpoint 启动协作：

### Claim 一个任务

1. 确认所有 dependency 在第 8 节为 `DONE`。
2. 确认没有其他任务处于 `IN_PROGRESS`；本 Requirement 严格单写。
3. 核对实际 Git HEAD 等于第 3 节记录的 checkpoint HEAD。
4. 将目标任务状态改成 `IN_PROGRESS`，记录 owner、startedAt 和 base HEAD。
5. 在第 9 节追加 `CLAIM` 备注后再修改业务文件。

### 判断是否跳过

- 状态 `DONE` 且 checkpoint commit、验证证据都存在：跳过，不重新实现。
- 状态 `APPLIED`：只补验证，不重做实现。
- 状态 `IN_PROGRESS` 且 owner 仍活跃：不得并发修改，等待或退出。
- 状态 `IN_PROGRESS` 但 owner 已失联：先按第 6 节对账，不能直接重做。
- 状态 `UNKNOWN`：先检查 Git、文件 diff、测试报告和外部回执。
- 状态 `BLOCKED`：只在阻塞解除后继续 `nextAction`。

### 完成一个任务

1. 运行该任务要求的 focused tests。
2. 检查 `git diff --check`。
3. 生成一个符合 `<type>: <description>` 的 checkpoint commit。
4. 记录 commit SHA、changed files、验证命令与结果。
5. 将任务状态更新为 `DONE`。
6. 在第 9 节追加 `CHECKPOINT` 备注，指明下一个可执行任务。

除当前态表和任务表外，第 9 节历史备注只允许追加。

## 6. Run 失败后的接手方法

### 调用失败，未产生工作

证据：

- 无业务文件 diff；
- 无新 commit；
- 无测试报告；
- 无外部回执。

处理：追加 `RECONCILIATION` 备注，把任务恢复为 `READY`，下一 Run 从既有 `nextAction` 开始。

### 过程失败，存在部分工作

处理：

1. 保存 `git diff --stat` 和实际 diff；
2. 检查是否已有 checkpoint commit；
3. 运行不修改文件的 focused verification；
4. 已验证部分记录为 `DONE` 或 `APPLIED`；
5. 未完成部分记录明确 `nextAction`；
6. 新 owner 只从 `nextAction` 继续。

不得因为 Run 失败删除已有变更。

### 实际完成但 Run 超时

处理：

1. 检查 checkpoint commit 是否存在；
2. 检查测试命令及报告是否绑定该 HEAD；
3. 检查外部 push/MR 回执；
4. 证据完整则补记 `CHECKPOINT/DONE`，不重新执行；
5. 证据不足则标记 `APPLIED` 或 `UNKNOWN`，只补验证/对账。

## 7. 首个编码任务：EXE-001

在第 4 节门禁解除后，首位后端 Agent 从 EXE-001 开始。

### 允许修改

```text
docs/db/
backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/
backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/
backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/dao/
对应 entity/dao 测试
```

### 必须交付

- `oc_requirement_workspace`
- `oc_task_execution`
- `oc_execution_step`
- `oc_execution_checkpoint`
- `oc_execution_effect`
- `oc_run_observation`
- `oc_run` 增量字段与索引
- 对应 enum、entity、DAO 和迁移验证测试

### 不能提前做

- 不实现 ProgressService 状态机（EXE-002）。
- 不修改 Runner/CodingTaskExecutionService（EXE-004）。
- 不修改 WorkspaceManager（EXE-005）。
- 不修改 RequirementDeliveryService（EXE-006）。
- 不修改前端（EXE-008）。

### 完成证据

- PostgreSQL DDL 与实体字段、表名、索引名一致；
- Hibernate schema validation 可通过；
- 唯一键并发行为有测试；
- 历史 Run 兼容策略有测试；
- checkpoint commit SHA 已写入第 9 节。

## 8. 任务状态表

| 任务 | 状态 | Owner | Base/Checkpoint | Next action |
|---|---|---|---|---|
| EXE-001 | `BLOCKED_PRECHECK` | - | `6751eb1` | skill 门禁已解除；待解除 dirty-worktree 归属+基线（门禁 2）与 Requirement 分支/worktree（门禁 3） |
| EXE-002 | `BLOCKED_DEPENDENCY` | - | - | 等待 EXE-001 |
| EXE-003 | `BLOCKED_DEPENDENCY` | - | - | 等待 EXE-002 |
| EXE-004 | `BLOCKED_DEPENDENCY` | - | - | 等待 EXE-002 |
| EXE-005 | `BLOCKED_DEPENDENCY` | - | - | 等待 EXE-002、EXE-004 |
| EXE-006 | `BLOCKED_DEPENDENCY` | - | - | 等待 EXE-002、EXE-005 |
| EXE-007 | `BLOCKED_DEPENDENCY` | - | - | 等待 EXE-004、EXE-005、EXE-006 |
| EXE-008 | `BLOCKED_DEPENDENCY` | - | - | 等待 EXE-003 契约冻结 |
| EXE-009 | `BLOCKED_DEPENDENCY` | - | - | 等待 EXE-003/004/006/007 |
| ACC-001 | `BLOCKED_DEPENDENCY` | - | - | 按计划依赖运行 |
| ACC-002 | `BLOCKED_DEPENDENCY` | - | - | 按计划依赖运行 |
| ACC-003 | `BLOCKED_DEPENDENCY` | - | - | 按计划依赖运行 |
| DEL-001 | `BLOCKED_DEPENDENCY` | - | - | 三项验收通过后创建/更新 MR |

允许状态：

```text
BLOCKED_PRECHECK / BLOCKED_DEPENDENCY / READY / IN_PROGRESS /
APPLIED / UNKNOWN / BLOCKED / DONE
```

## 9. Run 备注（追加式）

### OBS-001 — PLAN_HANDOFF

- observedAt：2026-07-17
- source：Codex / PM planning
- task：PLAN
- state：`CONFIRMED`
- baseHead：`6751eb1`
- summary：ADR、数据模型、任务板和机器可读计划已落地；实施尚未开始。
- verification：
  - JSON 语法通过；
  - 13 个 task ID 唯一；
  - 所有 dependency 均指向已有任务；
  - 3 个独立 acceptance task 存在；
  - Markdown 链接和尾随空白检查通过。
- blocker：
  - `eadp-backend`、`suid` skill 不可用；
  - 当前 `main` 有 8 个未提交后端文件和未跟踪文档；
  - Requirement branch/worktree 尚未记录。
- nextAction：解除第 4 节门禁，然后 claim EXE-001。

### OBS-002 — PREFLIGHT

- observedAt：2026-07-17
- source/agent：handoff preflight agent
- run/session：current
- task：EXE-001（仅 preflight，未 claim）
- state：`BLOCKED_PRECHECK`（门禁 1/4/5 已解除；门禁 2/3 未解除）
- baseHead：`6751eb1`
- currentHead：`6751eb1fad52e497cf1720bbecd7d31b9d0fe70a`
- changedFiles：无（仅核对与文档追加，未改任何业务文件）
- verification：
  - git：branch=`main`，HEAD=`6751eb1fad52e497cf1720bbecd7d31b9d0fe70a`，与第 3 节一致；
  - git status：8 个已修改后端文件（4 service + 4 test）+ `docs/{architecture,bugs,plans}` 未跟踪，与第 4.2 节一致；
  - eadp-backend skill：环境已变更，现可用（`~/.claude/skills/eadp-backend/SKILL.md` 已完整读取，166 行；`suid` 同样可用）；门禁 1 解除；
  - diff 归类：8 文件为同一主题连贯修复——移除 Run 终态自然语言关键字误判（`CodingTaskExecutionService` 删除 `output.contains("FAILED")`；`FeatureDesignBuildService`/`PlanAgentService`/`RequirementAgentService` 将 `terminalReason` 由子串匹配改为显式 `RunTerminalReason` 入参），配套 4 个回归测试；与 `docs/bugs/run-summary-failed-keyword-false-positive.md` 修复处方一致；
  - 门禁 5：任务板无 `IN_PROGRESS` 任务（全为 `BLOCKED_*`），满足；
  - 现有 `.sei/`、`.worktrees/`、`project/data/.../workspaces/` 下 worktree 均属其他 requirement（REQ-0001 等），非本次 Requirement。
- evidence：
  - `git rev-parse HEAD` = `6751eb1fad52e497cf1720bbecd7d31b9d0fe70a`；
  - `git diff --stat` = 198 insertions / 43 deletions，8 文件；
  - skill 路径存在且已读。
- blocker：
  - 门禁 2 未解除：8 文件归属尚未由用户确认；`docs/bugs/...` 只明确覆盖 4 个 service 中的 `CodingTaskExecutionService`，其余 3 个无任务记录佐证归属；可恢复基线 commit 尚未形成（提交需用户授权，遵守 commit 规范不用 `git add -A/.`）；
  - 门禁 3 未解除：本 Requirement 的唯一 feature branch/worktree 尚未创建/记录。
- nextAction：等待用户 (a) 确认 8 文件归属（前序 FAILED-keyword/supersede 修复）并授权形成基线 commit；(b) 指定或同意创建本 Requirement 唯一 feature branch/worktree 并写入第 3 节。两者解除后再 claim EXE-001；当前不编码。

### 后续备注模板

```text
### OBS-<sequence> — <CLAIM|PROGRESS|CHECKPOINT|TERMINAL|RECONCILIATION>

- observedAt:
- source/agent:
- run/session:
- task:
- state:
- baseHead:
- currentHead:
- changedFiles:
- verification:
- evidence:
- blocker:
- nextAction:
```

## 10. 验证命令基线

后端从 `backend/` 执行，使用仓库 wrapper：

```bash
./gradlew :sei-online-code-service:test --tests "<focused test class>"
./gradlew :sei-online-code-service:test
./gradlew build
```

前端从 `frontend/` 执行：

```bash
pnpm test:regression
pnpm lint
pnpm build
```

通用检查：

```bash
git status --short
git diff --check
```

如果私有依赖源不可达，记录完整命令、失败阶段和环境原因；不得把“未运行”写成“验证通过”。

## 11. MR 交接

DEL-001 只有在 ACC-001、ACC-002、ACC-003 均为 `DONE` 后才能执行：

- MR source SHA 必须等于验收使用的最终 checkpoint SHA；
- MR 描述链接 ADR、数据模型、迁移、三份验收报告和回滚方式；
- 请求 CODEOWNERS 审核；
- 只创建或更新 MR；
- 不调用 merge API，不自动合并。
