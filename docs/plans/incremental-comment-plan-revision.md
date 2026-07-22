# 用户评论驱动的增量计划修订实施计划

## 1. 目标

将活跃自动化期间的用户评论从“中断全部 Run 并创建新 loop”改为：

1. 保持当前 `loopId` 不变。
2. 暂停启动新的旧计划任务。
3. 采集现有任务、Run、进度账本和 Git 工作区成果。
4. 由 PM 生成任务级 `PlanPatch`。
5. 保留未受影响任务，只取消和重跑受影响任务。
6. 在同一 loop 内创建更高版本的 `ExecutionPlan` 并恢复调度。

不在本次范围内：

- 不实现任意时刻向所有 CLI Agent 注入新消息。
- 不改变已完成需求收到评论后创建 `CHANGE_REQUEST` loop 的语义。
- 不删除旧计划、旧任务、旧 Run 或其审计记录。
- 不引入租户隔离字段。

## 2. 成功标准

- `PLANNING / DEVELOPING / VALIDATING / ACCEPTING` 状态提交评论后，`loopId` 不变。
- 新计划 `version` 单调增加，且记录基础计划和触发评论。
- `KEEP` 的 `RUNNING` Run 不被取消，`SUCCEEDED` 任务不被重做。
- `AMEND` 任务取消前保存工作区 diff 和进度，新 Run 在同一需求工作区恢复。
- 多条连续评论只允许最新 `revisionSeq` 的 PM 结果生效。
- 修订失败不丢工作区成果、不继续调度旧下游任务，并可幂等重试。
- `COMPLETED` 评论仍创建新的 `CHANGE_REQUEST` loop。

## 3. 核心设计

### 3.1 loop 与 plan revision 的边界

`loopId` 表示一次业务交付周期；`ExecutionPlan.version` 和 `revisionSeq` 表示该周期内的计划修订。只有新的交付周期、不可恢复的工作区或明确回退策略才创建新 loop。

### 3.2 修订状态机

```text
NONE
  └─ 用户评论 → PENDING
                  └─ 暂停新调度 → SNAPSHOTTING
                                      └─ 快照完成 → PLANNING
                                                        └─ Patch 合法 → APPLYING
                                                                          └─ 原子应用 → NONE
                                                        └─ 失败 → FAILED

FAILED ── retry → PENDING
任意阶段收到新评论 → revisionSeq + 1；旧事件和旧 PM 结果变为过期
```

`Requirement.automationStatus` 保留现有业务阶段，修订使用独立的 `revisionState`，避免丢失修订前究竟处于开发、验证还是验收阶段。

### 3.3 评论状态策略

| 当前状态 | 新行为 |
|---|---|
| `PLANNING` | 同 loop 增加 `revisionSeq`；旧 PM 结果按 token 失效；基于完整评论重新规划 |
| `DEVELOPING` | 暂停新任务，快照，生成并应用 PlanPatch |
| `VALIDATING` | 当前验证结果只作为观察；新计划按影响决定 `KEEP` 或 `REVALIDATE` |
| `ACCEPTING` | 旧验收结果按 revision token 失效；同 loop 修订 |
| `FAILED` | 工作区和基础计划可恢复时同 loop 修订；否则显式回退到新 loop |
| `COMPLETED` | 保持现状，创建新的 `CHANGE_REQUEST` loop |
| 其他非活跃状态 | 仅保存评论；是否启动修订由现有业务规则决定 |

### 3.4 PlanPatch 内部契约

```json
{
  "requirementId": "req-id",
  "loopId": "loop-id",
  "revisionSeq": 4,
  "basePlanId": "plan-v3-id",
  "basePlanVersion": 3,
  "summary": "根据用户评论调整表单交互和验收范围",
  "operations": [
    {
      "taskKey": "backend-api",
      "action": "KEEP",
      "sourceTaskId": "task-1",
      "reason": "评论不影响接口契约"
    },
    {
      "taskKey": "frontend-form-v2",
      "action": "AMEND",
      "sourceTaskId": "task-2",
      "title": "调整表单交互",
      "description": "复用现有实现并增加用户要求的交互",
      "area": "frontend",
      "fileScope": ["frontend/src/pages/..."],
      "dependsOn": ["backend-api"],
      "assignedAgent": "frontend-dev-agent",
      "reason": "最新评论改变了交互要求"
    }
  ]
}
```

