# 重复调用下的任务进度发现与幂等续作方案（审核草案）

状态：APPROVED / 评审记录（正式决策见
[`ADR-001`](./adr-001-run-execution-progress-ledger.md)）

## 1. 正确的目标

本方案不追求“Agent 绝不被重复调用”。网络超时、服务重启和调度器重试决定了调用只能按
at-least-once 设计。

需要保证的是：

> 同一个业务任务即使产生多个 Run，后来的 Run 也能及时发现任务已经处理到哪一步；已完成的
> 步骤不再执行，正在处理的步骤不被并发重复处理，只接管或继续未完成的步骤。

因此要分开三个概念：

- `CodingTask/Task`：稳定的业务任务。
- `Execution`：该业务任务在某个需求版本和输入快照下的一次逻辑处理过程。
- `Run`：一次 Agent/CLI 调用 attempt。重复调用可以产生多个 Run，但共享同一个 Execution。

## 2. 一致性目标

### 2.1 必须保证

- Run 可以重复创建和启动。
- 所有重复 Run 使用稳定的 `executionKey` 找到同一份进度。
- 每次 Run 启动前必须读取最新进度快照。
- 每个步骤通过原子 claim 获得处理权，没有 claim 的 Run 不得处理该步骤。
- 已 `APPLIED/VERIFIED` 的步骤直接跳过。
- `IN_PROGRESS` 且租约有效的步骤由当前 owner 继续，重复 Run 等待或退出。
- 租约失效的步骤先对账实际效果，再决定标记完成或接管续作。
- Agent 完成某一步后立即落 checkpoint，不等整个 Run 结束。
- 平台控制的副作用使用稳定 effect key，重复提交返回第一次结果。

### 2.2 非目标

- 不保证只调用一次 Agent。
- 不要求一次 Run 完成整个任务。
- 不把 Agent 最终摘要作为唯一进度来源。
- 不承诺任意第三方系统天然 exactly-once；第三方写操作需要幂等键或平台代理。

## 3. 备选方案

### 方案 A：只恢复 session/worktree

重复调用恢复 Codex thread 和 worktree，由 Agent 自己查看文件、Git diff 和历史消息判断进度。

优点：改造少。

缺点：进度是推断结果；两个 Run 可能同时认为某一步未完成；无法对非文件副作用可靠去重。

### 方案 B：固定阶段状态机

平台只维护 `PREPARE / IMPLEMENT / VERIFY / DELIVER` 四个阶段，每阶段做 lease 和 checkpoint。

优点：模型简单，平台容易控制。

缺点：阶段太粗；“实现了 7 个文件中的 5 个”仍然不可见，重复 Run 可能重做整个 IMPLEMENT。

### 方案 C：平台阶段 + 动态步骤账本（推荐）

- 平台维护稳定的顶层阶段；
- Agent 或计划任务声明更细的动态步骤；
- 每个步骤有稳定 step key、输入哈希、状态、owner、lease、证据和副作用记录；
- 重复 Run 只 claim 未完成步骤。

优点：既能由平台控制并发，又能表达真实编码进度。

缺点：需要 Agent 遵守结构化 checkpoint 协议，并提供进度工具/API。

结论：推荐方案 C。

## 4. 数据模型

### 4.1 Execution

建议新增 `oc_task_execution`：

| 字段 | 用途 |
|---|---|
| `id` | execution ID |
| `execution_key` | 稳定幂等键，唯一 |
| `task_id/coding_task_id` | 业务任务 |
| `requirement_id/loop_id` | 所属需求版本 |
| `input_hash` | prompt、计划、基准提交等输入快照哈希 |
| `plan_version` | 当前步骤计划版本 |
| `status` | `PENDING/ACTIVE/COMPLETING/SUCCEEDED/FAILED/BLOCKED` |
| `requirement_workspace_id` | 该需求唯一续作工作区 |
| `base_commit` | 开始处理时的基准 |
| `latest_head` | 最近 checkpoint 的 Git HEAD |
| `active_run_id` | 当前主要 Run，仅用于观测 |
| `last_progress_at` | 最近有效进展时间 |

`executionKey` 建议由以下业务身份生成，而不是随机 request ID：

```text
taskType + taskId + loopId/planVersion + inputHash
```

同一输入下的调度重试命中同一 Execution；用户明确修改需求、计划或输入后生成新的 Execution。

#### Requirement workspace

建议新增 `oc_requirement_workspace`：

| 字段 | 用途 |
|---|---|
| `project_id/requirement_id` | 唯一业务键 |
| `workspace_path` | 需求唯一 worktree 路径 |
| `branch_name` | 需求唯一 feature branch |
| `base_commit/current_head` | 初始基线与当前已确认 HEAD |
| `active_loop_id` | 当前允许写入的需求轮次 |
| `owner_run_id/owner_execution_id` | 当前单写 owner |
| `lease_expires_at` | owner 租约 |
| `fencing_token` | 接管代次 |
| `state` | `ACTIVE/BLOCKED/DELIVERING/COMPLETED` |

