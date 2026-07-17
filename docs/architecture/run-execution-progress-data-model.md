# Run / Execution 进度账本数据模型

状态：Approved design，尚未生成数据库迁移

关联决策：
[`ADR-001：重复 Run 下的执行进度账本与幂等续作`](./adr-001-run-execution-progress-ledger.md)

## 1. 建模约定

- 数据库沿用 PostgreSQL。
- 主键沿用 sei-core `BaseAuditableEntity` 的 36 字符字符串 ID 和审计字段。
- 实体保持当前扁平 ID 引用风格，不在 JPA 层引入跨聚合对象关系。
- 参与一致性判断、claim 和查询的字段必须独立成列。
- checkpoint、observation、effect 的扩展证据使用带 `schemaVersion` 的 JSON `TEXT`；首期不依赖
  JSON 内字段索引。
- 时间判断以数据库/服务端时间为准。
- 审计表默认 `ON DELETE RESTRICT` 或不提供删除接口，不级联删除历史。
- 本文定义逻辑模型和必要约束；实际 DDL 需作为独立数据库任务复核 PostgreSQL 版本及现有
  `BaseAuditableEntity` 列。

## 2. 关系概览

```text
Requirement 1 ── 1 RequirementWorkspace
Requirement 1 ── N TaskExecution
TaskExecution 1 ── N Run
TaskExecution 1 ── N ExecutionStep
ExecutionStep 1 ── N ExecutionCheckpoint
ExecutionStep 1 ── N ExecutionEffect
Run 1 ── N RunObservation

RequirementWorkspace.currentHead
    └── 按 checkpoint commit 串联该需求所有已应用步骤
```

`RequirementWorkspace` 是写入协调根；`TaskExecution` 是逻辑执行根；Run 是 attempt，不是完成状态
聚合根。

## 3. `oc_requirement_workspace`

每个项目需求唯一的 worktree、feature branch 和写 owner。

| 字段 | 建议类型 | 约束/说明 |
|---|---|---|
| `project_id` | `VARCHAR(36)` | NOT NULL |
| `requirement_id` | `VARCHAR(36)` | NOT NULL |
| `workspace_path` | `VARCHAR(500)` | NOT NULL；服务端使用，API 默认不返回 |
| `branch_name` | `VARCHAR(200)` | NOT NULL |
| `base_commit` | `VARCHAR(64)` | NOT NULL |
| `current_head` | `VARCHAR(64)` | NOT NULL |
| `active_loop_id` | `VARCHAR(64)` | 可空 |
| `owner_run_id` | `VARCHAR(36)` | 可空；当前写 owner |
| `owner_execution_id` | `VARCHAR(36)` | 可空 |
| `lease_expires_at` | `TIMESTAMP` | 可空 |
| `fencing_token` | `BIGINT` | NOT NULL DEFAULT 0；接管时递增 |
| `snapshot_version` | `BIGINT` | NOT NULL DEFAULT 0；任一可观测进度提交时递增 |
| `state` | `VARCHAR(32)` | `ACTIVE/BLOCKED/DELIVERING/COMPLETED` |
| `last_progress_at` | `TIMESTAMP` | 最近可信进展 |
| `retention_until` | `TIMESTAMP` | 终态后的 GC 时间 |

必要约束和索引：

```text
UNIQUE (project_id, requirement_id)
UNIQUE (project_id, branch_name)
INDEX  (owner_run_id)
INDEX  (state, retention_until)
CHECK  (fencing_token >= 0 AND snapshot_version >= 0)
```

`owner_run_id/owner_execution_id` 不建议建立指向 Run/Execution 的物理外键，以避免 workspace →
execution → workspace 的循环插入依赖；其有效性由 claim 事务校验。

## 4. `oc_task_execution`

同一业务任务与输入快照的一次逻辑执行。

