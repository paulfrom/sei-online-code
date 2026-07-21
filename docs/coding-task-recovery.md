# CodingTask 计划任务恢复语义

## 目标

执行计划是开发自动化的唯一任务真相源。系统以 `CodingTask` 作为恢复单元，以 `Run` 表示一次执行尝试，
失败或超时后继续使用同一需求工作区，避免丢弃 Agent 已经落地的有效代码。

进度账本是可观测与审计辅助设施。账本不可用、写入失败或摘要生成失败时，只记录告警，不能改变
Agent 执行结果，也不能阻断 `CodingTask` 的正常成功收口。

## 对象职责

| 对象 | 职责 |
|---|---|
| `ExecutionPlan` | 定义任务、依赖、文件范围和验收任务 |
| `CodingTask` | 可独立调度和恢复的计划任务 |
| `Run` | `CodingTask` 的一次执行尝试 |
| 需求工作区 / Git | 保存跨 Run 的实际代码成果 |
| 进度账本 | 保存 Execution 关联、历史摘要和观察证据，不拥有另一套执行计划 |

## Run 恢复链

同一 `CodingTask` 的后续 Run 通过 `parentRunId` 指向上一次尝试，并递增 `attemptNo`：

```text
CodingTask C
  ├─ Run #1  FAILED / TIMEOUT
  └─ Run #2  parentRunId=Run #1, attemptNo=2
```

恢复 Run 使用原需求工作区。系统把上一次状态、终止原因、失败原因和当前 Git 差异写入提示，要求
Agent 先检查已有成果，只继续未完成或未通过验收的部分。

首次 Run 仍必须产生任务范围内的代码或文档变更。恢复 Run 如果没有产生新的文件增量，可以复用
上一次 Run 留下的变更，但这些变更必须落在当前 `CodingTask.fileScope` 内；后续计划中的独立验收任务
负责验证最终结果。

## 超时收敛

超时 Run 是已经终止的执行尝试，状态收敛为：

- `Run.state = FAILED`
- `Run.terminalReason = TIMEOUT`
- 对应的运行中 `CodingTask.status = FAILED`

其他协议产生的内部进度步骤仍可由 `ProgressReconciler` 标记为 `UNKNOWN` 并释放租约，但 `UNKNOWN`
不再用于表示一个仍在执行的 Run。CodingTask 自身不再创建固定 implement/verify 步骤。补偿流程按照
既有重试次数和退避窗口，把失败的计划任务重新置为可调度状态，并创建新的恢复 Run。

## 非阻断账本边界

以下账本操作全部采用 best-effort 语义：

- 执行前 preflight 与 Execution 绑定；
- Agent 提示中的进度摘要；
- Agent 成功后的变更证据 observation；
- Run 终态 observation 追加。

任何上述操作失败都只产生告警。任务成功仍由 Agent 进程结果、工作区成果和计划中的独立验收决定。

## 会话边界

当前 runner 每次 Run 都启动新进程；Codex 也会创建新的 thread。因此恢复能力建立在持久化工作区、
Git 差异、执行计划和失败上下文之上，而不是依赖 Agent 会话一定可恢复。未来可以把同线程续作作为
优化，但会话丢失不能影响恢复正确性。
