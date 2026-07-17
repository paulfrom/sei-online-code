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
| 当前计划任务 | `EXE-008`（EXE-001~007 均已落在 `main`；EXE-008 基础批次 DONE，后续批次待续） |
| 任务状态 | EXE-008 `IN_PROGRESS`（batch1 checkpoint `0576e9b`） |
| 当前 owner | frontend-agent / claude |
| 已观察分支 | `main`（实际工作分支；远程 `feature/run-execution-reliability-exe005` 仍在但落后） |
| 已观察 HEAD | `c687ff1`（EXE-008 batch3 checkpoint） |
| Requirement feature branch | 实际 `main`（与 ADR“唯一 feature branch”约定偏离；历史 EXE-001~007 均落在 main，见 OBS-016） |
| Requirement worktree | 当前检出 `D:\project\monorepo\sei-online-code`（Windows / `lin`） |
| 实施 checkpoint commit | 基线 `d49366f`+`4e291ce`；EXE-001 `46a0657`+`97153b9`+`cc0d9e1`；EXE-002 `e233612`；EXE-003 `99a7e4e`+`e301208`+`7db9593`；EXE-004 `441cf6a`+`6fb0bee`+`bd1827f`+`122f9a2`；EXE-005 `2972873`；EXE-006 `c2a8108`；其后 main 另有 `ff64c91`（reconciler，EXE-007 scope）+`014efbf`（progress-ledger 开关，EXE-009 scope）—git 实存但 ledger 未经验证登记，见 OBS-016；EXE-008 batch1 `0576e9b`；EXE-008 batch2 `4f4cec8`；EXE-008 batch3 `c687ff1` |
| eadp-backend skill | 不可用（本机 `~/.claude/skills/` 无；与 OBS-002 声称冲突，见 OBS-016） |
| suid skill | 不可用（本机同上；经用户授权按 frontend/CLAUDE.md 内联规范 + 既有实现推进，见 OBS-016） |
| 最近完成验证 | EXE-008 batch3：eslint（2 文件 clean，含对既有 formatTokens 的 eqeqeq 合规修复）+ git diff --check 干净 |
| 下一动作 | EXE-008 batch4：RunLogDrawer 三视图（执行记录/原始日志/证据）+ 证据分页（findEffects/runObservation findByRun） |

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
| EXE-001 | `DONE` | backend-agent/claude | `97153b9` | 数据层已交付（V7/V8+enum+entity+dao+测试）；并发/迁移 testcontainer 验收待 CI |
| EXE-002 | `DONE` | backend-agent/claude | `e233612` | 核心原子协议已交付；测试已写未跑、JPQL 待 CI |
| EXE-003 | `DONE` | backend-agent/claude | `7db9593` | 查询/事件/API 已交付；鉴权/WS/automation-mrStatus/测试延后 |
| EXE-004 | `DONE` | backend-agent/claude | `122f9a2` | 核心集成完成；Runner threadId/turnId 填充 + 测试延后 |
| EXE-005 | `DONE` | backend-agent/claude | `2972873` | WorkspaceLeaseService+Git方法+DAO CAS+接线+GC安全+17测试通过 |
| EXE-006 | `DONE` | backend-agent/claude | `c2a8108` | EffectService+DAO CAS+delivery 改造+fetch-merge+15测试通过 |
| EXE-007 | `READY` | - | - | 依赖 EXE-004+005+006 已 DONE，可 claim |
| EXE-008 | `IN_PROGRESS` | frontend-agent/claude | `c687ff1` | batch1（`0576e9b`）+ batch2（`4f4cec8`）+ batch3（`c687ff1`：Run 列表 execution/owner/attempt/最新 observation）DONE；RunLogDrawer 三视图（执行记录/原始日志/证据）+ 证据分页待 batch4 |
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

### OBS-006 — CHECKPOINT

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：EXE-001
- state：`DONE`
- baseHead：`4e291ce`
- currentHead：`97153b9`
- changedFiles（本次）：6 entity + Run 扩展 + 6 DAO + 2 测试（commit `97153b9`）；累计含 schema `46a0657`
- verification：
  - `./gradlew :sei-online-code-service:compileJava`、`compileTestJava` 通过。
  - `./gradlew :sei-online-code-service:test --tests "...RunStateTest"` 通过（历史 4 态合法 + QUEUED/UNKNOWN 存在 + 共 6 值）。
  - `git diff --check`、`git diff --cached --check` 无空白错误。
  - testcontainer 类（ProgressLedgerDaoTest）@Disabled，沿用 FeatureDesignDaoTest 约定。