| 字段 | 建议类型 | 约束/说明 |
|---|---|---|
| `execution_key` | `VARCHAR(128)` | NOT NULL；业务身份哈希或规范化 key |
| `task_type` | `VARCHAR(32)` | NOT NULL；如 `CODING_TASK/VALIDATION/DELIVERY` |
| `business_task_id` | `VARCHAR(36)` | NOT NULL |
| `coding_task_id` | `VARCHAR(36)` | 可空，兼容非 CodingTask 执行 |
| `requirement_id` | `VARCHAR(36)` | NOT NULL |
| `loop_id` | `VARCHAR(64)` | NOT NULL |
| `input_hash` | `VARCHAR(64)` | NOT NULL |
| `plan_version` | `INTEGER` | NOT NULL |
| `status` | `VARCHAR(32)` | `PENDING/ACTIVE/COMPLETING/SUCCEEDED/FAILED/BLOCKED` |
| `requirement_workspace_id` | `VARCHAR(36)` | NOT NULL |
| `base_commit` | `VARCHAR(64)` | NOT NULL |
| `latest_head` | `VARCHAR(64)` | 可空 |
| `active_run_id` | `VARCHAR(36)` | 可空，仅用于观测 |
| `last_progress_at` | `TIMESTAMP` | 可空 |
| `settlement_key` | `VARCHAR(128)` | 可空；完成收口幂等键 |
| `version` | `BIGINT` | NOT NULL DEFAULT 0；CAS/乐观锁 |

必要约束和索引：

```text
UNIQUE (execution_key)
UNIQUE (settlement_key) WHERE settlement_key IS NOT NULL
INDEX  (requirement_id, loop_id, status)
INDEX  (business_task_id, input_hash)
INDEX  (requirement_workspace_id)
CHECK  (plan_version > 0 AND version >= 0)
```

推荐的规范化 key 输入：

```text
taskType + businessTaskId + loopId + planVersion + inputHash
```

最终存储可使用该字符串的 SHA-256，原始组成字段仍独立保存用于审计。

## 5. `oc_run` 增量字段

现有 `oc_run` 保留，增加：

| 字段 | 建议类型 | 约束/说明 |
|---|---|---|
| `execution_id` | `VARCHAR(36)` | 新数据 NOT NULL；迁移期允许旧记录为空 |
| `invocation_key` | `VARCHAR(128)` | 调度调用幂等键 |
| `executor_id` | `VARCHAR(100)` | 执行器实例 |
| `thread_id` | `VARCHAR(128)` | Codex/session thread |
| `turn_id` | `VARCHAR(128)` | 当前或最后 turn |
| `heartbeat_at` | `TIMESTAMP` | Run 活性 |
| `observed_plan_version` | `INTEGER` | 启动快照版本 |
| `resume_from_checkpoint_id` | `VARCHAR(36)` | 本次恢复点 |
| `latest_observation_id` | `VARCHAR(36)` | 列表查询冗余 |
| `verification_status` | `VARCHAR(32)` | `UNVERIFIED/CONFIRMED/INCONCLUSIVE/CONTRADICTED` |

Run 状态扩展为：

```text
QUEUED / RUNNING / SUCCEEDED / FAILED / CANCELLED / UNKNOWN
```

必要约束和索引：

```text
UNIQUE (invocation_key) WHERE invocation_key IS NOT NULL
INDEX  (execution_id, created_date DESC)
INDEX  (requirement_id, state, heartbeat_at)
INDEX  (thread_id)
```

相同 `invocationKey` 重入返回同一个 Run；新的重复调度 attempt 使用新的 invocation key，但命中同一
Execution。

## 6. `oc_execution_step`

Execution 当前态账本。

