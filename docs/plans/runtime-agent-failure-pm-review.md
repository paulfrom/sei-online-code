# 运行期 Agent 失败审阅与调度修正方案

## 1. 目标

修正计划任务运行期的失败处理，使系统满足以下行为：

1. coding agent 或 test-agent 完成后，先形成结构化交付结果。
2. 失败结果必须由 pm-agent 分析，pm-agent 决定重试原任务、调整计划、等待人工或终止当前计划。
3. pm-agent 作出决定前，不启动同一需求的后续计划任务。
4. `VALIDATION_FAILED`、业务验收失败和交付不完整不得被补偿器自动重试。
5. 工作区繁忙属于调度延迟，不得记为任务失败、不得消耗重试次数。
6. 长时间运行的 agent 不得占用数据库事务；任务列表必须及时显示真实运行状态。
7. PM 调整计划后，修复任务完成，再由计划中独立的 test-agent 任务重新验收。

本方案只定义代码与测试修改，不处理当前数据库中的 Run、CodingTask 或 ExecutionPlan。

> 数据迁移不在本方案范围内（不回填历史数据）。新表 `oc_task_delivery_review` 的建表语句
> 已提供于 `backend/sei-online-code-service/src/main/resources/db/migration/V9__create_task_delivery_review.sql`，
> 由 DBA / 外部 schema 流程执行。本仓库 `ddl-auto: validate`，未引入 Flyway 运行时；
> 代码层只保证 `@Entity` 注解与该 DDL 列定义一致。测试层使用 Mockito 纯单元测试，不依赖真实数据库。

## 2. 非目标

- 不修复当前正在运行或已经失败的任务数据。
- 不通过 SQL 回填历史 review 状态。
- 不改变前后端功能需求或业务验收标准。
- 不允许 pm-agent 为了让计划通过而降低原验收标准。
- 不把每个 coding task 的自测替代为 test-agent 验收。
- 不引入 Flyway/Liquibase 等迁移工具，不改 `ddl-auto: validate` 行为。
- 不引入分布式租约（phase 1 单实例内存租约即可，见 §6.2）。

## 3. 当前问题

### 3.1 PM 介入时点过晚

`RequirementAutomationService.onPlanTasksSettled` 只在当前计划所有任务进入终态后调用 pm-agent。单个任务失败时，如果仍有 `PENDING`、`RUNNING` 或 `VALIDATING` 任务，PM 不会分析失败。

### 3.2 补偿器越权决定重试

`CompensationService.recoverDevelopment` 会将 `FAILED` 和 `VALIDATION_FAILED` 自动恢复为 `PENDING`。这绕过了“PM 先分析失败”的运行规则，并会对确定性测试失败反复执行相同命令。

### 3.3 调度并发模型与执行层互斥模型不一致

`CodingTaskScheduler` 按 area 和 fileScope 判断可并行，`AgentExecutionService` 却按 requirement workspace 全局互斥。调度器启动的任务可能在执行层因工作区繁忙立即失败。

### 3.4 验证任务持有长事务

`executeValidationTask` 在调度事务内同步等待 test-agent，最长可等待 1800 秒。Run 已经运行时，CodingTask 的 `VALIDATING` 变更可能仍未提交，造成任务列表与运行列表不一致。

## 4. 目标状态模型

执行状态与 PM 审阅状态分离，避免继续扩张 `CodingTaskStatus` 的含义。

### 4.1 CodingTask 执行状态

沿用现有执行状态：

- `PENDING`
- `RUNNING`
- `VALIDATING`
- `SUCCEEDED`
- `FAILED`
- `VALIDATION_FAILED`
- `BLOCKED`
- `CANCELLED`
- `STALE`
- `SUPERSEDED`

### 4.2 TaskDeliveryReview 审阅状态

新增任务交付审阅记录，推荐使用独立实体和表 `oc_task_delivery_review`：

| 字段 | 含义 |
|---|---|
| `id` | 审阅记录 ID |
| `requirement_id` | 需求 ID |
| `execution_plan_id` | 执行计划 ID |
| `coding_task_id` | CodingTask ID |
| `delivery_run_id` | 被审阅的最新交付 Run ID |
| `review_run_id` | pm-agent 审阅 Run ID |
| `status` | `PENDING/REVIEWING/DECIDED/WAITING_HUMAN` |
| `decision` | `APPROVE/RETRY/REPLAN/WAIT_HUMAN` |
| `summary` | PM 结论 |
| `decision_json` | 完整结构化决定 |
| `created_date` | 创建时间 |
| `reviewed_date` | 决策时间 |

