# Run / Execution 进度账本详细验收方案

状态：APPROVED TEST DESIGN / NOT EXECUTED

文档版本：1.0

编制日期：2026-07-17

验收对象：Run 重复调用下的进度发现、幂等续作、唯一 Requirement worktree、Effect/MR 交付及前端可观测

## 1. 验收结论先行

本功能不能通过“编译成功”“Agent 摘要说已完成”或 Mockito 单测直接验收。最终必须在真实
PostgreSQL、真实 Git worktree、可控 Runner、可查询的外部副作用替身以及运行中的前端页面上验证。

截至编制本文时，仓库观测状态为：

| 项目 | 当前观测 |
|---|---|
| 分支/HEAD | `main@7218faa` |
| 已合入内容 | EXE-001～EXE-004 的主体代码 |
| 计划未完成 | EXE-005～EXE-009、ACC-001～003 |
| 数据库迁移 | 仅 V1、V7、V8；V7/V8 依赖的基础表迁移链不完整 |
| 关键数据库测试 | `ProgressLedgerDaoTest` 仍为 `@Disabled` |
| 前端执行进度页 | 尚未满足 EXE-008 完成条件 |
| 未提交改动 | RequirementComment API/Controller 两个文件，验收时必须保留并确认归属 |

因此，如果现在执行发布验收，结论必须是：

```text
BLOCKED / FAIL — 尚未达到验收入口条件
```

本文件定义最终候选版本完成后的验收方法，不代表当前代码已经通过。

## 2. 测试依据

- [ADR-001](../architecture/adr-001-run-execution-progress-ledger.md)
- [进度账本数据模型](../architecture/run-execution-progress-data-model.md)
- [实施任务板](../plans/run-execution-reliability-task-board.md)
- [编码交接](../plans/run-execution-reliability-handoff.md)
- [机器可读计划](../plans/run-execution-reliability-plan.json)

验收遵循以下质量原则：

- ISO/IEC 25010：重点覆盖功能适合性、可靠性、安全性、性能效率、可用性和可维护性。
- ISO/IEC 29119：保持需求—风险—测试—证据的可追溯关系。
- 所有核心场景必须运行时验证；静态审查只能作为补充证据。
- test-agent 与 coding agent 分离，避免实现者自评偏差。

本文不声明取得任何正式标准认证。

## 3. 验收范围

### 3.1 必须验收

1. Execution、workspace、step、checkpoint、effect、observation 的持久化和唯一约束。
2. Execution find-or-create、Requirement lease、step claim 和 fencing CAS。
3. Run preflight、稳定 executionKey/invocationKey、进度快照和已完成步骤跳过。
4. 三类失败：
   - Agent 未执行；
   - 部分执行后失败；
   - 实际完成但 Run 超时或终态丢失。
5. Requirement 唯一 worktree/feature branch、Git checkpoint commit、HEAD CAS 和接管。
6. Effect `UNKNOWN` 对账、幂等重试和人工确认边界。
7. push、MR 创建/更新幂等，且平台不自动 merge。
8. overview、step、checkpoint、effect、observation API。
9. Requirement 级进度 WebSocket、乱序去重和轮询降级。
10. 前端步骤树、Run 时间线、证据、stale、MR 状态和刷新恢复。
11. 权限、审计、脱敏、路径安全和敏感信息保护。
12. 迁移、升级、回滚开关和历史 Run 兼容。

### 3.2 不属于本次验收

- GitLab 人工审核是否及时完成。
- MR 合并后的生产发布流程。
- Agent 生成业务代码本身的业务正确性。
- 不同 Requirement 的业务验收内容。
- 对无法查询且不支持幂等的第三方系统承诺 exactly-once。

## 4. 角色与职责

| 角色 | 职责 | 禁止事项 |
|---|---|---|
| test-agent | 执行 ACC-001～003、保存证据、给出结论 | 不修改业务实现来“让测试通过” |
| backend-agent | 修复验收发现的后端缺陷 | 不修改验收报告结论 |
| frontend-agent | 修复 UI、WS、权限或可访问性缺陷 | 不以静态页面截图代替交互验证 |
| scm-agent | 冻结候选 SHA、创建/更新 MR | 不自动 merge |
| reviewer/CODEOWNER | 审核证据与残余风险 | 不以口头确认替代缺失证据 |

验收发现缺陷后，test-agent 记录缺陷并结束对应 case。修复必须形成新 checkpoint commit，随后只重跑
受影响 case 和规定的回归集。

## 5. 验收入口条件

以下条件必须全部满足，才允许把 ACC-001 标记为 `IN_PROGRESS`：

### 5.1 计划与 Git

- EXE-001～EXE-009 在交接任务表中均为 `DONE`。
- 候选代码位于唯一 requirement feature branch。
- 工作区无未知改动；已知用户改动有明确归属和 commit。
- 记录完整候选 SHA，后续所有报告绑定该 SHA。
- `git diff --check` 通过。
- MR 尚未自动合并。