| 字段 | 建议类型 | 约束/说明 |
|---|---|---|
| `execution_id` | `VARCHAR(36)` | NOT NULL |
| `step_key` | `VARCHAR(160)` | NOT NULL；语义稳定，不含 Run ID |
| `phase` | `VARCHAR(32)` | 固定顶层阶段 |
| `plan_version` | `INTEGER` | NOT NULL |
| `title` | `VARCHAR(200)` | NOT NULL |
| `description` | `TEXT` | 可空 |
| `input_hash` | `VARCHAR(64)` | NOT NULL |
| `required_step` | `BOOLEAN` | NOT NULL DEFAULT TRUE |
| `status` | `VARCHAR(32)` | 步骤状态 |
| `owner_run_id` | `VARCHAR(36)` | 可空 |
| `claim_token` | `VARCHAR(64)` | 可空；每次 claim 重新生成 |
| `workspace_fencing_token` | `BIGINT` | 可空；claim 时捕获 |
| `lease_expires_at` | `TIMESTAMP` | 可空 |
| `attempt_count` | `INTEGER` | NOT NULL DEFAULT 0 |
| `progress_percent` | `INTEGER` | 仅展示 |
| `latest_checkpoint_id` | `VARCHAR(36)` | 可空；查询冗余 |
| `checkpoint_data` | `TEXT` | 当前 checkpoint JSON 镜像 |
| `evidence_data` | `TEXT` | 当前证据 JSON 镜像 |
| `last_heartbeat_at` | `TIMESTAMP` | 可空 |
| `started_at` | `TIMESTAMP` | 可空 |
| `applied_at` | `TIMESTAMP` | 可空 |
| `completed_at` | `TIMESTAMP` | 可空 |
| `version` | `BIGINT` | NOT NULL DEFAULT 0 |

必要约束和索引：

```text
UNIQUE (execution_id, step_key, plan_version)
INDEX  (execution_id, plan_version, status)
INDEX  (owner_run_id, lease_expires_at)
CHECK  (attempt_count >= 0)
CHECK  (progress_percent IS NULL OR progress_percent BETWEEN 0 AND 100)
```

状态迁移必须由服务端命令控制，禁止通用 CRUD 直接更新：

```text
PENDING → IN_PROGRESS
IN_PROGRESS → APPLIED / UNKNOWN / BLOCKED / FAILED
APPLIED → VERIFIED / UNKNOWN / BLOCKED
UNKNOWN → APPLIED / VERIFIED / IN_PROGRESS / BLOCKED
BLOCKED → IN_PROGRESS / SUPERSEDED
FAILED → IN_PROGRESS / SUPERSEDED
```

`VERIFIED` 只允许在计划变更时生成新版本或把旧步骤标记为 `SUPERSEDED`，不得原地回退。

## 7. `oc_execution_checkpoint`

不可变的进度 journal。

| 字段 | 建议类型 | 约束/说明 |
|---|---|---|
| `execution_id` | `VARCHAR(36)` | NOT NULL |
| `step_id` | `VARCHAR(36)` | 可空；PLAN 等执行级 checkpoint 可无 step |
| `run_id` | `VARCHAR(36)` | NOT NULL |
| `sequence_no` | `BIGINT` | Execution 内单调递增 |
| `checkpoint_type` | `VARCHAR(32)` | `PLAN/CLAIM/PROGRESS/APPLIED/VERIFIED/RECONCILED` |
| `claim_token` | `VARCHAR(64)` | 可空 |
| `workspace_fencing_token` | `BIGINT` | NOT NULL |
| `payload` | `TEXT` | 版本化 JSON |
| `evidence_digest` | `VARCHAR(64)` | payload/外部 artifact 摘要 |
| `git_head` | `VARCHAR(64)` | 可空 |
| `parent_git_head` | `VARCHAR(64)` | 可空 |

必要约束和索引：

```text
UNIQUE (execution_id, sequence_no)
INDEX  (step_id, sequence_no)
INDEX  (run_id, sequence_no)
INDEX  (git_head)
```

journal 只允许 INSERT。step 的 `latest_checkpoint_id` 在同一事务中更新。

## 8. `oc_execution_effect`

平台受控副作用及首次结果快照。