对 `(coding_task_id, delivery_run_id)` 建唯一约束，保证重复完成事件不会创建两次 PM 审阅。

### 4.3 PM 决策契约

pm-agent 必须只返回合法 JSON：

```json
{
  "decision": "APPROVE | RETRY | REPLAN | WAIT_HUMAN",
  "summary": "审阅结论",
  "failureCategory": "NONE | TRANSIENT_INFRA | DELIVERY_INCOMPLETE | VALIDATION_FAILED | UPSTREAM_INCOMPLETE | PLAN_DEFECT",
  "findings": ["事实与证据"],
  "retryReason": "仅 RETRY 时必填",
  "remediationTasks": []
}
```

决策约束：

- `APPROVE`：只允许执行成功且交付证据完整的任务。
- `RETRY`：只允许瞬态基础设施问题或可明确纠正的 agent 执行偏差；必须记录原因。
- `REPLAN`：用于代码缺陷、测试失败、上游交付不完整、任务契约错误或需要新增修复任务的情况。
- `WAIT_HUMAN`：PM 输出无效、无法安全判断、达到补救上限或需要需求方决策。
- 对 `FAILED` 和 `VALIDATION_FAILED` 返回 `APPROVE` 应被服务端拒绝并转为 `WAIT_HUMAN`。

## 5. 目标运行流程

### 5.1 Coding agent 完成

```text
agent 完成
  -> 短事务结算 Run 和 CodingTask（现有 CodingTaskExecutionService 已在提交后异步回调 finishRun）
  -> 写入 DEV_RESULT 与结构化交付摘要
  -> 创建 TaskDeliveryReview(PENDING)
  -> 事务提交后发布 TaskDeliveryReviewRequested（异步事件）
  -> 暂停同一需求的新任务调度（见 §6.1 门禁）
  -> pm-agent 审阅（异步，@TransactionalEventListener AFTER_COMMIT，事务外阻塞调用 agent）
  -> 应用 APPROVE / RETRY / REPLAN / WAIT_HUMAN（新短事务）
  -> 事务提交后按决定重新调度
```

**异步边界硬约束**：PM 审阅与 test-agent 执行一样，必须在任何数据库事务之外运行。
监听 `TaskDeliveryReviewRequested` 使用 `@Async` + `AFTER_COMMIT` 语义，调用 `PmAgentClient`
（其内部仍为同步阻塞，但不持有事务）。决策结果在新的 `@Transactional(REQUIRES_NEW)` 中落库。

### 5.2 Test-agent 完成

```text
test-agent 完成
  -> 解析 JSON passed
  -> 短事务结算 Run 和 CodingTask（claim/execute/finish 三阶段，见 §7）
  -> 写入 VALIDATION_RESULT
  -> 创建 TaskDeliveryReview(PENDING)
  -> pm-agent 审阅（异步事件）
  -> passed=false 时只能 RETRY / REPLAN / WAIT_HUMAN
```

### 5.3 PM 决策应用

#### APPROVE

- 审阅记录更新为 `DECIDED/APPROVE`。
- 成功任务才可成为依赖满足条件。
- 调度器继续启动后续任务。

#### RETRY

- 原失败事实和旧 Run 保留。
- CodingTask 回到 `PENDING`。
- retryCount 只在 PM 决定后增加一次。
- 新 Run 通过 parent/compensates 关系关联旧 Run。
- 事务提交后重新调度。

#### REPLAN

- 原任务和原 Run 保留失败证据。
- 当前计划进入 `NEEDS_REMEDIATION`。
- 根据 `remediationTasks` 创建新版本补救计划。
- **任务级 REPLAN 与计划级验收 rejected 复用同一补救路径**：现有 `RequirementAutomationService.startRemediationLoop`
  作为唯一补救计划生成入口。任务级 REPLAN 触发时直接调用该方法，传入任务级 review 的 `remediationTasks`，
  并复用其 `MAX_REMEDIATION_ROUNDS = 3` 上限。为避免任务级与计划级重复计数，补救轮数统一以
  `(requirementId, loopId)` 的历史 `REMEDIATION` 计划版本数为准（与现有 `onPlanTasksSettled` 计数口径一致）。
- 上游交付不完整时，修复任务必须先于原失败任务的替代任务。
- 补救计划必须包含独立 test-agent 验收任务。
- 新计划创建完成后再恢复调度。
- **remediationTasks 契约统一**：任务级 review 与计划级 acceptance 使用同一 `PlanTask` 结构（taskKey/title/description/agent/area/dependsOn/fileScope/acceptanceCriteria），由 `PmAgentClient` 统一解析与 DAG 校验。