### 5.2 Skill 与独立性

- test-agent 已读取根目录及对应模块 AGENTS.md。
- 后端修复仍遵循 `eadp-backend`，前端修复仍遵循 `suid`。
- test-agent 不是最后一次实现提交的作者，或至少由另一独立 Agent 复核运行结果。

### 5.3 数据库

- 从空 PostgreSQL 15 数据库可以顺序执行完整迁移链。
- V7/V8 依赖的 `oc_run`、`oc_requirement`、`oc_coding_task` 等基础表迁移已经进入当前分支。
- `ddl-auto=validate` 能在完整迁移后启动。
- `ProgressLedgerDaoTest` 等关键测试没有 `@Disabled`、条件跳过或环境豁免。
- Docker/Testcontainers 在验收环境可用。

### 5.4 Runner、Git 与外部系统

- 提供可注入故障点的 fake Runner，能控制 accepted、文件修改、checkpoint、terminal、超时和晚到回调。
- 提供临时 Git bare origin 和真实 requirement worktree。
- 提供隔离 GitLab 测试项目，或行为等价且可查询 branch/MR 的受控服务替身。
- 禁止使用真实生产 GitLab token、生产仓库或生产工作区。

### 5.5 前端

- 后端候选服务可以启动。
- 前端使用 `pnpm` 安装并可启动。
- 有至少两个测试身份：授权 reviewer 和无权限用户。
- Chrome DevTools 隔离上下文可用；不可用时必须提供等价浏览器 E2E 工具，不能退化为只读源码。

任一入口条件不满足，验收状态为 `BLOCKED`，不得把未执行 case 计为通过。

## 6. 候选环境与固定参数

### 6.1 推荐环境

| 组件 | 要求 |
|---|---|
| Java | 21 |
| Gradle | 仓库 `backend/gradlew`，Gradle 8.5 |
| PostgreSQL | Testcontainers PostgreSQL 15 |
| Node.js | >= 18 |
| 包管理器 | pnpm，使用现有 lockfile |
| 浏览器 | Chrome 隔离测试上下文 |
| Git | 临时 bare origin + 单独 requirement worktree |
| 时钟 | 服务端 UTC；前端本地显示，断言使用 ISO 8601 |

### 6.2 固定测试参数

```text
claim 并发数                  = 50
Requirement lease            = 30 秒
Step lease                   = 15 秒
活动态轮询                   = 5 秒
非活动态轮询                 = 30 秒
WS 正常端到端可见时间         <= 3 秒
overview 基准数据             = 20 executions / 100 steps / 1000 observations
overview p95                  <= 500 ms（本地验收环境）
分页默认 rows                 = 20
允许自动重试的 UNKNOWN effect = 仅可查询或支持相同幂等键者
```

若实现配置不同，应在执行前冻结实际值并同步更新期望，不能在看到结果后调整阈值。

## 7. 测试数据

每轮验收创建独立 `testRunId`，所有数据和外部资源名称带该 ID，避免与其他验收互相污染。

### 7.1 Requirement fixtures

| Fixture | 用途 |
|---|---|
| `REQ-A` | 主流程、重复 Run、部分失败、前端查看 |
| `REQ-B` | 与 REQ-A 并行，证明不同 Requirement 可并发 |
| `REQ-C` | loop 切换和旧 fencing token |
| `REQ-D` | MR delivery、push/MR 超时对账 |
| `REQ-LEGACY` | 无 executionId 的历史 Run 兼容 |

每个 Requirement 至少包含：

- projectId、requirementId、activeLoopId；
- execution plan version；
- requirement workspace 和 feature branch；
- 两个 required implement step；
- 一个 required verify step；
-一个 optional step；
- 至少两个 Run。

### 7.2 测试身份

| 身份 | 权限 |
|---|---|
| `reviewer` | 查看需求进度、查看脱敏证据、追加人工 observation |
| `project-user` | 查看允许的概要，不可执行人工对账 |
| `outsider` | 无该 Requirement 访问权限 |

### 7.3 故障注入点

Runner/服务替身必须支持：

```text
BEFORE_ACCEPTED
AFTER_CLAIM
AFTER_FILE_CHANGE
AFTER_CHECKPOINT
AFTER_APPLIED
AFTER_VERIFIED_BEFORE_TERMINAL
AFTER_PUSH_BEFORE_RESPONSE
AFTER_MR_CREATE_BEFORE_RESPONSE
LATE_TERMINAL_CALLBACK
CHECKPOINT_DB_FAILURE
WS_DISCONNECT
WS_DUPLICATE_EVENT
WS_REORDER_EVENT
```

## 8. 证据和报告规范