| 字段 | 建议类型 | 约束/说明 |
|---|---|---|
| `effect_key` | `VARCHAR(200)` | NOT NULL |
| `execution_id` | `VARCHAR(36)` | NOT NULL |
| `step_id` | `VARCHAR(36)` | NOT NULL |
| `effect_type` | `VARCHAR(32)` | `COMMENT/EVENT/MEMORY_JOB/PUSH/MR/EXTERNAL_CALL/...` |
| `request_hash` | `VARCHAR(64)` | NOT NULL |
| `status` | `VARCHAR(32)` | `PREPARED/APPLIED/CONFIRMED/UNKNOWN/FAILED` |
| `fencing_token` | `BIGINT` | NOT NULL |
| `request_snapshot` | `TEXT` | 脱敏后的版本化 JSON |
| `result_snapshot` | `TEXT` | 首次结果或查询结果 |
| `external_reference` | `VARCHAR(500)` | job key、branch、MR IID 等可查询引用 |
| `prepared_at` | `TIMESTAMP` | NOT NULL |
| `applied_at` | `TIMESTAMP` | 可空 |
| `confirmed_at` | `TIMESTAMP` | 可空 |
| `last_reconciled_at` | `TIMESTAMP` | 可空 |
| `version` | `BIGINT` | NOT NULL DEFAULT 0 |

必要约束和索引：

```text
UNIQUE (effect_key)
INDEX  (execution_id, status)
INDEX  (step_id, status)
INDEX  (effect_type, external_reference)
```

同一个 `effectKey` 的 `requestHash` 不同时返回冲突，不能覆盖原请求。`UNKNOWN` 的自动处理能力由
effect type 注册的 reconciler 决定。

## 9. `oc_run_observation`

Run 的追加式备注、验证和对账时间线。

| 字段 | 建议类型 | 约束/说明 |
|---|---|---|
| `run_id` | `VARCHAR(36)` | NOT NULL |
| `sequence_no` | `BIGINT` | Run 内单调递增 |
| `observation_type` | `VARCHAR(32)` | `DISPATCH/ACCEPTED/PROGRESS/CHECKPOINT/TERMINAL/RECONCILIATION/MANUAL_REVIEW` |
| `verification_status` | `VARCHAR(32)` | `UNVERIFIED/CONFIRMED/INCONCLUSIVE/CONTRADICTED` |
| `source_type` | `VARCHAR(32)` | `SYSTEM/AGENT/USER/RECONCILER` |
| `source_id` | `VARCHAR(100)` | 可空 |
| `summary` | `VARCHAR(500)` | 列表展示 |
| `detail` | `TEXT` | 可空 |
| `step_id` | `VARCHAR(36)` | 可空 |
| `checkpoint_id` | `VARCHAR(36)` | 可空 |
| `evidence_data` | `TEXT` | 脱敏后的版本化 JSON |
| `supersedes_observation_id` | `VARCHAR(36)` | 可空 |
| `observed_at` | `TIMESTAMP` | NOT NULL，服务端时间 |

必要约束和索引：

```text
UNIQUE (run_id, sequence_no)
INDEX  (run_id, observed_at DESC)
INDEX  (step_id, observed_at DESC)
INDEX  (verification_status, observed_at DESC)
```

observation 只允许 INSERT。人工备注不能直接更新 step/effect；如果人工确认导致状态变化，必须通过
受控 reconcile 命令验证证据，并另外追加状态变化 checkpoint。

## 10. 原子操作

### 10.1 创建或查找 Execution

以 `execution_key` 唯一约束执行 insert-on-conflict/find。冲突后必须校验 `taskType`、业务任务、
loop、plan version 和 `inputHash` 均与已有记录一致。

### 10.2 获取 Requirement 写 lease

语义 CAS：

```sql
UPDATE oc_requirement_workspace
SET owner_run_id = :runId,
    owner_execution_id = :executionId,
    lease_expires_at = :newExpiry,
    fencing_token = fencing_token + 1
WHERE id = :workspaceId
  AND (
    owner_run_id = :runId
    OR owner_run_id IS NULL
    OR lease_expires_at < CURRENT_TIMESTAMP
  );
```

接管新 owner 前还必须确认旧 Agent 进程已停止并取得本机 worktree 文件锁。数据库更新返回的新
`fencing_token` 是后续写操作的必填条件。

### 10.3 Claim step

单条条件更新必须同时校验：