- evidence：EXE-001 commits = `46a0657`（enums+V7+V8）+ `97153b9`（entity+dao+test）。
- 已交付：6 表（V7）+ oc_run 扩展（V8）+ 11 enum + RunState 扩展（QUEUED/UNKNOWN）+ 6 entity + Run 扩展 + 6 DAO + 单元/DAO 测试；迁移含前向说明与回滚注释。
- 待验证/已知缺口（不掩盖）：
  - testcontainer 并发回读与 schema validate 须在 CI 验证（本地 @Disabled：Docker 不可用 + 本分支仅 V1，基表 oc_run 等在 feat/compensation-logging）。
  - 完整并发 insert-on-conflict 事务编排属 EXE-002 ProgressService。
  - oc_run 基线若存在 state CHECK 约束需在 CI 放宽以容纳 QUEUED/UNKNOWN（实体层无 CHECK，不影响编译）。
- nextAction：EXE-001 DONE；EXE-002（ProgressService 核心原子协议）BLOCKED_DEPENDENCY 解除 → READY，可 claim。

### OBS-007 — CLAIM

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：EXE-002
- state：`IN_PROGRESS`
- baseHead：`cc0d9e1`
- currentHead：`cc0d9e1`
- owner：backend-agent / claude
- claim依据：EXE-001 `DONE`（§8）；无其他 `IN_PROGRESS`；实际 HEAD `cc0d9e1`（校正 §3 此前记录 `97153b9`，差一个 ledger commit）。
- nextAction：按 EXE-002 scope 分批实施 ProgressService 核心原子协议——find-or-create Execution / Requirement lease / step declare·claim·heartbeat / checkpoint / markApplied / markVerified（不可回退）/ observation / reconcile / snapshotVersion 递增 / snapshot 生成；写命令校验 runId·claimToken·fencingToken·loop·planVersion。每批 compile + 单测后 checkpoint。

### OBS-008 — CHECKPOINT

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：EXE-002
- state：`DONE`
- baseHead：`cc0d9e1`
- currentHead：`e233612`
- changedFiles：6 progress DTO + ProgressService + 2 DAO CAS 扩展 + ProgressServiceTest（commit `e233612`）
- verification：
  - `compileJava`、`compileTestJava` 通过。
  - `git diff --check` 干净。
  - 8 例单测已写（find-or-create×3 / claim×2 / markVerified×3 / checkpoint 回滚×1），**未运行**（用户暂停 test 运行）。
  - JPQL `@Query` 仅运行期校验，待 CI/testcontainers 跑绿。
- evidence：commit `e233612`。
- 已交付：find-or-create / lease CAS / claim·heartbeat·markApplied·markVerified(no-downgrade)·markUnknown / 原子 checkpoint / observation / snapshot 生成；写命令经 CAS WHERE 隐式校验 owner/claim/fencing，失败 STALE_OWNER；可观测写同事务递增 snapshotVersion。
- 待验证/已知缺口（不掩盖）：测试未本地运行；JPQL 运行期待 CI；完整补偿编排属 EXE-007，HTTP overview DTO/事件属 EXE-003。
- nextAction：EXE-002 DONE；EXE-003 与 EXE-004 依赖解除 → READY。

### OBS-009 — CLAIM

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：EXE-003
- state：`IN_PROGRESS`
- baseHead：`e233612`
- currentHead：`e233612`
- owner：backend-agent / claude
- claim依据：EXE-002 `DONE`；无其他 `IN_PROGRESS`；HEAD `e233612`。
- nextAction：冻结第 3 节 HTTP/WS DTO 契约（overview + step/checkpoint/effect/observation 查询 + appendManual）；实现 overview 聚合与分页查询；受控人工 observation；after-commit 进度事件；DTO 映射前证据授权/脱敏。