每次验收在以下目录保存证据：

```text
docs/test-reports/run-execution-reliability/<candidate-sha>/
  manifest.md
  acc-001-concurrency.md
  acc-002-recovery-delivery.md
  acc-003-frontend.md
  commands/
  junit/
  sql/
  screenshots/
  network/
  performance/
```

`manifest.md` 必须包含：

- candidate SHA、branch、执行人和时间；
- Java/Gradle/PostgreSQL/Node/pnpm/浏览器版本；
- 配置摘要，不包含凭证；
- 每条 case 的 PASS/FAIL/BLOCKED；
- 缺陷编号和修复 SHA；
- 最终结论。

每条 case 至少保留：

1. 准备数据或 fixture ID；
2. 实际执行命令/HTTP 请求/浏览器操作；
3. 关键日志；
4. 数据库查询结果；
5. Git HEAD/branch/MR 回执（适用时）；
6. expected 与 actual；
7. 绑定的 candidate SHA。

禁止使用“代码看起来正确”“Agent 表示成功”“测试理论上可过”作为 PASS 证据。

## 9. 需求与测试追踪

| ADR 不变量/需求 | 验收 case |
|---|---|
| 一个 Requirement 一个 workspace | WKS-001、WKS-002 |
| 一个 executionKey 一个 Execution | CON-001 |
| 同一步骤只有一个 owner | CON-002 |
| 旧 claim/fencing token 不能写 | CON-003、WKS-004 |
| VERIFIED 不回退 | STM-001 |
| UNKNOWN 先对账 | REC-003、EFF-003、EFF-004 |
| observation/checkpoint 追加 | AUD-001、AUD-002 |
| MR source SHA 等于验证 HEAD | DLV-003 |
| 不自动 merge | DLV-005 |
| 前端以后端聚合为权威 | API-001、UI-001、UI-005 |
| WS 丢失仍最终收敛 | UI-003、UI-004 |
| 自动化完成不等于 MR merged | UI-006、DLV-005 |

## 10. ACC-001：原子性、数据库与 Worktree 验收

### MIG-001 空库完整迁移

前置：

- 新建空 PostgreSQL 15 容器；
- 不预建任何业务表。

步骤：

1. 按版本顺序执行当前分支全部 migration。
2. 启动 Spring test context，启用 `ddl-auto=validate`。
3. 查询 V7 六张表、V8 Run 增量列、索引、unique/check 约束。
4. 执行 `ProgressLedgerDaoTest`。

期望：

- migration 全部成功且可重复检查；
- Hibernate validation 成功；
- 不存在缺表、缺列、类型或索引名不匹配；
- 关键测试实际运行，不能 `@Disabled`。

证据：Flyway/迁移日志、schema 查询、JUnit XML。

### MIG-002 旧库升级

步骤：

1. 用功能上线前的完整 schema 建库并插入 legacy Run。
2. 执行新增 migration。
3. 验证 legacy Run 可读，新增字段允许迁移期空值。
4. 启动服务并读取 Run API。

期望：

- 历史数据不丢失；
- 不伪造 execution、checkpoint 或 VERIFIED 状态；
- 新 Run 必须写 executionId，legacy Run 明确标识未验证。

### MIG-003 回滚开关

步骤：

1. 在 shadow/new-write 模式生成进度记录。
2. 关闭 authoritative-ledger 写入口。
3. 读取已有 overview、observation 和 checkpoint。
4. 再次打开功能。

期望：

- 回滚开关不删除审计记录；
- 旧流程可以恢复服务；
- 再启用后 snapshotVersion 继续单调增长。

### CON-001 Execution find-or-create 竞态

步骤：

1. 对同一 executionKey 同时发起 50 个事务。
2. 所有事务使用相同 taskType、businessTaskId、loop、planVersion、inputHash。
3. 等待完成并查询 `oc_task_execution`。
4. 再使用同 key、不同 inputHash 请求一次。

期望：

- 数据库只有一条 Execution；
- 50 个调用返回同一个 Execution ID；
- 同 key 不同业务输入返回冲突，不覆盖原记录；
- 无未处理唯一约束异常。

数据库断言：

```sql
SELECT execution_key, COUNT(*)
FROM oc_task_execution
WHERE execution_key = :executionKey
GROUP BY execution_key;
-- COUNT(*) = 1
```

### CON-002 50 个 Run 并发 claim

步骤：

1. 创建一个 `PENDING` required step。
2. 50 个不同 Run 同时调用 claim。
3. 收集每个结果和最终 step。
4. 查询 CLAIM checkpoint 数量。

期望：

- 恰好一个返回 `OK`；
- 其余返回 `STALE_OWNER/ALREADY_CLAIMED` 等非成功结果；
- 最终只有一个 ownerRunId 和 claimToken；
- attemptCount 只按实际成功 claim 规则递增；
- 不产生多个有效 CLAIM checkpoint。