数据库使用 `UNIQUE(project_id, requirement_id)` 保证一个需求只有一条 workspace 记录。不同 loop
复用同一 worktree，但 loop 切换时递增 fencing token，使旧 loop 的 Run 失去写权限。

### 4.2 Run

现有 `oc_run` 继续代表一次调用 attempt，并增加：

| 字段 | 用途 |
|---|---|
| `execution_id` | 多个 Run 共享同一 Execution |
| `invocation_key` | 本次调用幂等键 |
| `executor_id` | 执行器实例 |
| `thread_id/turn_id` | session 恢复与审计 |
| `heartbeat_at` | Run 活性 |
| `observed_plan_version` | 启动时读到的计划版本 |
| `resume_from_checkpoint_id` | 本次从哪个 checkpoint 继续 |

Run 状态只描述调用本身：

```text
QUEUED → RUNNING → SUCCEEDED / FAILED / CANCELLED / UNKNOWN
```

Run 失败不等于 Execution 失败。只要仍有可恢复步骤，Execution 可以继续保持 `ACTIVE`。

#### Run observation / verification

为每个 Run 增加追加式 `oc_run_observation`，而不是只在 `oc_run` 放一个会被覆盖的 remark：

| 字段 | 用途 |
|---|---|
| `run_id/sequence_no` | Run 内有序记录，联合唯一 |
| `observation_type` | `DISPATCH/ACCEPTED/PROGRESS/CHECKPOINT/TERMINAL/RECONCILIATION/MANUAL_REVIEW` |
| `verification_status` | `UNVERIFIED/CONFIRMED/INCONCLUSIVE/CONTRADICTED` |
| `source_type/source_id` | `SYSTEM/AGENT/USER/RECONCILER` 及来源 |
| `summary/detail` | 简要备注与详细说明 |
| `step_id/checkpoint_id` | 可选关联进度 |
| `evidence_data` | thread/turn、Git、日志、命令、测试和外部回执 |
| `supersedes_observation_id` | 更正旧判断，不修改历史 |
| `observed_at` | 发生时间 |

系统可以在 `oc_run` 冗余 `latest_observation_id` 和 `verification_status` 供列表查询，但权威历史来自
observation 表。

典型记录包括：

- “Run 已启动，但尚未获得 turn accepted 证据”；
- “已确认完成 step X，checkpoint commit 为 SHA”；
- “Run 超时，但 worktree/terminal event 证明任务已完成”；
- “Run 报告成功，但 required step Y 尚未验证”；
- “人工审核确认该 Run 未产生外部副作用”。

自由文本备注只用于解释；只有关联的结构化证据通过校验，才能影响 step/effect 的状态。

### 4.3 Step ledger

建议新增 `oc_execution_step`：

| 字段 | 用途 |
|---|---|
| `id` | step ID |
| `execution_id` | 所属逻辑执行 |
| `step_key` | 稳定步骤键 |
| `plan_version` | 所属计划版本 |
| `title/description` | 人和 Agent 可读的任务 |
| `input_hash` | 判断步骤定义是否改变 |
| `status` | 步骤状态 |
| `owner_run_id` | 当前处理者 |
| `claim_token` | 本次 claim 代次 |
| `lease_expires_at` | 处理租约 |
| `attempt_count` | 被 claim 次数 |
| `progress_percent` | 可选展示值，不作为一致性依据 |
| `checkpoint_data` | 结构化进度 |
| `evidence_data` | 文件、commit、测试、外部回执等证据 |
| `started_at/completed_at` | 生命周期 |
| `last_heartbeat_at` | 步骤活性 |

唯一约束：

```text
UNIQUE(execution_id, step_key, plan_version)
```

步骤状态：

```text
PENDING
  → IN_PROGRESS
  → APPLIED
  → VERIFIED

IN_PROGRESS → UNKNOWN / BLOCKED / FAILED
UNKNOWN → APPLIED / VERIFIED / IN_PROGRESS
```

- `APPLIED`：动作已经产生效果，但尚未验证。
- `VERIFIED`：效果已经验证，可永久跳过。
- `UNKNOWN`：调用结果不确定，必须先对账，不能直接重做。

### 4.4 Checkpoint journal

建议新增 append-only `oc_execution_checkpoint`：

| 字段 | 用途 |
|---|---|
| `execution_id/step_id/run_id` | 归属 |
| `sequence_no` | Execution 内单调序号 |
| `checkpoint_type` | `PLAN/CLAIM/PROGRESS/APPLIED/VERIFIED/RECONCILED` |
| `payload` | 结构化快照 |
| `evidence_digest` | 证据摘要 |
| `created_at` | 时间 |