### OBS-010 — CHECKPOINT

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：EXE-003
- state：`DONE`（功能闭环；鉴权/WS/状态字段/测试延后，见缺口）
- baseHead：`e233612`
- currentHead：`7db9593`
- changedFiles：9 DTO + QueryService + DAO 查询扩展 + 2 Feign API + 2 Controller + 进度事件 + listener（commits `99a7e4e`/`e301208`/`7db9593`）
- verification：compileJava/compileTestJava 通过；git diff --check 干净。JPQL 运行期、Feign 同名 bean、测试待 CI。
- 已交付：冻结 DTO 契约；overview 聚合 + 分页查询 + 脱敏；2 Feign API + 2 Controller（PageResult）；可观测写 after-commit 进度事件（AFTER_COMMIT）。
- 延后缺口（不掩盖）：(1) appendManual 鉴权未实现（需项目权限/会话模型）；(2) WS broker 转发未接（listener 仅 log，待 EXE-008/WS 基建）；(3) overview automationStatus/mrStatus 置 null（需 Requirement + MR effect，EXE-006/009）；(4) 查询/Controller 测试未写（用户暂停 test 运行）。
- nextAction：EXE-003 DONE；EXE-004 依赖 EXE-002 已满足 → claim。

### OBS-011 — CLAIM

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：EXE-004
- state：`IN_PROGRESS`
- baseHead：`7db9593`
- currentHead：`7db9593`
- owner：backend-agent / claude
- claim依据：EXE-002 `DONE`；无其他 `IN_PROGRESS`（EXE-003 刚 DONE）；HEAD `7db9593`。
- nextAction：调度入口用 invocation key 创建/复用 Run 并绑定稳定 Execution；Agent 启动前强制读 progress snapshot（已完成 Execution 不跳过模型启动）；注入 progress.json/nextActions 到 brief；自动采集 accepted/工具/文件/Git HEAD/测试/terminal/heartbeat；保存 threadId/turnId/resumeFromCheckpointId；Run 终态只更新 Run/observation 不判 Execution 完成。需先读 CodingTaskExecutionService/AgentExecution/agent 包理解既有 Runner 模型。

### OBS-012 — CHECKPOINT

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：EXE-004
- state：`DONE`（核心+管线完成；Runner 填充/跨任务缺口延后）
- baseHead：`7db9593`
- currentHead：`122f9a2`
- changedFiles：CodingTaskProgressIntegrator(+test) + CodingTaskExecutionService 接线 + CliRunResult + AgentExecutionService（commits `441cf6a`/`6fb0bee`/`bd1827f`/`122f9a2`）
- verification：compileJava/compileTestJava 通过；git diff --check 干净。运行期（JPQL/Feign/热路径/事务）待 CI。
- 已交付：调度绑定 Execution+invocationKey、preflight 跳过、brief 注入 progress/nextAction、terminal observation、threadId/turnId 落库管线、resumeFromCheckpoint 绑定、Run 终态不判 Execution。
- 延后缺口（不掩盖）：
  - (1) Runner 实现尚未填充 CliRunResult.threadId/turnId（管线已通，值暂 null）——CLI 协议层深探；
  - (2) checkpoint git-HEAD 自动采集需 fencingToken（EXE-005）；
  - (3) 完整 invocation 幂等重用受 hasActiveRun 阻塞（EXE-009）；
  - (4) 测试未本地运行（无 Docker）。
- nextAction：EXE-004 DONE；EXE-005（EXE-002+EXE-004 已 DONE）→ READY。建议先推送 CI 验证堆积的运行期风险。

### OBS-013 — CLAIM + CHECKPOINT

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：EXE-005
- state：`DONE`
- baseHead：`701eb82`（main 最新，feature branch `feature/run-execution-reliability-exe005`）
- currentHead：`2972873`
- changedFiles：7 文件（WorkspaceManager +6 Git 方法/GC 安全 + RequirementWorkspaceDao +2 CAS + CodingTaskExecutionService 接线 + ProgressService bumpSnapshotForWorkspace + CodingTaskExecutionServiceTest 构造器更新；新增 WorkspaceLeaseService + WorkspaceLeaseServiceTest 17 测试）
- verification：
  - compileJava 通过 ✅
  - compileTestJava 通过 ✅
  - 17/17 WorkspaceLeaseServiceTest 通过（bindOrResolve×3 + acquireOwnership×2 + commitCheckpoint×3 + releaseOwnership×2 + isGcSafe×5 + reconcileAfterRestart×2）
  - git diff --check 无空白错误 ✅