#### WAIT_HUMAN

- 需求进入 `WAITING_HUMAN`。
- 不再自动调度或补偿该需求的计划任务。

## 6. 调度与并发契约

### 6.1 PM 审阅门禁

`CodingTaskScheduler.doSchedule` 在选择候选任务前检查：

```text
当前 loop 是否存在 PENDING 或 REVIEWING 的 TaskDeliveryReview？
```

存在时立即结束本轮调度。最终计划验收不得替代任务级交付审阅；只有所有任务交付审阅完成后，才允许进入 `onPlanTasksSettled`。

### 6.2 工作区租约（复用现有 mutex，不新建）

代码现状：`AgentExecutionService.activeAgentRuns`（`ConcurrentMap<String,String>`）已是一个
按 `projectId::requirement-{id}` 的单实例内存互斥锁；`WorkspaceLeaseService` 另有一套面向
进度账本/Git commit 的 DB lease（含 fencing token，语义不同）。

本方案**重构并复用 `AgentExecutionService` 的内存 mutex**，不引入第二把锁，避免双锁死锁：

- 将 `activeAgentRuns` 重构为具名组件 `AgentWorkspaceLease`（仍是单实例内存 `ConcurrentMap`），
  暴露 `tryAcquire(slotKey, runId) -> AcquireResult` 与 `release(slotKey, runId)`。
- `AcquireResult` 区分 `ACQUIRED` / `BUSY`（BUSY 携带当前持有 runId）。
- 同一 `(projectId, workspaceKey)` 同时最多一个活动 agent。
- 租约繁忙时任务保持 `PENDING`，不创建失败 Run，不写失败摘要，不增加 retryCount。
- `AgentExecutionResult` 增加类型化 `DEFERRED` 语义：保持 record 不变，新增
  `boolean deferred()` 访问器（默认 false）；新增静态工厂 `deferred(runId, reason)`，
  把"workspace busy"改动收敛到执行入口一处，**不**改造现有 sealed 层级（当前是扁平 record）。
- 调度器收到 `DEFERRED` 时跳过该任务、不修改任务状态、本轮不发布 `SchedulingPassCompleted` 触发的下游启动。
- 租约在成功、失败、取消和异常路径都必须释放（现有 `finally` 块已覆盖，保持）。
- 多实例部署前替换为带过期时间/心跳的持久化或分布式租约（phase 1 不做，列为风险）。

**与 area/fileScope 并行的关系澄清**：phase 1 保持现有"同一 requirement 全局串行"语义
（requirement 工作区互斥），`doSchedule` 的 area lane / fileScope 检查仍保留——它们在
多 requirement 共享调度器实例、或未来放开 requirement 内并行时仍有效，不与此方案冲突。

### 6.3 调度依赖条件

任务依赖满足应同时要求：

```text
依赖任务 status == SUCCEEDED
且依赖任务最新 delivery review == APPROVE
```

实现要点：`doSchedule` 的依赖检查由"`status==SUCCEEDED`"扩展为"`status==SUCCEEDED` 且
`reviewService.latestReviewFor(dep).decision==APPROVE`"。`onDevelopmentRunFinished` 现有逻辑
（把成功任务直接置 `SUCCEEDED` 并 `schedule()`）改为：成功时置 `SUCCEEDED` 但**不**立即把下游
视为可启动——下游启动由 review APPROVE 后的恢复调度驱动。

失败任务等待 PM 决策期间，不把下游永久改为 `BLOCKED`；可保持 `PENDING`。只有 PM 决定 `REPLAN` 或 `WAIT_HUMAN` 后，才按计划变更结果处理下游任务。

## 7. 事务与异步边界

新增 `ValidationTaskExecutionService`，将验证执行拆成三段：

1. `claim(taskId)`：短事务执行 `PENDING -> VALIDATING`，保存并提交。
2. `execute(taskId)`：事务外通过专用线程池运行 test-agent。
3. `finish(taskId, result)`：短事务保存 Run、评论、任务状态和 review 记录，提交后发布审阅事件。

禁止在 `CodingTaskScheduler.schedule()` 的数据库事务内等待 agent Future。coding agent 也应遵守相同原则：状态抢占提交后才启动外部 CLI，完成回调再用新事务结算。

专用线程池必须配置：

- 有界队列；
- 明确线程数；
- 拒绝策略；
- 超时与取消传播；
- traceId、requirementId、codingTaskId 和 runId 日志上下文。