step 表保存当前态，checkpoint 表保存历史。重复 Run 读取 step 当前态，排障和恢复读取 journal。

### 4.5 Effect ledger

建议新增 `oc_execution_effect`，记录平台受控副作用：

| 字段 | 用途 |
|---|---|
| `effect_key` | 全局或 execution 内唯一 |
| `execution_id/step_id` | 来源 |
| `effect_type` | `FILE_PATCH/GIT_COMMIT/MERGE/COMMENT/EVENT/MEMORY_JOB/EXTERNAL_CALL` |
| `request_hash` | 请求一致性校验 |
| `status` | `PREPARED/APPLIED/CONFIRMED/UNKNOWN/FAILED` |
| `result_snapshot` | 第一次执行结果 |
| `fencing_token` | 防止旧 owner 提交 |

重复 effect key：

- request hash 相同：返回第一次结果；
- request hash 不同：拒绝并进入冲突处理；
- 状态 `UNKNOWN`：先查询目标系统，不直接重发。

## 5. 进度协议

### 5.1 Run 启动前

每个 Run 必须执行统一 preflight：

1. 根据业务任务和输入计算 `executionKey`。
2. `find-or-create` Execution。
3. 读取 plan version、全部 step、checkpoint 和 effect 摘要。
4. 检查 worktree 当前 HEAD、未提交变更和已有测试结果。
5. 生成 `ExecutionProgressSnapshot`。
6. 将快照写入 `.agent_context/progress.json` 并注入 Agent prompt。
7. Agent 只能从快照中的 `nextActions` 开始工作。

如果 Execution 已成功，重复 Run 不启动 Agent，直接返回已有结果。这里允许“调度调用重复”，但避免
不必要的模型调用。

### 5.2 Agent 发现和声明步骤

顶层阶段由平台固定：

```text
DISCOVER → PLAN → IMPLEMENT → VERIFY → DELIVER
```

Agent 在 PLAN 阶段声明动态步骤，例如：

```text
implement:selection-service
implement:capacity-guard
implement:selection-controller
verify:selection-unit-tests
verify:module-compile
```

step key 必须语义稳定，不能使用本次 Run ID。计划改变时：

- 定义未变：沿用原 step；
- 定义改变：增加 plan version；
- 已完成但仍有效的 step 通过输入哈希/证据复用；
- 已失效 step 标记 `SUPERSEDED`，不能删除历史。

### 5.3 原子 claim

Run 处理步骤前必须调用：

```text
claimStep(executionId, stepKey, expectedStatus, runId)
```

数据库 CAS 条件：

```text
status in (PENDING, BLOCKED)
or (status = IN_PROGRESS and lease expired)
```

成功后写 `ownerRunId + claimToken + leaseExpiresAt`。失败时返回最新步骤：

- `VERIFIED/APPLIED`：跳过；
- `IN_PROGRESS` 且租约有效：不得并发处理，可等待、观察或结束本 Run；
- `UNKNOWN`：进入 reconcile；
- `SUPERSEDED`：重新读取计划。

### 5.4 执行中 checkpoint

不能只在步骤结束时记录。至少在以下时点 checkpoint：

- 完成分析并确定处理范围；
- 修改一组相关文件后；
- 获得外部操作回执后；
- 测试执行后；
- 准备进入耗时工具调用前后；
- Agent 即将结束或上下文接近上限时。

checkpoint 必须携带结构化证据，例如：

```json
{
  "changedFiles": ["..."],
  "gitHead": "...",
  "commands": [{"commandKey": "compile:module", "exitCode": 0}],
  "artifacts": [{"path": "...", "sha256": "..."}],
  "nextAction": "verify:selection-unit-tests"
}
```

自然语言摘要只用于展示，不用于判断步骤是否完成。

### 5.5 步骤完成

步骤完成采用两段语义：

1. `markApplied`：记录动作结果和证据；
2. `markVerified`：验证结果满足完成条件。

重复 Run 看到：

- `VERIFIED`：无条件跳过；
- `APPLIED`：只执行验证，不重新实施；
- `UNKNOWN`：先查证效果；
- `FAILED/BLOCKED`：根据恢复策略继续或等待人工处理。

## 6. 三类失败下的行为

### 6.1 调用失败，Agent 什么都没做

表现：

- Run 失败；
- 没有新的 step claim；
- 没有 checkpoint/effect；
- worktree 和 HEAD 无变化。

处理：

- Execution 保持原状态；
- 下一个 Run 读取同一进度快照；
- claim 原来的第一个未完成 step；
- 不需要把旧 Run 当作任务失败。

### 6.2 过程失败，做了一点但没做完

表现：

- 某些 step 已 `VERIFIED`；
- 当前 step 可能是 `IN_PROGRESS/APPLIED/UNKNOWN`；
- worktree、checkpoint、thread 中有部分结果。