### CON-003 旧 token 晚到写入

步骤：

1. Run-A claim step，记录 claimToken/fencingToken。
2. 让 lease 过期，Run-B 完成对账并接管。
3. Run-A 使用旧 token 依次调用 heartbeat、checkpoint、markApplied 和 effect。

期望：

- 四类写入全部拒绝为 `STALE_OWNER`；
- snapshotVersion、currentHead、effect 数量不因旧写入变化；
- observation 可以记录“旧 owner 写入被拒绝”，但不能改变业务状态。

### CON-004 checkpoint 事务回滚

步骤：

1. fault injection 使 checkpoint insert 后、step latestCheckpoint 更新前失败。
2. 提交事务。
3. 查询 checkpoint、step 和 workspace snapshotVersion。

期望：

- 整个事务回滚；
- 不存在孤立 checkpoint；
- latestCheckpoint 和 snapshotVersion 不变化；
- 当前 step 保持故障前状态。

### CON-005 sequence 唯一和单调

步骤：

1. 并发追加同一 Execution 的 50 个 checkpoint。
2. 并发追加同一 Run 的 50 个 observation。
3. 查询 sequence_no。

期望：

- 无重复 sequence；
- 按 sequence 排序严格递增；
- 重试不会产生同一业务事件的重复记录，或能通过稳定事件 key 去重。

### STM-001 状态机和 VERIFIED 不回退

使用参数化测试覆盖：

```text
PENDING → IN_PROGRESS
IN_PROGRESS → APPLIED / UNKNOWN / BLOCKED / FAILED
APPLIED → VERIFIED / UNKNOWN / BLOCKED
UNKNOWN → APPLIED / VERIFIED / IN_PROGRESS / BLOCKED
BLOCKED/FAILED → IN_PROGRESS / SUPERSEDED
```

额外尝试：

- `PENDING → VERIFIED`；
- `VERIFIED → IN_PROGRESS`；
- 普通 CRUD 直接修改 VERIFIED；
- 旧 plan version 修改新步骤。

期望：

- 合法迁移成功；
- 非法迁移返回稳定错误且数据不变；
- VERIFIED 只能被新 plan version 取代，不能原地回退。

### SNP-001 snapshotVersion 单调性

步骤：

1. 记录初始 version。
2. 依次 claim、checkpoint、markApplied、markVerified、append observation。
3. 并发读取 overview。

期望：

- 每个成功可观测事务提交后版本递增；
- 失败/回滚事务不递增；
- 读取不会看到新版本配旧状态；
- 不同 Requirement 的版本互不影响。

### WKS-001 同 Requirement 唯一 worktree/branch

步骤：

1. 50 个 Run 同时请求 REQ-A workspace。
2. 记录返回路径、数据库 workspace ID 和 branch。
3. 检查物理目录和 Git worktree list。

期望：

- 只有一个 workspace 数据库记录；
- 所有 Run 返回同一路径和同一 feature branch；
- 不生成 Run/Execution 级额外 worktree。

### WKS-002 不同 Requirement 可并行

步骤：

1. REQ-A 与 REQ-B 同时 claim 并写各自文件。
2. 同时生成 checkpoint commit。

期望：

- 两个 Requirement 可以并行成功；
- 路径、branch、HEAD 和文件互不污染；
- Requirement 单写锁不是项目级全局锁。

### WKS-003 checkpoint commit 与 HEAD CAS

步骤：

1. Run-A 修改一组文件并 markApplied。
2. 生成 checkpoint commit。
3. 校验 parent SHA、changed files/hash 和测试证据。
4. CAS 推进 workspace currentHead。

期望：

- commit parent 等于 stepBaseCommit；
- actual Git HEAD 等于 checkpoint gitHead；
- workspace currentHead 只推进一次；
- 下一个 step 从该 currentHead 开始。

### WKS-004 HEAD 不匹配和旧进程接管

步骤：

1. 制造实际 Git HEAD 与数据库 currentHead 不一致。
2. 保留旧 Agent 进程或模拟旧进程晚到。
3. 新 Run 尝试接管。

期望：

- 在旧进程停止、文件锁和新 fencing token 获取前不能写；
- 不执行 reset/force checkout 覆盖未知文件；
- 状态进入 UNKNOWN/BLOCKED；
- 对账恢复后才允许继续。

ACC-001 通过条件：

- MIG-001～003、CON-001～005、STM-001、SNP-001、WKS-001～004 全部 PASS；
- 无关键 case skip；
- 无 CRITICAL/HIGH/MEDIUM 缺陷。

## 11. ACC-002：三类失败、Effect 与 MR 交付验收

### REC-001 调用失败，Agent 未执行

故障点：`BEFORE_ACCEPTED`。

步骤：