动作语义：

- `KEEP`：复用任务实体、状态、Run 和依赖结果。
- `AMEND`：旧任务标记 `SUPERSEDED`，创建关联 `supersedesTaskId` 的新任务。
- `ADD`：创建新任务。
- `SUPERSEDE`：停止旧任务后续调度，但保留审计数据和工作区成果。
- `REVALIDATE`：创建独立验收任务，不重做已正确完成的编码任务。

服务器必须拒绝重复任务键、未知来源任务、依赖环、非法 Agent、非法 area、空文件范围以及跨前后端目录边界。

### 3.5 运行中任务处理

1. 评论提交后只暂停新调度，不立即取消 Run。
2. 快照服务采集最新 Run、`TaskExecution`、进度账本、HEAD、base commit、changed files 和 diff。
3. PM 对每个任务给出动作。
4. `KEEP` Run 继续执行。
5. `AMEND / SUPERSEDE` Run 才设置 `cancelRequested` 并调用 CLI cancel。
6. 取消完成后再补采一次快照，保存 CLI 已返回摘要。
7. `AMEND` 新 Run 关联旧 Run 和 handoff snapshot，在同一需求工作区检查、保留并继续现有修改。

第一版以工作区和进度账本作为可靠成果来源。后续如 CLI Runner 支持双向控制，再增加“请收口并报告”的优雅 checkpoint，不作为第一版上线阻塞项。

## 4. 数据模型与迁移

新增 `V10__add_incremental_plan_revision.sql`，只做向后兼容的加列、建表和索引，不修改现有 loop 数据。

### Requirement

- `revision_seq BIGINT NOT NULL DEFAULT 0`
- `applied_revision_seq BIGINT NOT NULL DEFAULT 0`
- `revision_state VARCHAR(32) NOT NULL DEFAULT 'NONE'`
- `revision_trigger_comment_id VARCHAR(36)`
- `revision_failure_reason TEXT`

### ExecutionPlan

- `base_plan_id VARCHAR(36)`
- `trigger_comment_id VARCHAR(36)`
- `revision_seq BIGINT NOT NULL DEFAULT 0`
- `change_set_json TEXT`
- 建议唯一索引：`requirement_id, loop_id, revision_seq`

### CodingTask

- `revision_seq BIGINT NOT NULL DEFAULT 0`
- `supersedes_task_id VARCHAR(36)`
- `disposition_reason TEXT`
- `CodingTaskStatus` 增加 `SUPERSEDED`

### TaskHandoffSnapshot

新增 `oc_task_handoff_snapshot`，保存：

- requirement/task/run/comment/revision 关联。
- head/base commit。
- changed files、diff stat、截断后的 diff summary。
- 进度快照和 Run summary。
- 唯一约束建议为 `coding_task_id, revision_seq`。

所有快照字段设置明确大小上限，不写入环境变量、凭证和完整原始日志。

## 5. 对外 API

### 提交评论

保持现有接口兼容：

```http
POST /requirement/{id}/comments
Content-Type: application/json

{
  "content": "调整筛选交互，但保留已经完成的后端实现",
  "metadataJson": null
}
```

接口仍返回 `RequirementCommentDto`。客户端刷新 Requirement 后读取新增字段：

- `revisionSeq`
- `appliedRevisionSeq`
- `revisionState`
- `revisionFailureReason`

### 重试失败修订

```http
POST /requirement/{id}/revision/retry
```

约束：

- 仅 `revisionState=FAILED` 时允许。
- retry 必须沿用当前 `revisionSeq`。
- 如果失败后已经收到新评论，旧 revision 不允许重放。
- 重复请求幂等。

## 6. 任务板

### task-1：领域契约与数据库迁移

- Agent：`backend-agent`
- 必须使用：`eadp-backend`
- 优先级：P1
- 依赖：无
- 复杂度：High
- 范围：API DTO/enums、entities、DAOs、Flyway migration
- 交付：V10、修订枚举、实体字段、DTO、DAO、映射测试
- 验收：旧数据默认值正确；迁移可升级；新增索引可用；现有评论 API 不破坏。

