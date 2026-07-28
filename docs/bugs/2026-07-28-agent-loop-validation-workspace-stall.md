# Agent Loop 验证与工作区卡死修复

日期：2026-07-28

## 现象

1. 开发任务完成后，test-agent 验收失败只显示
   `test-agent validation did not pass`。当工作区或执行线程池繁忙时，
   调度延迟也会被当成验证失败，随后 PM 审阅可能无法生成有效修复计划，
   任务停在失败/审阅状态。
2. memory-review-agent 与需求开发、验证 agent 使用同一个 requirement workspace。
   memory-review 在物化 AgentBrief 和 skills 时也需要写目录，因此会触发
   “同一工作区已有运行中任务”。
3. PM 审阅进程在领取 `PENDING -> REVIEWING` 后若中断，没有对应的超时恢复，
   补偿任务只会重发 `PENDING`，导致 `REVIEWING` 永久悬挂。
4. WebSocket 日志仅适合在线查看。运行结束后 test-agent 的最终 JSON 输出没有
   统一落入 Run，排障时看不到实际命令、退出码和 findings。

## 根因

- `AgentExecutionResult` 原来用 `succeeded/deferred` 两个 boolean 表示三种状态，
  允许产生互相矛盾的组合，且 deferred 没有贯穿 validation 和 PM review 状态机，
  被压缩成普通失败或空决策。
- 物理工作区的默认 key 优先取 requirementId，未区分 writer 与 review/read lane。
- PM REPLAN 的提示词和服务端约束不一致：服务端要求独立 test-agent，
  但模型未被明确要求；同时也没有拒绝“只重新测试、不修代码”的计划。
- `TaskDeliveryReview` 缺少 `REVIEWING -> PENDING` 的 CAS 恢复路径。
- Agent CLI 最终输出没有作为审计证据持久化。

## 修复

### 调度延迟不再计为业务失败

- 工作区繁忙或 executor 拒绝时，对应 Run 收敛为 `CANCELLED`，摘要注明
  “因调度资源繁忙而推迟”，不增加业务 retryCount。
- validation 收到 deferred 后不写失败评论、不提交验证失败，而是调用已有
  defer settlement，把验证任务恢复为 `PENDING` 并等待重新调度。
- PM delivery review 收到 deferred 后通过 CAS 把 `REVIEWING` 恢复为 `PENDING`；
  补偿任务负责后续重发，避免立即热循环。

### PM 修复闭环

- 验证失败原因从 test-agent JSON 中提取 summary、非零 exitCode 和首条 finding。
- PM REPLAN 必须包含至少一个 frontend/backend 修复任务。
- 最终 test-agent 必须依赖所有修复任务；缺失或顺序不完整时，由服务端追加
  确定性的最终验证任务。
- 只包含 test-agent、没有代码修复任务的 REPLAN 会被拒绝，避免“失败后只重跑
  同一个测试”的死循环。

### 审阅恢复

- 新增 `REVIEWING -> PENDING` 的原子状态更新。
- 定时补偿会检测超时的 REVIEWING 审阅，原子恢复后重发 PM 审阅事件。

### 日志与工作区

- CLI 最终输出、failureReason、threadId 和 turnId 持久化到 Run；
  现有 Run 详情页可以在运行结束后显示最终结构化输出。
- requirement 开发/验证继续使用单 writer workspace。
- `AgentExecutionRequest` 只保留 `WRITER` 和 `SNAPSHOT_READER` 两种 workspace mode，
  不再允许业务调用方覆盖任意物理 workspace key。
- memory-review 使用 `SNAPSHOT_READER`，每个 Run 获得独立快照工作区，避免其 AgentBrief/skills
  写入与 writer 冲突。它是“逻辑只读、物理隔离副本”，不是让多个进程共享写
  同一个目录。

### 旧实现清理

- 删除 `AgentExecutionResult` 四参数兼容构造器；执行结果只能通过
  `SUCCEEDED / FAILED / DEFERRED` 枚举状态和命名工厂创建。
- 删除 ValidationOutcome 和 CodingTask CompletionDecision 的双 boolean 状态，
  改为互斥枚举。
- 删除 Plan、Spec、Requirement、PM 和 FeatureDesignBuild 中各自维护的 Run
  终态写入代码，统一由 `AgentExecutionService.settleRun` 处理取消优先级、
  terminal reason、finishedDate 和 failureReason。
- 删除 PM 客户端内部的 deferred 异常类型，改为 agent 包级统一异常。

## 并发模型

推荐模型不是在同一个物理目录上直接实现普通读写锁，而是：

- 一个 requirement writer lane：开发、集成及验证串行取得写租约；
- 多个 reader/reviewer lane：基于确定快照创建隔离工作区，可以并行；
- reader 产物只作为报告或决策返回，不直接合并代码；
- writer 通过 fencing token/CAS 防止过期执行提交结果。

这种方式语义上是“多读单写”，同时避免 agent 启动时生成临时文件造成真正的
共享目录写冲突。

## 与 multica loop 的取舍

multica 的 loop 更偏运行时调度底座：队列、lease、heartbeat、requeue 和
可观测性边界更直接，因此在“进程会失败、资源会繁忙”的现实环境下更容易恢复。

本项目的 loop 更偏业务状态机：Requirement、CodingTask、Validation、
PM Review 和 Acceptance 的门禁更完整，适合做可审计的交付闭环，但此前把
调度状态和业务失败混在一起，导致恢复性较弱。

更合适的方向是混合两者：

- 保留本项目的业务状态、DAG、PM 决策和独立验收；
- 采用 multica 风格的 lease/heartbeat/deferred/requeue 运行语义；
- 所有领取动作使用 CAS，所有运行都持久化终态原因与可回放证据。

## 验证

- 针对 AgentExecution、validation settlement、PM review/replan、补偿恢复和
  memory-review workspace 的单元测试已覆盖。
- 完整 service 测试执行 606 项：本次相关测试通过；另有 3 项既有
  `ProgressLedgerMigrationStaticTest` 失败。当前迁移目录只有 V9–V13，
  缺少该测试要求的 V8 之前核心建表迁移；另有 13 项跳过，其余 590 项通过。
  这 3 项失败与本次 agent loop 改动无关。
