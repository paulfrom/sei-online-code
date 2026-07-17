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
| 任务状态 | `IN_PROGRESS`（实施中，分批 checkpoint） |
| 当前 owner | backend-agent / claude |
| 已观察分支 | `feature/run-execution-reliability`（自 `main` `6751eb1` 切出） |
| 已观察 HEAD | `46a0657`（EXE-001 schema 契约 commit） |
| Requirement feature branch | `feature/run-execution-reliability` |
| Requirement worktree | 当前检出 `/home/paul/project/sei-online-code`；物理 worktree 绑定属 EXE-005 |
| 实施 checkpoint commit | 基线 `d49366f`+`4e291ce`；EXE-001 step1 `46a0657`（enums+V7+V8） |
| eadp-backend skill | 可用（`~/.claude/skills/eadp-backend/SKILL.md` 已读取；`suid` 同样可用） |
| 最近完成验证 | 门禁 1–5 解除；EXE-001 step1：11 enum + RunState 扩展 + V7/V8 已落 `46a0657`；api 模块 compileJava 通过；无 RunState 穷举 switch |
| 下一动作 | EXE-001 step2：6 个新 entity + Run 实体扩展（匹配 V7/V8）；step3 DAO；step4 测试 |

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
| EXE-001 | `IN_PROGRESS` | backend-agent/claude | `46a0657` | step1 enums+schema 已落；下一步 entity→DAO→test |
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

### OBS-003 — CHECKPOINT

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：baseline（用户授权 commit）
- state：`CONFIRMED`
- baseHead：`6751eb1`
- currentHead：`4e291ce`
- changedFiles：8 backend（fix）+ 7 planning docs（docs）
- verification：用户确认 8 文件归属并授权基线；`git checkout -b feature/run-execution-reliability`；2 commits；`git status --short` 干净；`git diff --check` 无空白错误。
- evidence：
  - `fix: stop deriving run terminal reason from natural-language keywords` = `d49366f07272e0ea87761a10c106e02af158a44d`；
  - `docs: add run execution reliability plan, adr and data model` = `4e291ceb6b12bb73f760289acd295c5e38ab3376`；
  - 门禁 2、3 据此解除。
- nextAction：claim 并调研 EXE-001。

### OBS-004 — INVESTIGATION

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：EXE-001（调研，未修改业务文件）
- state：`BLOCKED`（启动门禁 1–5 已解除；新增 base-schema 阻塞）
- baseHead：`4e291ce`
- currentHead：`4e291ce`
- verification（调研结论）：
  - DB 方言：PostgreSQL（application.yaml / 另一 profile / application-test.yml 均为 `jdbc:postgresql` + `PostgreSQLDialect`；testcontainers `postgresql:15`；`ddl-auto: validate`）。gradle 的 `mysqlVersion` 未启用，设计文档正确。
  - DDL 约定：BaseAuditableEntity 列 = id(varchar36) + creator_id/creator_account/creator_name/created_date + last_editor_id/last_editor_account/last_editor_name/last_edited_date；ISoftDelete = deleted_date(BIGINT epoch ms)+deleted_by；部分唯一索引 `CREATE UNIQUE INDEX ... WHERE col IS NOT NULL`；CHECK 约束；`COMMENT ON`（见 V1__create_student.sql）。
  - entity 约定：lombok `@Data`+`@EqualsAndHashCode(callSuper=true)`+`@Access(FIELD)`，`extends BaseAuditableEntity`，枚举 `@Enumerated(STRING)`（见 Run.java）。
  - DAO 约定：`extends BaseEntityDao<T>`，`@Repository`，派生查询 + `@Modifying @Query` CAS（见 RunDao.java）。
  - enum 约定：简单 enum + `@Schema`（见 RunTerminalReason.java）。
- blocker（base-schema）：
  - `main`/本分支 `db/migration/` 仅 `V1__create_student.sql`；`oc_run`/`oc_task`/`oc_requirement` 等基表 DDL 不在任何已跟踪 migration。
  - 完整链 V1–V20（`V2__task_run.sql` 内 `CREATE TABLE oc_run`）仅在未合并分支 `worktree-compensation-migration` / origin `feat/compensation-logging`。
  - 影响：EXE-001 新表（workspace/execution/step/checkpoint/effect/observation）自洽可建；但 `oc_run` ALTER、Run 实体新增列的 `validate` 与并发测试，在全新 testcontainers DB（仅 V1）下 oc_run 不存在 → Spring 上下文校验 Run 实体失败 → DB 类测试无法运行。
- changedFiles：无（仅调研与文档追加）。
- nextAction：等待用户裁决 base-schema 来源（A 先把基线 schema 迁移并入本分支；B EXE-001 假定基表存在、测试仅限新表并显式记录缺口；或 C 拆前置任务）。裁决后按 EXE-001 scope 生成 enums/DDL/entity/dao/测试，分批 checkpoint。

### OBS-005 — PROGRESS

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：EXE-001（step 1/N：schema 契约）
- state：`IN_PROGRESS`
- baseHead：`4e291ce`
- currentHead：`46a0657`
- changedFiles：dto/enums 11 新增 + RunState 扩展（QUEUED/UNKNOWN）；db/migration V7（6 表）+ V8（oc_run 扩展）
- verification：
  - 方言决策：依用户指示“根据 entity 确认可用表/字段”，以实体为真相；DB=PostgreSQL 不变。base-schema 文件来源不再追查，V8 的 oc_run ALTER 假定基表存在（与 Run 实体一致）。
  - `./gradlew :sei-online-code-api:compileJava` 通过（exit 0；RunDto 警告为既有，无关）。
  - 无 `switch(RunState)` 穷举分支，QUEUED/UNKNOWN 加入安全。
  - validate-safe：本批未新增实体映射，V7 新表/V8 新列不破坏既有实体 ddl-auto=validate。
- evidence：commit `46a06577b2e7a8058332101cfe6f781b4d8077d3`；`git diff --cached --name-only` 14 文件；`git status --short` 干净。
- scope 决策：EXE-001 的 `docs/db/` 范围由权威 Flyway 迁移 `db/migration/V7、V8` 承载（项目 schema 实际由 db/migration 管理，见 application.yaml），不在 docs/db 重复，避免双写漂移。
- 待验证风险：oc_run 基线 DDL 不可见；若其 `state` 列有 CHECK 约束限制旧枚举集合，需在基表可用时放宽以容纳 QUEUED/UNKNOWN（实体层无 CHECK，不影响编译）。
- nextAction：EXE-001 step2 生成 6 个新 entity + 扩展 Run 实体（列与 V7/V8 严格一致），再 step3 DAO、step4 测试；各步 checkpoint。

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