### task-2：运行现场快照与成果交接

- Agent：`backend-agent`
- 必须使用：`eadp-backend`
- 优先级：P2
- 依赖：task-1
- 复杂度：High
- 范围：`service/revision` 及对应测试
- 交付：快照采集、持久化、PM/恢复摘要组装
- 验收：运行中任务包含未提交和未跟踪文件；重复执行幂等；快照失败不改变工作区。

### task-3：PM PlanPatch 生成与校验

- Agent：`backend-agent`
- 必须使用：`eadp-backend`
- 优先级：P2，可与 task-2 并行
- 依赖：task-1
- 复杂度：High
- 范围：`PmAgentClient`、revision contract/validator 及测试
- 交付：prompt、parser、validator
- 验收：完整评论、基础计划和成果摘要进入 PM；非法动作、重复键和 DAG 环被拒绝。

### task-4：PlanPatch 原子应用与有效任务图

- Agent：`backend-agent`
- 必须使用：`eadp-backend`
- 优先级：P3
- 依赖：task-1、task-3
- 复杂度：Very High
- 交付：新版计划创建、任务复用/替代、CAS、防并发覆盖
- 验收：同 loop 计划版本单调递增；KEEP 不改 ID；事务失败完全回滚。

### task-5：版本感知调度器

- Agent：`backend-agent`
- 必须使用：`eadp-backend`
- 优先级：P4
- 依赖：task-4
- 复杂度：Very High
- 交付：effective task graph resolver、调度器改造、回归测试
- 验收：同 loop 重复 taskKey 不冲突；SUPERSEDED 不执行；旧 loop 仍 STALE；lane/fileScope 规则不回归。

### task-6：评论修订编排与并发收敛

- Agent：`backend-agent`
- 必须使用：`eadp-backend`
- 优先级：P5
- 依赖：task-2、task-4、task-5
- 复杂度：Very High
- 交付：revision service、领域事件、监听器、状态恢复
- 验收：活跃状态不建新 loop；连续评论只应用最新 token；PM 失败保留现场并进入 FAILED。

### task-7：选择性取消与恢复执行

- Agent：`backend-agent`
- 必须使用：`eadp-backend`
- 优先级：P6
- 依赖：task-2、task-5、task-6
- 复杂度：Very High
- 交付：受影响 Run 取消、取消摘要保存、handoff-aware prompt、同工作区恢复
- 验收：KEEP Run 不取消；AMEND Run 有 parent/handoff；已有正确差异不会被无条件重做。

### task-8：查询、重试、推送、观测和运行开关

- Agent：`backend-agent`
- 必须使用：`eadp-backend`
- 优先级：P7
- 依赖：task-6、task-7
- 复杂度：Medium
- 交付：DTO/事件字段、retry API、feature flag、日志和指标
- 验收：过期 revision 不可重试；日志具备完整关联键；开关可安全回退旧行为。

### acceptance-1：后端独立验收

- Agent：`test-agent`
- 优先级：P8
- 依赖：task-1 至 task-8
- 只执行验收并出报告，不隐式修改生产代码。
- 若失败，PM 根据报告新增对应修复任务；修复后重新执行 acceptance-1。

阻断性场景：

- 活跃评论 `loopId` 不变。
- KEEP Run 不取消。
- AMEND Run 的 diff/进度进入新 Run。
- 连续评论、事件重放、PM 超时、事务回滚、服务重启均保持一致。
- `COMPLETED` 新 loop 和旧 loop STALE 逻辑无回归。

### task-9：前端修订体验

- Agent：`frontend-agent`
- 必须使用：`suid`
- 优先级：P9
- 依赖：task-8、acceptance-1
- 复杂度：Medium
- 交付：评论提示、修订状态、失败原因与重试、前端测试
- 验收：活跃评论不再提示“中断全部自动化”；COMPLETED 仍提示新变更 loop；慢请求和重连不重复提交。

### task-10：运行手册和回滚说明

- Agent：`docs-agent`
- 优先级：P9，可与 task-9 并行
- 依赖：task-8
- 复杂度：Low
- 交付位置：`docs/`
- 验收：状态、配置、排障、迁移和回滚内容与最终代码一致。

### acceptance-2：端到端独立验收