处理：

1. 跳过所有 `VERIFIED`；
2. 对 `APPLIED` 只补验证；
3. 对租约失效的 `IN_PROGRESS` 检查文件、Git、测试和 effect；
4. 能证明完成则 reconcile 为 `APPLIED/VERIFIED`；
5. 未完成则新 Run claim 后从 checkpoint 的 `nextAction` 继续；
6. 不重新执行已经完成的 step。

### 6.3 实际做完，但 Run 超时

表现：

- Run 可能为 `UNKNOWN/FAILED`；
- step/effect/worktree 或 Codex session 已有完成证据。

处理：

1. 重复 Run preflight 先读进度账本；
2. 对账 worktree HEAD、变更、测试回执、Codex terminal event 和 effect；
3. 将已有结果补记为 `APPLIED/VERIFIED`；
4. 所有必要 step 已 `VERIFIED` 时直接收口 Execution；
5. 不重新执行 Agent 已完成的工作。

## 7. 进度发现工具

仅靠 prompt 要求 Agent“先检查”不够可靠。建议提供平台进度工具，可实现为内部 MCP 或 CLI：

```text
progress.get(executionId)
progress.declareSteps(executionId, planVersion, steps)
progress.claimStep(executionId, stepKey)
progress.heartbeat(executionId, stepKey, claimToken)
progress.checkpoint(executionId, stepKey, claimToken, payload)
progress.markApplied(executionId, stepKey, claimToken, evidence)
progress.markVerified(executionId, stepKey, claimToken, evidence)
progress.reconcile(executionId, stepKey, evidence)
effect.execute(effectKey, requestHash, request)
```

执行策略：

- preflight 由平台强制执行；
- claim 和 effect 幂等由服务端强制执行；
- checkpoint/complete 工具写数据库；
- `.agent_context/progress.json` 是 Agent 可读镜像，不是权威存储；
- Agent brief 明确禁止处理未 claim 的步骤。

## 8. Worktree 与并发

同一 Requirement 使用一个唯一 worktree，需求下所有 Execution、step 和重复 Run 共享：

```text
<workspace-root>/requirement-<requirement-id>/worktree
<workspace-root>/requirement-<requirement-id>/codex-home/<execution-id>
<workspace-root>/requirement-<requirement-id>/progress-mirror.json
```

- 数据库唯一约束保证同一项目和需求只注册一个 worktree。
- 同一 Requirement 同时只允许一个写 owner；锁范围不是 Run 或 Execution。
- 重复 Run 可以读取进度，但只有持有 requirement lease、文件锁和最新 fencing token 的 Run 可以写。
- active loop 改变时 worktree 继续复用，但旧 loop 的 fencing token 立即失效。
- 不同 Requirement 使用不同 worktree，可以并行。
- merge/delivery 使用 effect key，并校验 requirement fencing token。

Codex `threadId/turnId` 必须落库，`CODEX_HOME` 在 Execution 完成和保留期到期前不能删除。

### 8.1 需求 Worktree 的文件一致性

同一需求的步骤不再跨多个 worktree 集成，而是在同一 feature branch 上顺序推进：

1. 创建需求 worktree 时固定初始 `baseCommit` 和唯一 feature branch。
2. 每个 step claim 时记录 `stepBaseCommit = requirementWorkspace.currentHead`。
3. 写入前同时校验数据库 current HEAD、实际 Git HEAD、owner lease 和 fencing token。
4. step 达到 `APPLIED` 时生成 checkpoint commit，并记录：
   - commit SHA；
   - parent SHA；
   - changed files 和 content hash；
   - required verification 证据；
   - execution、step 和 plan version。
5. checkpoint commit 成功后，CAS 更新 requirement workspace 的 `currentHead`。
6. 下一个步骤只能从该 `currentHead` 开始，因此天然包含之前所有已应用步骤。
7. Run 崩溃时若存在未提交变更，接管者先根据 checkpoint journal、Git diff 和 patch artifact 对账；
   不得直接清空或覆盖工作区。
8. 跨主机接管时，从远端 checkpoint ref 或持久化 patch artifact 重建到已记录的 `currentHead`。

这样，同一需求内不需要 cherry-pick 多个 Execution branch；步骤顺序本身就是 feature branch 的提交
顺序。checkpoint commit 可在交付时由 GitLab squash merge 压缩，不影响恢复账本。

### 8.2 MR 交付协议

平台交付边界保持现状：负责生成并推送 requirement feature branch，创建或更新 MR，不自动合并
目标分支：

1. 获取 `requirement + loop` 级 delivery lease。
2. `git fetch origin`，读取最新 `origin/main`（或配置的 target branch）。
3. 将最新 target branch 合入 requirement feature branch；发生冲突则进入 `DELIVERY_BLOCKED`，
   冲突解决作为新的 remediation step 在同一 requirement worktree 内执行。