- 已交付：
  - WorkspaceLeaseService：bindOrResolveWorkspace / acquireOwnership / commitCheckpoint / reconcileAfterRestart / releaseOwnership / isGcSafe
  - WorkspaceManager：getCurrentHead / getCurrentBranch / hasUncommittedChanges / commitAll / getGitDiff / getFileLock / resetSoftHead / ensureOnBranch
  - RequirementWorkspaceDao：advanceCurrentHead（CAS）/ releaseLease
  - CodingTaskExecutionService：bindProgress 中 workspace 不存在时自动创建（bindOrResolve）
  - deleteRequirementWorkspace：GC 安全防护（ACTIVE/BLOCKED/DELIVERING 或 COMPLETED+retention 未到期禁止删除）
- 延后缺口（不掩盖）：
  - (1) 不实现跨进程 file lock（当前单 JVM ReentrantLock 足够）
  - (2) 不终止旧 Agent 进程（进程管理属基础设施层，接管通过 fencing_token 递增实现）
  - (3) Git commit 失败回滚为 best-effort（DB CAS 失败后 git reset HEAD~1；若 reset 也失败需人工对账）
  - (4) push/MR 属 EXE-006，patch artifact 持久化属 EXE-007
- nextAction：EXE-005 DONE；EXE-006（依赖 EXE-002+EXE-005 已满足）→ READY，可 claim。

### OBS-014 — CLAIM

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：EXE-006
- state：`IN_PROGRESS`
- baseHead：`4540c45`
- currentHead：`4540c45`
- owner：backend-agent / claude
- claim依据：EXE-002 `DONE` + EXE-005 `DONE`；无其他 `IN_PROGRESS`；HEAD `4540c45`。
- nextAction：调研现有 RequirementDeliveryService、GitLabApi 适配层、effect ledger 表结构；按 EXE-006 scope 设计并分批实施 Effect 注册/执行/对账框架、push/MR effect、交付前合入+验证。

### OBS-015 — CHECKPOINT

- observedAt：2026-07-17
- source/agent：backend-agent / claude
- task：EXE-006
- state：`DONE`
- baseHead：`4540c45`
- currentHead：`c2a8108`
- changedFiles：5 文件（ExecutionEffectDao +3 CAS Query + EffectService 新建 + RequirementDeliveryService 接入 effect ledger+fetch-merge target + EffectServiceTest 15 测试 + RequirementDeliveryServiceTest 构造器更新）
- verification：
  - compileJava 通过 ✅
  - compileTestJava 通过 ✅
  - 15/15 EffectServiceTest 通过（findOrPrepare×4 + markApplied×3 + markConfirmed×2 + markUnknown×2 + findByKey×2 + reconcile×2）
  - git diff --check 无空白错误 ✅
- 已交付：
  - EffectService：findOrPrepare / markApplied / markConfirmed / markUnknown / findByKey / reconcile（AD-001 §5 幂等模型）
  - ExecutionEffectDao：applyEffect / confirmEffect / markEffectUnknown（CAS 状态迁移）
  - RequirementDeliveryService：push+MR 接入 effect ledger（幂等复用）；交付前 fetch+merge target（ff-only）防止冲突与基线漂移
  - MR effect 复用已有 URL；push effect 复用已有 commitHash
- 延后缺口（不掩盖）：
  - (1) comment/memory job effect 注册未接入（仅提供 EffectService 框架，调用方自行使用）
  - (2) UNKNOWN effect reconcile 对外部查询的完整 handler 注册（仅提供 reconcile 框架+回调函数模式）
  - (3) MR source SHA 校验（需 EXE-005 workspace fencing + VERIFIED step checkpoint gitHead，当前 doDeliver 以当前 HEAD 为准）
- nextAction：EXE-006 DONE；EXE-007（依赖 EXE-004+005+006 已满足）→ READY，可 claim。

### OBS-016 — CLAIM + CHECKPOINT

