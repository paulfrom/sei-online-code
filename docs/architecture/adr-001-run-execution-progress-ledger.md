# ADR-001：重复 Run 下的执行进度账本与幂等续作

- 状态：Accepted
- 决策日期：2026-07-17
- 范围：Requirement 自动化、CodingTask 执行、Agent Run、MR 交付和需求工作台
- 详细评审记录：
  [`run-execution-reliability-design-draft.md`](./run-execution-reliability-design-draft.md)
- 数据模型：
  [`run-execution-progress-data-model.md`](./run-execution-progress-data-model.md)

## 背景

Agent 调用可能在三个位置失败：

1. 调用没有被执行；
2. 调用执行了一部分后失败；
3. 调用实际完成，但调用方等待超时或终态回调丢失。

网络、进程和调度器只能提供 at-least-once 调用。禁止调度器重复调用既不能覆盖第三种情况，也会降低
故障恢复能力。真正需要保证的是：重复 Run 能发现同一业务任务的可信处理进度，跳过已完成步骤，只
接管未完成步骤，并且不重复产生外部副作用。

现有实现已经具备 Requirement、ExecutionPlan、CodingTask、Run、需求级工作台、Run 日志 WebSocket
和 MR 交付流程，但 Run 的摘要、退出码和原始日志不足以作为任务完成的权威依据。

## 决策

### 1. 分离 Task、Execution 和 Run

- `CodingTask/Task` 是稳定业务任务；
- `Execution` 是某个任务在确定输入快照和计划版本下的一次逻辑处理；
- `Run` 是一次 Agent/CLI attempt；
- 重复 Run 可以存在，但通过稳定 `executionKey` 共享同一个 Execution 和进度账本。

Run 成功不直接完成 Execution；Run 失败或超时也不抹除已经验证的步骤。

### 2. 使用平台阶段与动态步骤账本

平台固定：

```text
DISCOVER → PLAN → IMPLEMENT → VERIFY → DELIVER
```

Agent 在 PLAN 阶段声明具有稳定 `stepKey` 的动态步骤。步骤状态区分：

```text
PENDING / IN_PROGRESS / APPLIED / VERIFIED /
UNKNOWN / BLOCKED / FAILED / SUPERSEDED
```

- `APPLIED` 表示动作已经产生效果，但尚未完成验证；
- `VERIFIED` 表示证据满足完成条件，后续 Run 永久跳过；
- `UNKNOWN` 表示结果不确定，必须先对账，不得直接重做。

### 3. Requirement 级单写和唯一 worktree

- 每个 Requirement 只有一个持久化 workspace 和一个 feature branch；
- 需求下所有 Execution、步骤和重复 Run 共享该 worktree；
- 同一 Requirement 同时只允许一个写 owner，不同 Requirement 可以并行；
- owner 通过租约、文件锁和单调递增的 fencing token 获得写权限；
- active loop 切换或接管时递增 fencing token，旧 Run 的 checkpoint 和副作用提交被拒绝。

每个 `APPLIED` 步骤形成 checkpoint commit，并以 CAS 推进 workspace `currentHead`。接管者不得清空
未知的未提交变更，必须先使用 journal、Git diff 和持久化 patch 进行对账。

### 4. Checkpoint 和证据采用追加式记录

平台自动采集 claim、Git HEAD、文件变化、工具结果、测试报告和终态事件；Agent 主动提交结论、
剩余事项和 `nextAction`。

关键 checkpoint 写入失败时 fail-closed，不能继续产生新的平台副作用。自然语言摘要只用于展示；
只有类型化证据可以把步骤推进为 `APPLIED/VERIFIED`。

每个 Run 使用追加式 observation 记录启动、进展、checkpoint、终态、对账和人工审核。更正通过
`supersedesObservationId` 追加，不能覆盖历史。

### 5. 平台副作用使用 effect ledger

评论、事件、memory job、push、MR 和外部调用使用稳定 `effectKey + requestHash`：

- key 和 hash 相同：返回第一次结果；
- key 相同但 hash 不同：拒绝并进入冲突处理；
- 结果 `UNKNOWN`：可查询则先查询；不可查询但支持幂等键则使用原键重试；两者都不支持则等待人工。

未解决的 required `UNKNOWN` effect 阻止步骤进入 `VERIFIED`。

### 6. 完成状态分层判定

- Run：只表示一次调用的生命周期；
- Execution：当前计划版本的 required 步骤全部 `VERIFIED`，必要 effect 全部 `CONFIRMED`；
- Requirement 自动化：active loop 的 required Execution、最终验证和 MR delivery 全部完成。

Requirement 的 `COMPLETED` 固定表示“MR 已提交”，不表示已经合入主分支。