4. 在最终候选 commit 上重新执行 required verification，旧 HEAD 上的测试结果不能复用。
5. 使用稳定 effect key 推送 requirement feature branch：

   ```text
   delivery:<requirementId>:<loopId>:<candidateCommit>
   ```

6. 创建或更新唯一 MR，并记录 source SHA、target SHA、MR IID 和 pipeline ID。
7. GitLab 确认 MR 已创建或更新且 source SHA 正确后，MR delivery effect 进入 `CONFIRMED`。
8. Requirement 自动化流程进入 `COMPLETED`，其含义明确为“MR 已提交”，不是“代码已合入 main”。
9. 后续 CI、CODEOWNERS 审查和人工合并由现有仓库流程负责；平台不调用 GitLab merge API。
10. 可通过 webhook/轮询附加记录 `OPEN/MERGED/CLOSED` 状态用于展示，但不改变本方案的自动交付
    完成边界。

因此这里保证的是：

- MR 中的 source commit 对应已验证的 requirement worktree HEAD；
- 本地准备、push 或 MR 创建失败时保持 `DELIVERY_BLOCKED/DELIVERING`；
- 重复交付调用命中同一个 effect/MR，并从已知阶段继续；
- 自动化 `COMPLETED` 只表示 MR 已成功提交；是否进入 main 以 GitLab MR 状态为准。

## 9. 超时与补偿

补偿器不再依据 Run 超时直接把业务任务变成可重跑状态，而是：

1. 标记 Run `UNKNOWN/FAILED`；
2. 释放或等待 step lease；
3. 触发 `ProgressReconciler`；
4. 根据 checkpoint/effect/worktree/session 重建步骤状态；
5. 创建或接受新的 Run；
6. 新 Run 从最新进度继续。

超时后的核心问题从：

```text
“要不要重发任务？”
```

变为：

```text
“哪些步骤已有可信效果，剩余哪个步骤可以被安全 claim？”
```

## 10. 完成判定

完成状态按三层独立判定，不能由某个 Run 的摘要或退出状态直接向上传播。

### Run

- 只表示一次 Agent/CLI 调用是否结束；
- `SUCCEEDED` 不代表 Execution 或 Requirement 完成；
- `FAILED/UNKNOWN` 不代表其已经产生的步骤进度无效。

### Execution

- 当前 plan version 的所有 required step 为 `VERIFIED`；
- 不存在 required `UNKNOWN/IN_PROGRESS/BLOCKED` step；
- 所属必要 effect 为 `CONFIRMED`；
- checkpoint commit 与 requirement workspace `currentHead` 链一致；
- optional 或已 `SUPERSEDED` 的 step 不阻塞完成。

### Requirement 自动化

- 当前 active loop 的所有 required Execution 已完成；
- 需求级最终验证 step 为 `VERIFIED`；
- MR delivery effect 为 `CONFIRMED`；
- MR source SHA 等于已验证的 requirement workspace HEAD；
- 状态 `COMPLETED` 表示“MR 已提交”，不表示已经合入 main。

某个 Run 成功但 required step 未完成，Execution 仍为 `ACTIVE`。

某个 Run 失败但所有 required step 已验证，Execution 可以直接 `SUCCEEDED`。所有层级 settlement
分别使用稳定幂等键，重复收口不得产生重复事件或评论。

## 11. 前端可观测性

前端必须让用户直接看见“同一个需求目前处理到哪里、哪个 Run 在处理、重复 Run 为什么没有重做”，
不能要求用户从 Agent 摘要或原始日志自行推断。

### 11.1 权威查询契约

在需求工作台提供一个聚合查询，避免前端分别请求 Requirement、Execution、step、Run 后自行拼装状态：

```text
GET /executionProgress/findOverview?requirementId=<id>
```

响应至少包含：

- Requirement 自动化状态和独立的 MR 状态；
- 当前 active loop、plan version 和 `snapshotVersion`；
- requirement workspace、feature branch、current HEAD；
- 当前 owner Run、owner Execution、lease 到期时间和服务端计算的 stale 状态；
- required/optional/superseded step 数量及各状态统计；
- 当前步骤、上一个可信 checkpoint 和建议的 `nextAction`；
- 最近 Run 及其最新 observation；
- unresolved `UNKNOWN/BLOCKED` 数量；
- `serverTime/updatedAt/staleAfter`。

详情使用分页或按需查询：

```text
GET /executionProgress/findSteps?executionId=<id>
GET /runObservation/findByRun?runId=<id>&page=<n>&rows=<n>
GET /executionProgress/findCheckpoints?stepId=<id>&page=<n>&rows=<n>
GET /executionProgress/findEffects?executionId=<id>&status=<status>
```