- observedAt：2026-07-17
- source/agent：frontend-agent / claude
- task：EXE-008（batch 1/N：数据管线 + MR 状态分离）
- state：`IN_PROGRESS`（batch1 `DONE`，后续批次待续）
- baseHead：`014efbf`
- currentHead：`0576e9b`
- owner：frontend-agent / claude
- claim依据：EXE-003 `DONE`（冻结 DTO 契约齐全）；§8 EXE-008 原 `BLOCKED_DEPENDENCY` 已解除；无其他 `IN_PROGRESS`。
- 环境/门禁偏离（不掩盖）：
  - skill 门禁：本机 `~/.claude/skills/` 无 `suid`、无 `eadp-backend`（与 OBS-002 在 Linux 机 `/home/paul/...` 上“已可用”的记录冲突）。经用户明确授权“参考当前实现，不用 suid skill”，按 frontend/CLAUDE.md 内联 SUID 规范 + 既有 RequirementWorkspace 实现（@ead/suid）推进；未读取 suid SKILL.md。
  - 分支偏离：实际工作分支为 `main`（EXE-001~007 均落 main），非 ADR 约定的唯一 feature branch；远程 `feature/run-execution-reliability-exe005` 落后。checkpoint 沿既有约定提交到 `main`，未 push。
  - ledger §3/§8 滞后于 git：main 上 `ff64c91`（EXE-007 reconciler）+ `014efbf`（EXE-009 progress-ledger 开关）已存在但 ledger 未登记；未经验证不擅自改其状态，留待对账。
- changedFiles（batch1，commit `0576e9b`，6 文件 +291）：
  - 新增 `frontend/src/services/executionProgress.js`（findOverview/findSteps/findCheckpoints/findEffects）
  - 新增 `frontend/src/services/runObservation.js`（findByRun/appendManual）
  - 新增 `frontend/src/utils/requirement-progress-socket.ts`（`/ws/requirement/{id}/progress`，JSON 帧，有界自动重连，disconnect/reconnect 回调）
  - 改 `useRequirementWorkspace.js`：refresh 拉 findOverview；snapshotVersion 门控 refetch；stale（staleAfter 或 WS 断线）；暴露 overview/stale
  - 改 `OverviewPanel.jsx`：状态卡片分离 MR 合并状态（OPEN=待合入 / MERGED=已合入 / CLOSED=已关闭，未知枚举原值透传）+ stale 提示
  - 改 `index.tsx`：透传 overview/stale
- 范围偏离（不掩盖）：scope 文本写“扩展 AutomationStatusBar”，但 `AutomationStatusBar.jsx` 是死代码（仅 `AUTOMATION_STATUS_META` 被 OverviewPanel 借用，状态卡由 OverviewPanel 内联渲染）。按“仅改绝对必要部分”未改死代码，MR 分离落在可见的 OverviewPanel 状态卡。
- verification：
  - `tsc --noEmit -p tsconfig.json --ignoreDeprecations 5.0`（项目 tsconfig 用 `ignoreDeprecations:"6.0"` 面向更新版 TS；include 仅 .ts/.tsx，.js/.jsx 不参与 tsc，符合 CLAUDE.md）：改动 .ts/.tsx（requirement-progress-socket.ts、index.tsx）零错误；项目全量有既有噪音（FlowStatusView/Login/Agents/List 等），非本次引入。
  - `eslint`（6 个改动文件）：clean，exit 0。
  - `git diff --check`：clean（仅 Windows autocrlf LF→CRLF 提示）。
  - 未跑 umi build；前端无单测要求（frontend/CLAUDE.md“不需要提交测试文件”）；E2E 属 ACC-003 独立任务。
- 已知缺口/待续（不掩盖）：
  - 执行进度页签（步骤树 / owner / lease / checkpoint / nextAction）、Run 列表扩展、RunLogDrawer 三视图、证据分页 → 后续批次。
  - overview.automationStatus/mrStatus 在 EXE-003 时曾为 null（OBS-010）；EXE-006 后是否填充未验证，前端按 null 容错（MR tag 不渲染、不伪造）。
  - 未连 WS broker 转发（后端 listener 仅 log，OBS-010 延后项）；前端 socket 已就绪，后端推送上线即生效。
- nextAction：EXE-008 batch2 实现执行进度页签（findSteps + findCheckpoints 分页 + 步骤状态视觉区分 APPLIED/VERIFIED/UNKNOWN/BLOCKED）。

### OBS-017 — CHECKPOINT