1. 创建 Run-A，但 Runner 在 accepted 前失败。
2. 查询 step、checkpoint、effect、worktree diff。
3. 启动 Run-B。

期望：

- Run-A 记录失败/UNKNOWN observation；
- 无 step claim、checkpoint、effect 或文件变化；
- Execution 保持可恢复；
- Run-B 从第一个 PENDING step 开始；
- 不把 Requirement 直接判失败。

### REC-002 过程失败，完成一部分

步骤：

1. Run-A 完成 step-1 VERIFIED。
2. step-2 修改文件并 checkpoint，但未完成验证。
3. 在 `AFTER_CHECKPOINT` 故障退出。
4. Run-B preflight 并继续。

期望：

- Run-B 跳过 step-1；
- step-2 从 checkpoint `nextAction` 继续；
- 已修改文件不被重做或覆盖；
- attempt/owner/接管理由可查询；
- 最终只产生一份有效业务结果。

### REC-003 实际完成但 Run 超时

步骤：

1. Run-A 完成全部 required step 和 effect。
2. 在 `AFTER_VERIFIED_BEFORE_TERMINAL` 丢弃终态。
3. 触发 timeout compensation。
4. 启动重复 Run-B。

期望：

- Run-A 可以显示 UNKNOWN/TIMEOUT；
- reconciler 根据 step、Git、测试、session 和 effect 证明完成；
- Execution 直接收口成功；
- Run-B 不启动模型、不重做 step/effect；
- observation 时间线同时保留超时判断和后续更正。

### REC-004 APPLIED 只补验证

步骤：

1. step 已 APPLIED，文件和 commit 存在，verify step 尚未运行。
2. 新 Run preflight。

期望：

- 新 Run 不重新修改文件；
- nextAction 指向验证；
- 验证通过后转 VERIFIED；
- changed file hash 保持不变。

### REC-005 晚到终态不能覆盖新 owner

步骤：

1. Run-A lease 失效，Run-B 接管并推进状态。
2. Run-A 晚到发送 SUCCEEDED/FAILED terminal callback。

期望：

- Run-A 终态只影响自身 Run/observation；
- 不覆盖 Run-B owner、step、Execution 或 Requirement 状态；
- 可看到 `CONTRADICTED/STALE_CALLBACK` 类记录。

### REC-006 compensation cycle 幂等

步骤：

1. 对同一批 timeout/UNKNOWN 数据连续运行 compensation 10 次。
2. 每次记录表行数、状态和事件/评论数。

期望：

- 第一次完成必要对账；
- 后九次不重复 comment、event、checkpoint、effect 或 MR；
- settlement key 稳定；
- 最终状态一致。

### EFF-001 相同 effect key/hash 重放

步骤：

1. 执行一次 effect 并确认。
2. 使用相同 effectKey/requestHash 重复 20 次。

期望：

- 外部系统实际执行一次；
- 所有调用返回首次 resultSnapshot；
- 数据库只有一个 effect 业务记录。

### EFF-002 相同 key、不同 hash

步骤：

1. 已存在 effectKey-A/hash-A。
2. 请求 effectKey-A/hash-B。

期望：

- 返回冲突；
- 不覆盖 request/result snapshot；
- 不调用外部系统；
- required step 不能因冲突 VERIFIED。

### EFF-003 可查询 UNKNOWN

故障点：`AFTER_PUSH_BEFORE_RESPONSE`。

步骤：

1. push 已成功，但客户端收到超时。
2. effect 标记 UNKNOWN。
3. 运行 reconciler。

期望：

- 先查询远端 branch SHA；
- SHA 匹配则 effect 转 CONFIRMED；
- 不重复 push；
- 查询证据写入 resultSnapshot/observation。

### EFF-004 不可查询但支持幂等键

步骤：

1. 模拟首次调用结果丢失。
2. 使用完全相同的 idempotency/effect key 重试。

期望：

- 外部替身返回首次结果；
- effect 最终 CONFIRMED；
- requestHash 不变。

### EFF-005 既不可查询也不幂等

步骤：

1. 模拟未知结果。
2. 运行自动补偿。

期望：

- 禁止自动重发；
- effect 保持 UNKNOWN；
- step BLOCKED/WAITING_HUMAN；
- reviewer 可追加 observation，但不能直接改成功；
- 只有受控人工 reconcile 能解除。

### DLV-001 重复 MR 创建请求

故障点：`AFTER_MR_CREATE_BEFORE_RESPONSE`。

步骤：

1. GitLab 实际创建 MR 后丢弃响应。
2. 再次调用 delivery。
3. 按 project + source branch 查询 MR。

期望：

- 只存在一个 MR；
- 重复调用复用/更新该 MR；
- effect 记录唯一 MR IID/URL。

### DLV-002 target branch 前进

步骤：

1. 在初次验证后推进 target branch。
2. 执行 delivery。