### 7. 交付只创建或更新 MR

平台在最新目标分支基础上整合并重新验证 requirement feature branch，然后 commit、push 并创建或
更新唯一 MR。平台不调用 GitLab merge API。CI、CODEOWNERS 审查和最终合并沿用现有人工流程。

### 8. 前端以聚合快照为权威

现有 RequirementWorkspace 增加“执行进度”视图，分开展示自动化、Execution、步骤、Run 和 MR
状态。Run 详情展示 observation 时间线、原始日志和结构化证据。

需求级 WebSocket 只通知 `snapshotVersion` 变化，前端收到后重新查询权威聚合快照；事件丢失或断线
时使用现有活动态 5 秒、非活动态 30 秒轮询收敛。前端不得从日志、摘要、退出码或 MR 评论推断完成
状态。

## 备选方案

### 只恢复 session/worktree

改造成本最低，但进度依赖 Agent 推断，无法阻止两个 Run 同时处理同一步，也不能可靠去重外部
副作用，因此拒绝。

### 只使用固定阶段状态机

平台控制简单，但 IMPLEMENT 阶段过粗，部分文件完成后仍可能整体重做，因此拒绝。

### 阻止所有重复调用

无法可靠判断超时调用是否实际完成，也无法覆盖进程重启和消息重复投递，因此拒绝。

### 每个 Run 或 Execution 使用独立 worktree

并行度更高，但同一需求的部分成果需要频繁合并，冲突、基线漂移和交付一致性成本更高。当前采用
Requirement 级单写，待有真实并行吞吐证据后再评估。

### 自动合并 MR

可以缩短交付路径，但越过现有 CI、CODEOWNERS 和人工授权边界，因此拒绝。

### 前端只轮询或复用日志 WebSocket

只轮询的短时可观测性不足；把业务状态混入日志流会耦合两种生命周期。采用独立的需求级变更通知，
同时保留轮询降级。

## 结果与权衡

正向结果：

- 重复 Run 可以安全跳过已验证工作；
- 三类调用失败均可以从统一账本恢复；
- worktree、Git、外部副作用和 MR 具有一致的审计链；
- 前端可以解释“谁在处理、处理到哪里、为什么跳过或接管”；
- 交付权限边界保持不变。

成本与限制：

- 新增 Execution、workspace、step、checkpoint、effect 和 observation 数据；
- runner、补偿器和所有受控副作用必须接入 claim/fencing/effect 协议；
- Requirement 级单写限制同一需求内部的并行吞吐；
- checkpoint 和证据增加存储、脱敏和保留期治理成本；
- WebSocket 只改善及时性，正确性仍依赖数据库快照和轮询。

## 架构不变量

1. 同一 `(projectId, requirementId)` 只能有一个 requirement workspace。
2. 同一 `executionKey` 只能有一个 Execution。
3. 同一 Execution 计划版本内的 `stepKey` 唯一。
4. 没有有效 claim token 和 fencing token 的 Run 不能写步骤、worktree 或 effect。
5. `VERIFIED` 步骤不能因后续 Run 失败而回退。
6. `UNKNOWN` effect 未对账前不能自动重做，也不能完成 required step。
7. observation 和 checkpoint 只能追加，不能删除或覆盖历史判断。
8. MR source SHA 必须等于最终验证的 requirement workspace HEAD。
9. 前端展示的聚合完成状态必须来自后端账本。
10. 自动化 `COMPLETED` 不等于 MR `MERGED`。

## 实施顺序

1. 建立数据表、Run 关联、聚合只读查询和前端只读观测。
2. runner 强制 preflight、固定阶段 claim 和关键 checkpoint。
3. 引入动态步骤、plan version、证据验证和 supersede。
4. 接入 effect ledger、ProgressReconciler 和需求级进度事件。
5. 完成 session/patch 跨进程恢复、历史兼容与旧补偿逻辑清理。

每一阶段必须保持旧 Run 可读，并提供回滚开关。编码任务与验收任务分开规划，验收任务由
`test-agent` 独立执行。

## 后续验证

- 两个 Run 并发 claim 同一步骤时只有一个成功；
- Run 调用前失败、过程失败和完成后超时均从正确 checkpoint 续作；
- 旧 fencing token 的 checkpoint、commit 和 effect 被拒绝；
- `UNKNOWN` effect 不产生未经对账的重复调用；
- 最终 MR source SHA 与验证证据绑定的 HEAD 一致；
- WebSocket 重复、乱序、漏失和断线时前端最终显示同一权威快照；
- 页面明确区分 Run 失败、Execution 状态、自动化完成和 MR 已合并。