新增 `OcAgentExecutorConfig` `@Configuration` 类，定义名为 `validationAgentExecutor` 的
`ThreadPoolTaskExecutor` `@Bean`（有界队列 + AbortPolicy + 明确 core/max 池大小，参数走 `OcConfig`）。
这是本代码库首个显式 `TaskExecutor` bean（现状 `@EnableAsync` 走默认 `SimpleAsyncTaskExecutor`）。
PM 审阅复用同一有界池（或单独 `pmReviewExecutor`），避免无界 common pool。

## 8. 补偿策略

`CompensationService.recoverDevelopment` 不再直接恢复所有 `FAILED/VALIDATION_FAILED`。

允许自动补偿的范围：

- 已取得 PM `RETRY` 决策但调度事件丢失的任务；
- 服务重启导致的孤儿 Run；
- 已明确标记 `TRANSIENT_INFRA` 且策略允许自动恢复的基础设施故障。

禁止自动补偿：

- `VALIDATION_FAILED`；
- `DELIVERY_INCOMPLETE`；
- `UPSTREAM_INCOMPLETE`；
- `PLAN_DEFECT`；
- 尚未完成 PM 审阅的失败任务；
- `WAITING_HUMAN` 需求。

补偿器只恢复既有决策，不产生新的业务决策。

**retryCount 单调约束**：`failureInfoSupport.markRetrying` 自增 retryCount。为避免 PM 决策路径与
补偿恢复路径对同一任务重复自增：PM `RETRY` 决策应用时调用一次 `markRetrying`；补偿器在
"已有 RETRY 决策但调度事件丢失"路径只重发 `ScheduleRequested` 事件，**不**再调用 `markRetrying`。
验收用例需断言：RETRY 决策 + 后续补偿恢复同一任务，`retryCount` 仅 +1。

## 9. 实施任务

### BE-RUN-001：交付审阅领域契约与持久化

- Agent：backend-dev-agent
- 优先级：P0
- 依赖：无
- 范围：API enum/DTO、service entity/DAO、数据库建表机制
- 交付：`TaskDeliveryReview`、审阅状态与决策枚举、唯一约束、PM JSON DTO
- 验收条件：
  - `(codingTaskId, deliveryRunId)` 幂等创建；
  - 非法决策组合被服务端拒绝；
  - 不引入 tenant 字段或租户过滤；
  - 单元测试覆盖状态转换和并发重复创建。

### BE-RUN-002：任务级 PM 审阅服务

- Agent：backend-dev-agent
- 优先级：P0
- 依赖：BE-RUN-001
- 范围：`PmAgentClient`、新增 `TaskDeliveryReviewService`、事件与监听器
- 交付：审阅提示词、结果解析、幂等事件处理、四类决策应用
- 验收条件：
  - 成功和失败任务均生成结构化审阅输入；
  - `VALIDATION_FAILED + APPROVE` 被拒绝并进入 `WAITING_HUMAN`；
  - PM 调用或 JSON 解析失败进入 `WAITING_HUMAN`；
  - `RETRY` 只产生一次重试；
  - `REPLAN` 复用补救计划能力并保留旧失败证据。

### BE-RUN-003：验证任务异步化与事务拆分

- Agent：backend-dev-agent
- 优先级：P0
- 依赖：BE-RUN-001
- 范围：`CodingTaskScheduler` 验证入口、`ValidationLoopService`、新增执行服务与线程池配置
- 交付：claim/execute/finish 三阶段验证执行
- 验收条件：
  - test-agent 运行期间其他事务可查询到 `VALIDATING`；
  - scheduler 事务不等待 agent Future；
  - 完成、失败、超时、取消都能结算 Run 和任务；
  - 完成后创建唯一 delivery review；
  - 不使用无界 common pool。

### BE-RUN-004：统一工作区租约和 DEFERRED 语义

- Agent：backend-dev-agent
- 优先级：P0
- 依赖：BE-RUN-001
- 范围：`AgentExecutionService`、`AgentExecutionResult`、`CodingTaskScheduler`、租约组件
- 交付：统一 workspace lease、类型化 `DEFERRED` 结果
- 验收条件：
  - 同一 workspace 并发启动只允许一个成功取得租约；
  - 未取得租约的任务保持 `PENDING`；
  - workspace busy 不产生失败 Run、不增加 retryCount；
  - 所有终止路径释放租约；
  - 不同 workspace 仍可并行。

### BE-RUN-005：补偿策略收敛