聚合响应中的状态全部由后端依据账本推导。前端不得通过 Run 退出码、`summary/failureReason`、日志文本或
MR 评论反推 Execution/Requirement 是否完成。

### 11.2 增量刷新与断线降级

沿用现有需求工作台的状态轮询，并增加需求级轻量进度事件通道：

```text
/ws/requirement/<requirementId>/progress
```

事件类型至少包括：

```text
execution.updated
step.updated
run.updated
run.observation-added
effect.updated
workspace.lease-changed
delivery.updated
```

事件只携带 `requirementId/entityId/snapshotVersion/occurredAt` 和必要摘要；收到事件后重新读取权威聚合
快照，不把 WebSocket 消息直接当最终状态。前端只接受大于当前 `snapshotVersion` 的版本，避免乱序
事件把页面回滚。

- WebSocket 正常时，进度变化应在 3 秒内可见；
- WebSocket 断开时，沿用当前活动状态 5 秒、非活动状态 30 秒的轮询；
- 页面从后台恢复时立即刷新；
- 连续刷新失败时保留最后成功快照，并明确显示“数据可能已过期”和最后更新时间；
- 重新连接后总是全量刷新一次，不能依赖补齐所有漏失事件；
- Run 原始日志继续使用现有 `/ws/run/{logStreamKey}`，与结构化进度事件职责分离。

### 11.3 需求工作台视图

复用现有 `RequirementWorkspace`，不新增独立运维控制台：

1. 扩展 `AutomationStatusBar`：
   - 分开显示自动化状态与 MR `NOT_SUBMITTED/SUBMITTED/OPEN/MERGED/CLOSED`；
   - 显示当前阶段、步骤完成数、active owner、最近进展时间；
   - `COMPLETED` 的中文文案固定为“自动化完成（MR 已提交）”，不得展示成“已合入”。
2. 在右侧区域增加“执行进度”页签：
   - 按固定阶段和动态步骤展示树形进度；
   - 明确区分 `APPLIED`、`VERIFIED`、`UNKNOWN`、`BLOCKED`；
   - 显示 claim owner、lease/stale、attempt 数、checkpoint 和 `nextAction`；
   - 已完成步骤显示“后续 Run 将跳过”，正在处理显示 owner，接管步骤显示接管原因。
3. 保留现有 Run 页签并增强：
   - 展示 `runNo/attemptNo/executionId/resumeFromCheckpoint`；
   - 展示最新 observation 摘要与验证状态；
   - 明确区分“Run 失败”和“任务失败”。
4. 扩展 `RunLogDrawer`：
   - “执行记录”时间线展示追加式 observations；
   - “原始日志”保留现有实时日志；
   - “证据”按需展示关联 step、checkpoint、commit、测试报告和 effect 回执；
   - 更正记录显示其 supersedes 关系，不覆盖旧记录。
5. 对 `UNKNOWN/BLOCKED/lease stale` 提供醒目标识：
   - 普通用户可以查看原因和恢复建议；
   - 有权限的用户可以追加人工 observation；
   - 人工处理 effect 必须调用后端受控操作，不能由前端直接改 step/effect 状态。

### 11.4 前端状态与安全约束

- 前端类型必须覆盖所有服务端枚举；未知枚举显示“未知状态（原值）”，不能默认映射成成功。
- `progressPercent` 只作展示，完成标识只能来自 `VERIFIED` 和后端聚合结果。
- stale 使用服务端返回值和服务端时间计算，避免浏览器时钟差导致错误接管提示。
- observation、checkpoint 和 effect 分页加载；证据内容按需加载，避免需求页一次拉取全部日志。
- 绝对 worktree 路径、prompt、日志和外部回执在后端脱敏；前端只展示当前用户有权访问的字段。
- 页面所有人工操作记录操作者、原因和 observation，不提供无审计的“直接改成功”按钮。

## 12. 迁移顺序

### Phase 1：进度观测

- 增加 Execution、step 和 checkpoint 表。
- 现有 Run 绑定 Execution。
- 在 Agent 调用前生成 progress snapshot。
- 保存 thread/turn、Git HEAD、changed files 和测试结果。
- 提供需求进度聚合查询，在现有工作台展示只读进度和 Run observations。

### Phase 2：强制 step claim

- 提供 progress API/MCP。
- Agent brief 注入 checkpoint 协议。
- 顶层阶段先使用固定步骤。
- 重复 Run 启动后能跳过已完成阶段。

### Phase 3：动态细粒度步骤

- PLAN 阶段声明动态 step。
- 增加 plan version、input hash、证据复用和 supersede。
- 编码过程中强制 checkpoint。

### Phase 4：effect ledger 与对账

- 评论、事件、memory job、push/MR 等平台副作用接入 effect key。
- 增加 `ProgressReconciler`。
- 超时补偿改为进度对账，不直接重置任务。
- 增加需求级进度 WebSocket 事件，并保留轮询降级。