期望：

- feature branch 合入最新 target；
- 冲突进入 DELIVERY_BLOCKED/remediation，而不是强推；
- 候选新 HEAD 重新运行 required verification；
- 旧 HEAD 测试证据不被复用。

### DLV-003 MR SHA 一致性

步骤：

1. 完成交付。
2. 比较 workspace currentHead、最终验证证据 HEAD、远端 source branch SHA、MR source SHA。

期望：四者完全一致。

### DLV-004 delivery 重复收口

步骤：

1. 对已 CONFIRMED delivery 重复调用 20 次。
2. 查询 comment、event、memory job、Run 和 MR。

期望：

- 不重复创建 MR；
- 不重复产生语义相同评论、事件或 memory job；
- 可以有独立 Run attempt，但共享 delivery Execution/effect。

### DLV-005 不自动合并

步骤：

1. 执行完整 delivery。
2. 记录 GitLab API 请求。
3. 检查 Requirement 和 MR 状态。

期望：

- 没有 merge API 请求；
- Requirement 可以为 `COMPLETED`；
- MR 单独为 `OPEN/SUBMITTED`；
- UI 不显示“已合入”。

ACC-002 通过条件：

- REC-001～006、EFF-001～005、DLV-001～005 全部 PASS；
- 三类失败均有数据库、Git 和调用次数证据；
- 外部副作用没有无法解释的重复执行。

## 12. ACC-003：API、前端可观测、安全与性能验收

### API-001 overview 权威状态

准备：

- 制造 Run `SUCCEEDED`，但仍有 required step `PENDING`；
- 制造 Run `FAILED`，但所有 required step 已 VERIFIED。

步骤：分别调用：

```http
GET /executionProgress/findOverview?requirementId=<id>
```

期望：

- 第一种 Execution 仍 ACTIVE；
- 第二种 Execution 可以 SUCCEEDED；
- response 不从 Run summary/exitCode 推断；
- automationStatus 与 mrStatus 分开。

### API-002 step、checkpoint、effect 分页

步骤：

1. 插入 45 个 checkpoint、45 个 effect。
2. 分别请求 page 1、2、3，rows=20。
3. 请求非法 page/rows 和不存在 ID。

期望：

- 页间不重复、不遗漏，顺序稳定；
- 20/20/5；
- 参数有上限，非法请求返回业务错误；
- 不一次返回全部 payload。

### API-003 observation 追加和更正

步骤：

1. reviewer 追加 manual observation。
2. 再追加一条 supersedes 第一条的更正。
3. 尝试 update/delete 第一条。

期望：

- 两条历史均保留；
- supersedes 关系正确；
- API 不暴露 update/delete；
- observation 不直接修改 step/effect。

### SEC-001 Requirement 访问控制

分别使用 reviewer、project-user、outsider 调用 overview、详情和 appendManual。

期望：

- reviewer 按权限访问并追加；
- project-user 不能执行人工对账；
- outsider 返回 403/业务拒绝，不能通过猜 ID 读取；
- WebSocket 订阅使用相同 Requirement 授权。

### SEC-002 证据脱敏

在 evidence 中注入：

- 绝对 worktree 路径；
- GitLab token；
- Authorization header；
- prompt 中的测试 secret；
- 环境变量值；
- 外部回执中的敏感字段。

期望：

- API、WS、页面、日志和导出报告均不出现原值；
- 可展示 digest、相对路径或脱敏占位；
- 服务端在 DTO 映射前处理，不依赖前端隐藏。

### SEC-003 路径和命令安全

尝试：

- 非法 requirement/workspace key；
- `../` 路径；
- branch 名控制字符；
- evidence 中伪造文件路径；
- 人工 observation 注入 HTML/script。

期望：

- 路径不能逃出 workspace root；
- 不发生命令注入；
- 页面不执行脚本；
- 日志不输出敏感堆栈。

### UI-001 执行进度主视图

使用运行中的应用和隔离浏览器：

1. 打开 RequirementWorkspace。
2. 查看 AutomationStatusBar 和“执行进度”页签。
3. 展开固定阶段和动态步骤。

期望：

- 显示 current owner、lease/stale、step 统计、checkpoint 和 nextAction；
- `APPLIED/VERIFIED/UNKNOWN/BLOCKED` 不只依赖颜色区分；
- 已 VERIFIED 显示“后续 Run 将跳过”；
- Run 状态与任务状态分开。

证据：a11y snapshot、截图、overview network response。

### UI-002 Run 时间线和证据

步骤：

1. 打开 Run。
2. 切换执行记录、原始日志和证据。
3. 展开 superseded observation。
4. 翻页加载证据。

期望：

- observation 完整有序；
- 原始日志继续实时显示；
- 证据按需请求，不在首屏全量加载；
- 更正不隐藏历史；
- 无 JS console error。