- Agent：backend-dev-agent
- 优先级：P0
- 依赖：BE-RUN-001、BE-RUN-002
- 范围：`CompensationService`、`FailureInfoSupport`、补偿日志
- 交付：基于失败分类和 PM 决策的补偿规则
- 验收条件：
  - `VALIDATION_FAILED` 不会被自动恢复；
  - review 未完成时不重试；
  - `WAITING_HUMAN` 不被补偿；
  - 已决定 `RETRY` 但调度事件丢失时可以恢复；
  - 补偿日志包含决策来源和 deliveryRunId。

### BE-RUN-006：调度门禁与计划结算集成

- Agent：backend-dev-agent
- 优先级：P0
- 依赖：BE-RUN-002、BE-RUN-003、BE-RUN-004、BE-RUN-005
- 范围：`CodingTaskScheduler`、`RequirementAutomationService`、有效任务图解析
- 交付：review gate、依赖审阅条件、PM 决策后的恢复调度
- 验收条件：
  - 任一 delivery review 为 `PENDING/REVIEWING` 时不启动后续任务；
  - 依赖任务未 `APPROVE` 时下游不启动；
  - 任务级审阅全部完成后才允许最终计划验收；
  - `REPLAN` 创建的补救计划包含明确修复任务和独立验收任务；
  - 重复调度事件不重复启动任务或 PM 审阅。

### TEST-RUN-001：失败审阅与补偿策略后端验收

- Agent：test-agent
- 类型：独立验收任务
- 优先级：P1
- 依赖：BE-RUN-006
- 范围：后端单元测试与集成测试
- 验收条件：
  - agent 失败后首先出现 PM review，PM 决策前零次自动重试；
  - PM `RETRY/REPLAN/WAIT_HUMAN` 三条路径全部通过；
  - validation failure 不被 CompensationScheduler 自动重试；
  - 重复事件和并发事件保持幂等；
  - 报告实际命令、退出码、测试数量、失败和跳过数量；
  - 全部测试通过，无跳过或未解释失败。

### TEST-RUN-002：调度、租约与事务可见性验收

- Agent：test-agent
- 类型：独立验收任务
- 优先级：P1
- 依赖：BE-RUN-006
- 范围：并发集成测试、事务集成测试
- 验收条件：
  - 同一 workspace 同时最多一个活动 agent；
  - busy 任务保持 `PENDING` 且 retryCount 不变；
  - 不同 workspace 可以并行；
  - test-agent 运行期间任务状态对其他事务显示为 `VALIDATING`；
  - agent 执行期间不存在持续占用的 scheduler 数据库事务；
  - 全部测试通过，无跳过或未解释失败。

## 10. 任务依赖

```text
BE-RUN-001
  ├─ BE-RUN-002 ── BE-RUN-005 ─┐
  ├─ BE-RUN-003 ────────────────┤
  └─ BE-RUN-004 ────────────────┤
                                 ▼
                            BE-RUN-006
                              ├─ TEST-RUN-001
                              └─ TEST-RUN-002
```

编码任务完成后只进入后续编码任务调度。只有 `TEST-RUN-001` 和 `TEST-RUN-002` 执行 test-agent 验收。任一验收失败时，根据失败报告新增或重开修复任务，修复后重新执行对应验收任务，不降低验收标准。

## 11. 风险与控制

| 风险 | 控制措施 |
|---|---|
| PM review 事件重复 | `(codingTaskId, deliveryRunId)` 唯一约束和条件更新 |
| PM 自身调用失败导致死锁 | 明确转 `WAITING_HUMAN`，不无限重试 |
| 状态提交成功但事件丢失 | 补偿器只补发 `PENDING` review 事件 |
| PM review 增加运行成本 | 使用轻量模型/提示词，只传交付摘要、验收证据和必要上下文 |
| 全需求暂停降低并行度 | 第一阶段优先保证正确性；后续可演进为依赖子图级暂停 |
| 单实例内存租约不支持扩容 | 多实例部署前替换为持久化或分布式租约 |
| 新旧计划任务混淆 | 所有 review 和决定携带 loopId、planId、codingTaskId、deliveryRunId |

## 12. 完成定义

满足以下条件才视为方案实施完成：

1. 失败 agent 不会在 PM 决策前被自动重试。
2. 任一任务交付结束后，PM 审阅完成前不会启动后续任务。
3. 工作区繁忙不会产生任务失败或消耗重试次数。
4. test-agent 运行期间任务状态已提交且可查询。
5. PM 可以明确选择重试、补救计划或人工处理。
6. 补救计划包含修复任务以及独立 test-agent 验收任务。
7. 两个计划内验收任务全部通过，无未解释失败或跳过。