### Phase 5：恢复与交付增强

- 保留 Codex session 并支持 resume。
- 完成 requirement workspace 跨进程接管与 checkpoint artifact 恢复。
- 完成历史 Run 兼容和旧补偿逻辑清理。

## 13. 验收场景

1. 同一调度请求发送两次，产生两个 Run：只有一个能 claim 当前 step。
2. 第一个 Run 未启动即失败：第二个 Run 从第一个未完成 step 开始。
3. 第一个 Run 完成三个步骤后崩溃：第二个 Run 跳过三个 `VERIFIED` step。
4. 文件已经修改但未验证：第二个 Run 只运行验证。
5. 外部调用响应丢失：effect 进入 `UNKNOWN`，先查询，不直接重发。
6. Agent 已完成但终态回调丢失：对账证据后直接完成 Execution。
7. 两个 Run 同时读取 PENDING：CAS 保证只有一个 claim 成功。
8. owner lease 失效后旧 Run 恢复：旧 claim token 的 checkpoint/effect 被拒绝。
9. 计划更新：有效完成步骤被复用，失效步骤被 supersede。
10. Run 报告失败但所有 required step 已验证：Execution 判定成功。
11. 重复 Run 启动后，需求工作台在 3 秒内显示新的 Run、共享 Execution 和当前 owner，已验证步骤
    显示为跳过。
12. Run 超时但实际完成：页面先显示 Run `UNKNOWN`，对账后显示相关 step 已验证；历史判断和更正
    均保留在 observation 时间线。
13. WebSocket 断开：页面显示连接/过期提示并继续轮询；恢复连接后全量刷新到最新
    `snapshotVersion`。
14. WebSocket 事件乱序或重复：页面状态不回退，也不重复追加同一 observation。
15. 自动化完成但 MR 未合并：页面显示“MR 已提交”，MR 状态单独显示 `OPEN`，不显示“已合入”。
16. 用户刷新或换浏览器：从服务端恢复相同的 step、checkpoint、Run observation 和 unresolved effect
    视图，不依赖浏览器本地状态。

## 14. 风险

- 动态 step key 不稳定会导致无法复用进度，需要定义命名和版本规则。
- Agent 忘记 checkpoint 会降低恢复精度，因此关键 checkpoint 必须由 runner/tool wrapper 自动补充。
- 文件变更是否“完成”不能仅由文件存在判断，必须配合 hash、Git、测试或业务验证证据。
- 同一 worktree 并行写入容易冲突，第一阶段应保持单写 owner。
- 第三方 API 若无查询接口也无幂等键，`UNKNOWN` effect 无法安全自动处理。
- 当前 CodexRunner 删除 `CODEX_HOME`，会破坏 session 恢复，必须调整生命周期和 GC。
- 聚合查询若直接展开全部 journal/log 会放大数据库和网络负载，必须限制摘要范围并分页加载详情。
- WebSocket 事件可能重复、乱序或漏失，必须以 `snapshotVersion` 和重新查询权威快照收敛。
- checkpoint 和日志可能包含路径、prompt 或凭证，必须在服务端做字段级授权和脱敏。

## 15. 待审核决策

### D1 步骤粒度和声明权（已批准）

批准采用“平台固定阶段 + Agent 动态子步骤”：

- 平台固定 `DISCOVER/PLAN/IMPLEMENT/VERIFY/DELIVER`；
- Agent 在 PLAN 中声明实现和验证子步骤；
- 平台通过 stable step key、plan version 和 input hash 管理复用。

### D2 重复 Run 的并发策略（已批准，后续修订）

批准采用 Requirement 级单写：

- 同一 Requirement 只允许一个写 owner；
- 重复 Run 可以启动并读取进度；
- 发现活跃 owner 后返回“任务处理中”，不并发修改；
- owner 租约失效后，新 Run 对账并接管；
- active loop 切换时递增 fencing token，旧 loop/Run 不能继续写；
- 不同 Requirement 可以并行。

### D3 完成可信证据（已批准）

批准采用类型化证据策略，并区分“动作已发生”和“结果已验证”：

- 代码实现 `APPLIED`：changed files + content hash + Git diff/commit；
- 代码实现 `VERIFIED`：产物证据有效，且其关联的 required verify step 已通过；
- 编译测试：command spec hash + exit code + environment hash + report digest，并绑定被测 Git HEAD；
- 外部操作：effect key + request hash + 目标系统回执或查询结果；
- 交付：MR effect + source commit + MR IID/URL；
- Agent 的文字结论只用于展示，不能单独把 step 标记为 `VERIFIED`。

### D4 Checkpoint 强制方式（已批准）

批准采用平台自动采集与 Agent 主动提交双通道，并按重要性区分失败策略：