### UI-003 WS 正常及时性

步骤：

1. 页面建立 `/ws/requirement/{id}/progress`。
2. 服务端提交 step 更新并记录 committedAt。
3. 浏览器记录新 snapshot 可见时间。
4. 重复 30 次。

期望：

- 每次页面最终版本 >= 事件版本；
- 端到端可见时间全部 <= 3 秒；
- WebSocket 消息不直接覆盖权威快照，而是触发 overview refetch。

### UI-004 WS 断线与恢复

步骤：

1. 活动态断开 WS，继续修改进度。
2. 观察 5 秒轮询。
3. 切换非活动状态，观察 30 秒轮询。
4. 恢复 WS。

期望：

- 页面显示连接/过期状态和最后更新时间；
- 保留最后成功快照，不清空数据；
- 活动态 5 秒内、非活动态 30 秒内收敛；
- 恢复连接后立即全量刷新。

### UI-005 重复和乱序事件

发送版本：

```text
10, 10, 12, 11, 13, 12
```

期望：

- 页面 snapshotVersion 单调到 13；
- 不回退到 11/12；
- 同一 observation 不重复；
- refetch 失败时显示过期而不是伪成功。

### UI-006 自动化完成与 MR 状态

覆盖组合：

| 自动化 | MR | 期望文案 |
|---|---|---|
| COMPLETED | OPEN | 自动化完成（MR 已提交）/ MR 待审核 |
| COMPLETED | MERGED | 自动化完成 / MR 已合入 |
| COMPLETED | CLOSED | 自动化完成 / MR 已关闭 |
| DELIVERING | NOT_SUBMITTED | 交付中 / MR 未提交 |

不得把 `COMPLETED + OPEN` 显示为“已合入”。

### UI-007 刷新和跨浏览器恢复

步骤：

1. 打开特定 Execution/Run 详情。
2. 刷新页面并清空非认证本地缓存。
3. 在第二浏览器登录同一身份。

期望：

- 两端从服务端恢复相同 step、checkpoint、observation 和 effect；
- 完成状态不依赖浏览器 localStorage；
- 时间显示一致且可追溯到服务端 ISO 时间。

### UI-008 未知枚举

让后端测试替身返回前端尚未认识的状态 `RECONCILING_V2`。

期望：

- 页面显示“未知状态（RECONCILING_V2）”；
- 不显示成功色/完成图标；
- 不崩溃。

### A11Y-001 键盘和语义

步骤：

1. 仅使用 Tab/Shift+Tab/Enter/Escape 操作进度页、Run drawer 和 evidence。
2. 检查焦点、ARIA 名称、表格/树语义和颜色对比。
3. 在 200% 缩放和 375×812 viewport 验证。

期望：

- 无键盘陷阱；
- drawer 关闭后焦点返回触发元素；
- 状态不只依赖颜色；
- 文本不被截断到不可读；
- 满足项目约定的 WCAG 2.2 AA 基线。

### PERF-001 overview 查询

数据量：20 executions、100 steps、1000 observations、1000 checkpoints、500 effects。

步骤：

1. 预热后执行 overview 500 次。
2. 并发度 20。
3. 记录 p50/p95/p99、数据库查询数和响应大小。

期望：

- p95 <= 500 ms；
- 查询数与 journal 总量无关，不出现逐 step N+1；
- overview 不携带完整 evidence/log；
- 错误率 0。

### PERF-002 前端长时间连接

步骤：

1. 页面保持 30 分钟。
2. 每秒发送一次进度事件，周期性断线重连。
3. 记录 WS 数量、定时器、内存和 overview 请求。

期望：

- 同一页面不累积重复 WS/轮询器；
- unmount 后连接和 timer 释放；
- observation 不重复增长；
- 无持续性明显内存增长。

ACC-003 通过条件：

- API-001～003、SEC-001～003、UI-001～008、A11Y-001、PERF-001～002 全部 PASS；
- 浏览器运行时无未解释 console error；
- 网络请求、页面状态与数据库权威状态一致。

## 13. 基础自动化命令

### 13.1 后端

从 `backend/` 执行：

```bash
./gradlew :sei-online-code-service:test --tests \
  "com.changhong.onlinecode.dao.ProgressLedgerDaoTest"

./gradlew :sei-online-code-service:test --tests \
  "com.changhong.onlinecode.service.progress.ProgressServiceTest"

./gradlew :sei-online-code-service:test --tests \
  "com.changhong.onlinecode.service.agent.CodingTaskProgressIntegratorTest"

./gradlew :sei-online-code-service:test
./gradlew build
```

验收时必须检查 Gradle 报告中的 executed/skipped 数，不能只看进程 exit code。

### 13.2 前端

从 `frontend/` 执行：

```bash
pnpm test:regression
pnpm lint
pnpm build
```