- Agent：`test-agent`
- 优先级：P10
- 依赖：task-9、task-10
- 从 UI 提交评论，验证计划修订、任务复用、受影响任务恢复、推送和最终验收。
- 若失败，PM 新增前端、后端、测试或环境修复任务，修复完成后重新执行 acceptance-2。

## 7. 执行依赖

```text
task-1
  ├─ task-2 ───────────────┐
  └─ task-3 → task-4 → task-5
                            ├→ task-6 → task-7 → task-8 → acceptance-1 → task-9 ─┐
                            │                              └→ task-10 ───────────┤
                            └────────────────────────────────────────────────────→ acceptance-2
```

task-2 和 task-3 可并行；task-9 和 task-10 可并行。其余阶段涉及状态机和调度一致性，应按依赖顺序推进。

## 8. 必测场景

### 正常路径

1. `DEVELOPING` 中，一个前端任务运行、后端任务成功、验收任务待执行。
2. 用户评论只修改前端要求。
3. 后端成功任务 `KEEP`，运行前端任务 `AMEND`，验收任务 `REVALIDATE`。
4. `loopId` 不变，plan version 增加。
5. 前端旧 Run 保存 diff 后取消，新 Run 在原工作区恢复。
6. 新验收任务成功后进入后续交付阶段。

### 并发路径

- 快照期间连续提交两条评论。
- PM v4 结果晚于 PM v5 返回。
- 只能应用 v5；v4 记录为过期，不能修改任务或恢复调度。

### 故障路径

- PM 返回非法 JSON。
- PlanPatch 存在依赖环。
- Git diff 采集失败。
- CLI cancel 失败或进程已经退出。
- 原子应用中途数据库异常。
- 应用成功后、调度事件发布前服务重启。

所有故障都必须满足：不删除成果、不误调旧任务、能够观测和恢复。

### 回归路径

- `COMPLETED` 评论仍创建新 loop。
- 手工 stop/resume 行为不变。
- 旧 loop 任务仍被标记为 `STALE`。
- lane、fileScope、依赖失败和 test-agent 验收规则保持有效。

## 9. 上线与回滚

1. 部署 V10 和只读 DTO 字段，增量修订开关关闭。
2. 部署后端逻辑，在测试环境开启开关并执行 acceptance-1。
3. 部署前端并执行 acceptance-2。
4. 生产灰度开启，观察：修订成功率、过期 PM 结果数、取消 Run 数、修订停留时间和旧任务误调度数。
5. 稳定一个发布周期后默认开启；旧行为开关再保留一个发布周期。

运行时回滚优先关闭 feature flag，不回滚数据库列。V10 为向后兼容结构，旧代码应忽略新增字段。只有确认不存在新版本代码写入后，才考虑后续版本清理结构。

## 10. 风险与控制

| 风险 | 级别 | 控制措施 |
|---|---|---|
| 同 loop 多版本 taskKey 选择错误 | 高 | effective graph resolver；唯一有效修订；调度器并发测试 |
| 旧 PM 结果覆盖新评论 | 高 | revisionSeq 前后双重校验；CAS 应用 |
| 取消 Run 丢失成果 | 高 | 取消前后快照；工作区不删除；保存取消摘要 |
| PM Patch 直接生成越界任务 | 高 | 服务端白名单、目录边界和 DAG 校验 |
| 修订失败后旧任务继续扩散 | 高 | revision 非 NONE 时调度硬门禁 |
| 大 diff 导致 DB/Prompt 膨胀 | 中 | 文件数、字节数截断；保存截断标志 |
| feature flag 双路径长期维护 | 中 | 明确移除日期；稳定一个发布周期后删除旧路径 |

## 11. 实施约束

- 后端任务必须使用 `eadp-backend` skill，并遵循 sei-core 分层。
- 前端任务必须使用 `suid` skill，优先采用 `@ead/suid`。
- 本功能涉及超过三个生产文件，执行阶段必须使用项目规定的 superpowers 做任务拆解和执行。
- 前端不得引用后端内部实现，后端不得引用前端代码。
- 不增加 `tenant_code`、`ITenant` 或租户上下文。
- 验收只由计划中的 `test-agent` 任务触发；coding task 完成后不得隐式触发。