- claim、plan 发布、`markApplied`、`markVerified`、effect 前后记录属于关键 checkpoint；
- 关键 checkpoint 写入失败时 fail-closed：暂停当前步骤，不能继续产生新的平台副作用；
- 工具完成、文件变化、Git HEAD、测试报告和 terminal event 由 runner 自动采集；
- Agent 主动提交当前结论、剩余事项和 `nextAction`；
- 周期性 progress/heartbeat 属于中间 checkpoint，可短暂失败并重试，不立即终止 Agent；
- 自动采集按事件触发并做 debounce，避免每条日志都写数据库。

### D5 Worktree 模型（已批准，后续修订）

批准每个 Requirement 一个唯一 worktree：

- 同一需求下所有 Execution、step 和重复 Run 共享 worktree；
- 数据库 `UNIQUE(project_id, requirement_id)` 保证唯一注册；
- worktree 绑定唯一 requirement feature branch；
- 接管前必须确认旧 owner 租约失效，并获得 Requirement owner lease 和本机文件锁；
- 旧 Agent 进程仍存在时先终止并等待退出，再允许新 Run 写入；
- 每个 `APPLIED` step 生成 checkpoint commit，下一步骤从已确认 HEAD 继续；
- 未提交变更通过 checkpoint journal 和持久化 patch artifact 对账恢复；
- active/unknown/blocked Requirement 禁止 GC；
- Requirement 终态后默认保留 7 天再 GC，保留期配置化；
- 在最新远端 target HEAD 上合入、解决冲突并重新验证；
- 通过唯一 MR 和 delivery effect 完成交付；
- 自动化完成只表示 MR 已成功提交，不表示代码已经合入 main。

### D5a 最终合并授权方式（已批准）

批准保持现有 MR 交付方式：

- 平台只 commit、push 并创建或更新 MR；
- 不调用 GitLab merge API；
- CI、CODEOWNERS 审查和最终合并继续由现有仓库流程负责；
- MR 创建/更新成功即表示自动化交付完成；
- 平台必须在状态和文案上区分“MR 已提交”和“已合入 main”。

### D6 UNKNOWN effect 策略（已批准）

批准按能力分级处理结果未知的副作用：

1. 可查询：先查询目标状态并对账，不直接重发。例如：
   - push 超时后查询远端 branch SHA；
   - MR 创建超时后按 project + source branch 查询已有 MR；
   - memory job 按 idempotency key 查询。
2. 不可查询但支持幂等键：使用相同 effect/idempotency key 重发，并复用首次结果。
3. 既不可查询也不支持幂等：标记 effect `UNKNOWN`，暂停对应 step，等待人工确认，禁止自动重发。

任何 UNKNOWN effect 未解决前，其所属 required step 不能进入 `VERIFIED`。

### D7 Execution 完成规则（已批准）

批准采用 Run、Execution、Requirement 分层判定：

- Run 只记录一次调用结果；
- Execution 在当前 plan version 的 required step 全部 `VERIFIED`、必要 effect 全部 `CONFIRMED`
  后完成；
- optional 和 `SUPERSEDED` step 不阻塞；
- Requirement 在 active loop 的 required Execution、最终验证和 MR delivery 全部完成后进入自动化
  `COMPLETED`；
- 自动化 `COMPLETED` 的含义是“MR 已提交”，不是“已合入 main”；
- 下层失败不能覆盖已经验证的上层进度，所有 settlement 使用稳定幂等键。

### D8 Run 备注与执行验证（已批准）

- 系统、Agent、对账器和用户都可以追加 observation；
- observation 记录类型、判断状态、来源、备注、关联 step/checkpoint 和结构化证据；
- 历史不可修改，更正通过 `supersedesObservationId` 追加；
- Run 列表展示最新验证状态和摘要，详情页展示完整时间线；
- observation 不能只靠文字改变 step/effect 状态，必须经过证据校验。

### D9 前端进度刷新模型（已批准）

- 复用现有 RequirementWorkspace，不新建独立控制台；
- 增加“执行进度”页签，Run 页签继续承担 attempt 历史和原始日志查看；
- `/ws/requirement/{id}/progress` 只通知版本变化，前端收到后读取聚合快照；
- WebSocket 断开时使用现有 5 秒/30 秒轮询，恢复后强制全量刷新；
- `snapshotVersion` 防止重复和乱序事件造成 UI 状态回退；
- 自动化状态、Run 状态、步骤验证状态和 MR 状态分开展示。

## 16. 审核与实施门禁

- D1-D9 已于方案审核中批准。
- 正式决策见 [`ADR-001`](./adr-001-run-execution-progress-ledger.md)。
- 实施字段和约束见
  [`run-execution-progress-data-model.md`](./run-execution-progress-data-model.md)。
- 下一步再拆分数据库、progress service、runner、前端、补偿器和独立验收任务；本轮不编码。
- 验收任务由 test-agent 执行，不在 coding task 后隐式触发。