- step 的允许状态或 lease 已过期；
- step 当前 version；
- requirement workspace 当前 owner Run；
- workspace fencing token；
- step 所属 loop/plan version 仍有效。

成功时生成新 `claim_token`、递增 `attempt_count/version` 并追加 CLAIM checkpoint。只更新 step
但 checkpoint 写入失败时整个事务回滚。

### 10.4 写 checkpoint 或 effect

所有写入必须携带 `(runId, claimToken, fencingToken)`，并在事务中重新校验 owner、step 和 workspace。
旧 token 返回明确的 `STALE_OWNER`，不得静默接受。

### 10.5 推进 workspace HEAD

checkpoint commit 完成后：

```text
expected currentHead == checkpoint.parentGitHead
actual worktree HEAD == checkpoint.gitHead
owner/claim/fencing 均有效
```

满足后 CAS 更新 `current_head` 和 Execution `latest_head`。不满足时进入 `UNKNOWN/BLOCKED` 对账，
不能强制覆盖。

### 10.6 增加前端快照版本

任何会改变需求进度视图的事务，在提交前执行：

```text
workspace.snapshotVersion = workspace.snapshotVersion + 1
```

事务提交后发布只包含 Requirement ID 和新版本号的进度事件。事件允许重复或漏失；数据库聚合查询
是权威来源，轮询保证最终收敛。Requirement 级单写使该版本行不会成为跨需求热点。

## 11. 聚合查询模型

`findOverview(requirementId)` 在一个一致性读快照中返回：

- requirement 自动化状态和独立 MR 状态；
- workspace branch/current HEAD、owner、lease、stale、snapshot version；
- active loop 的 Execution 状态；
- required/optional/superseded step 统计和当前步骤；
- 最近可信 checkpoint 和 `nextAction`；
- 最近 Run 及其最新 observation；
- unresolved `UNKNOWN/BLOCKED` step/effect 数量；
- `serverTime/updatedAt/staleAfter`。

列表查询不展开完整 `payload/evidence/resultSnapshot`。journal、observation 和 effect 详情分页按需加载。
绝对路径、prompt、命令环境和回执在 DTO 映射前完成授权与脱敏。

## 12. 保留与清理

- `ACTIVE/UNKNOWN/BLOCKED` Requirement 的 workspace、session、checkpoint 和 artifact 禁止 GC。
- Requirement 自动化终态后默认保留 worktree 和 session 7 天，配置化。
- checkpoint、effect 和 observation 是审计记录，不随 worktree GC 删除。
- 大型日志和 patch artifact 可以迁移到对象存储，数据库保留 URI、digest、大小和保留期。
- 清理任务必须先确认不存在 owner lease、未解决 effect 和开放的恢复流程。

## 13. 迁移兼容

1. 新表先以旁路只写/只读观测方式上线。
2. `oc_run.execution_id` 在迁移期允许为空；新 Run 必填。
3. 历史 Run 可按 `codingTaskId + loopId + input snapshot` 尽力回填 Execution，无法证明的记录保持
   legacy，不伪造 VERIFIED step。
4. 首期保留现有 CodingTask/Requirement 状态写入，并比对新旧聚合结果。
5. claim 协议稳定后，新账本成为权威；旧补偿器改为触发 reconciler。
6. 前端先兼容 overview 缺失并回退现有视图，完成迁移后再移除旧状态推断。

## 14. 数据模型验收

- 所有唯一键在并发 insert 下返回同一业务记录；
- 两个 Run 并发 claim 时只有一个事务成功；
- 旧 claim/fencing token 无法写 checkpoint、effect 或推进 HEAD；
- checkpoint/observation 的 sequence 在并发下不重复且历史不可更新；
- effect key 相同、request hash 不同时稳定返回冲突；
- snapshot version 单调递增，乱序前端事件不能使视图回退；
- 聚合查询不会把 Run `SUCCEEDED` 直接映射为 Execution `SUCCEEDED`；
- MR `OPEN/MERGED/CLOSED` 与自动化 `COMPLETED` 分开返回。