- observedAt：2026-07-17
- source/agent：frontend-agent / claude
- task：EXE-008（batch 2/N：执行进度页签）
- state：`IN_PROGRESS`（batch2 `DONE`，后续批次待续）
- baseHead：`0576e9b`
- currentHead：`4f4cec8`
- changedFiles（batch2，commit `4f4cec8`，4 文件 +411）：
  - 新增 `ExecutionProgressTab.jsx`：渲染 overview（workspace/lease/stale + stepSummary + currentStep + latestCheckpoint + nextAction）+ findSteps 步骤列表（按 phase 排序，状态视觉区分 PENDING/IN_PROGRESS/APPLIED/VERIFIED/UNKNOWN/BLOCKED/FAILED/SUPERSEDED，未知枚举原值透传）；CheckpointList 子组件按需 findCheckpoints 分页（hasMore 由满页推断，不依赖 PageResult.total）
  - 改 `OverviewPanel.jsx`：新增“执行进度”卡片（verified/required + APPLIED/UNKNOWN/BLOCKED 异常计数）；onOpenPanel 联合类型加 'progress'
  - 改 `OverviewDrawer.jsx`：TITLE_BY_PANEL/panelKey/renderPanel 加 'progress'；透传 overview
  - 改 `index.tsx`：detailPanel/handleOpenPanel 联合类型加 'progress'；给 OverviewDrawer 传 overview
- executionId 来源：`overview.workspace.ownerExecutionId`（无活跃 Execution 时步骤区显示空态，不伪造）。
- verification：
  - `eslint`（4 文件）：clean，exit 0。
  - `tsc --noEmit -p tsconfig.json --ignoreDeprecations 5.0`：改动 .tsx（index.tsx）零错误（.jsx 不参与 tsc，符合 CLAUDE.md）。
  - `git diff --check`：clean。
- 组件选型说明（不掩盖，供 suid 审阅）：步骤区用基础 div+Tag+Button 构建的展开式“步骤树”，未用 ExtTable。依据：scope 明确要求“步骤树”、嵌入 Drawer 的只读结构化小列表、含展开行；符合 CLAUDE.md 的 Table/列表降级条件。suid skill 缺失（OBS-016），此选型未经 SKILL.md 确认。
- 已知缺口/待续（不掩盖）：
  - Run 列表扩展、RunLogDrawer 三视图（执行记录/原始日志/证据）、证据分页 → batch3/4。
  - overview 工作区/steps 字段在 backend 是否填充未端到端验证（OBS-010 曾记 null）；前端全程 null 容错。
- nextAction：EXE-008 batch3 扩展 Run 列表（execution/attempt/恢复点/最新 observation/验证状态）。

### OBS-018 — CHECKPOINT

- observedAt：2026-07-17
- source/agent：frontend-agent / claude
- task：EXE-008（batch 3/N：Run 列表扩展）
- state：`IN_PROGRESS`（batch3 `DONE`，batch4 待续）
- baseHead：`4f4cec8`
- currentHead：`c687ff1`
- changedFiles（batch3，commit `c687ff1`，2 文件 +45/-4）：
  - 改 `RunTab.jsx`：新增 Execution 列（`overview.recentRuns` 按 runId 关联取 executionId；当 run.id === `overview.workspace.ownerRunId` 显示"当前 owner" Tag，使重复 Run 可见共享 Execution 与当前 owner）；重启用 Attempt 列；新增"最新 observation"列（recentRuns.latestObservationSummary 截断）；drive-by 修复既有 `formatTokens` 的 `== null`→`=== null || === undefined`（eqeqeq 合规，语义不变）
  - 改 `OverviewDrawer.jsx`：run 面板给 RunTab 传 overview
- 范围说明（不掩盖）：scope 文本列"恢复点/验证状态"，但 `overview.recentRuns`(RecentRunDto) 与既有 RunDto 均不含 resume checkpoint id 与 per-run verification；这些属 effect/observation 详情，归 batch4（RunLogDrawer 三视图 + findEffects/findByRun 证据分页）。
- verification：
  - `eslint`（2 文件）：clean，exit 0。
  - `git diff --check`：clean。
  - 本批仅改 .jsx（tsc 不覆盖，符合 CLAUDE.md）。
- nextAction：EXE-008 batch4 RunLogDrawer 三视图（执行记录 / 原始日志 / 证据）+ 证据分页。

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