现有 `test:regression` 主要是源码契约断言，不能替代浏览器 E2E。ACC-003 必须实际启动前后端并操作
页面。

### 13.3 通用

```bash
git status --short
git diff --check
git rev-parse HEAD
```

依赖漏洞扫描：

```bash
cd frontend
pnpm audit
```

若私有依赖源、Docker 或测试 GitLab 不可用，记录为 `BLOCKED`，不能把静态替代结果判 PASS。

## 14. 缺陷严重度

### CRITICAL

- 数据丢失或覆盖用户 worktree；
- 旧 fencing token 能写入或推进 HEAD；
- 重复副作用造成不可逆外部影响；
- 未授权读取敏感 evidence/token；
- 自动调用 GitLab merge API。

### HIGH

- 三类失败任一会从头重复执行；
- 两个 Run 可以同时 claim 同一步骤；
- MR source SHA 与验证 HEAD 不一致；
- Requirement 产生多个 worktree/branch；
- 前端把未完成/未合并显示为完成/已合入；
- 核心 API 无权限校验。

### MEDIUM

- WS 断线不能在轮询窗口内收敛；
- observation/checkpoint 分页错误；
- unknown enum 导致页面崩溃；
- overview 超过性能阈值或存在 N+1；
- 键盘无法操作核心查看流程。

### LOW

- 不影响判断的文案、布局或非核心审计展示问题。

## 15. 总体验收判定

### PASS

必须同时满足：

- ACC-001、ACC-002、ACC-003 全部 PASS；
- 所有 mandatory case 实际运行，无 skip/disabled；
- 0 CRITICAL、0 HIGH、0 MEDIUM；
- 后端全量测试、前端 lint/build/regression 通过；
- candidate SHA 与报告、Git/MR 证据一致；
- 未调用 merge API；
- CODEOWNER 可以复核全部证据。

### WARNING

- 所有核心 case PASS；
- 0 CRITICAL、0 HIGH；
- 仅存在不影响发布判断的已接受 MEDIUM，且有 owner、期限和书面风险接受。

本项目默认发布门禁不接受 WARNING；只有用户/CODEOWNER 明确批准例外才可继续 MR 审核。

### FAIL

满足任一：

- 存在 CRITICAL/HIGH；
- 任一核心 case FAIL；
- 用 mock/static review 替代规定的运行时验收；
- 关键测试被 `@Disabled` 或跳过；
- 证据无法绑定候选 SHA；
- 出现无法解释的重复副作用。

### BLOCKED

- 环境、迁移、凭证、Docker、GitLab sandbox 或前置任务未完成，导致 case 无法运行。

BLOCKED 不等于 PASS，也不能通过延期测试解除发布门禁。

## 16. test-agent 执行顺序

1. 冻结 candidate SHA，创建报告目录和 manifest。
2. 检查入口条件；不满足则立即出具 BLOCKED 报告。
3. 执行 ACC-001：先证明持久化、CAS、租约和 worktree 安全。
4. ACC-001 通过后执行 ACC-002：故障、effect 和 MR。
5. ACC-001/002 通过后执行 ACC-003：API、UI、安全、性能。
6. 汇总缺陷；有修复 commit 时按影响分析重跑。
7. 全部通过后签署 PASS，并把报告路径和 candidate SHA 写回交接任务表。
8. DEL-001 创建或更新唯一 MR，不自动合并。

## 17. 验收报告模板

```markdown
# Run / Execution Reliability Acceptance Report

- Candidate SHA:
- Branch:
- Test agent:
- Started/Finished:
- Environment:
- Overall: PASS / WARNING / FAIL / BLOCKED

## Entry Gate

| Gate | Evidence | Result |
|---|---|---|

## Case Results

| Case | Expected | Actual | Evidence | Result |
|---|---|---|---|---|

## Defects

| ID | Severity | Reproduction | Evidence | Owner | Fix SHA | Retest |
|---|---|---|---|---|---|---|

## Metrics

- Claim concurrency:
- Overview p50/p95/p99:
- WS event-to-visible:
- Duplicate external effects:
- Executed/skipped tests:

## Residual Risks

## Sign-off

- test-agent:
- CODEOWNER:
- User/product owner:
```

## 18. QA 自检

test-agent 提交报告前确认：

- 每个 FAIL 都可复现，不报告猜测性缺陷；
- 每个 PASS 都有运行证据；
- 所有 case 绑定同一候选 SHA；
- 安全检查先于性能和 UI 美观；
- 实际检查了 skipped/disabled 测试；
- 没有把 Run 成功直接当成 Execution/Requirement 成功；
- 没有把 Requirement COMPLETED 当成 MR MERGED；
- 没有遗漏三类失败中的任何一种；
- 没有修改或删除现有用户工作；
- 最终结论与严重度规则一致。
