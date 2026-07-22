# 评论驱动的增量计划修订

本文面向开发、测试和运维人员，说明用户在自动化执行期间提交评论后，系统如何在同一个 `loopId` 内修订执行计划。实现入口位于 `RequirementAutomationService`，核心编排位于 `service/revision`，数据库结构由 `V10__add_incremental_plan_revision.sql` 提供。

## 1. 行为边界

- `PLANNING`、`DEVELOPING`、`VALIDATING`、`ACCEPTING` 中的需求收到评论后，保留当前 `activeLoopId`，递增 `revisionSeq` 并异步修订计划。
- `FAILED` 且当前 loop 仍有可解析的有效任务图时，清理需求失败信息后在原 loop 修订；没有可复用计划或工作区时，兼容回退为新的 `CHANGE_REQUEST` loop。
- `COMPLETED` 收到评论后仍创建新的 `CHANGE_REQUEST` loop，不复用已完成交付周期。
- 其他非活跃状态只保存评论，不主动修订。
- 修订期间只暂停启动新任务。正在运行的任务先保留，待 `PlanPatch` 判定后再选择性处理。

`loopId` 表示业务交付周期；同一周期内的变化由 `Requirement.revisionSeq`、`ExecutionPlan.revisionSeq` 和递增的 `ExecutionPlan.version` 表示。历史计划、任务和 Run 不删除，用于审计与恢复。

## 2. 状态机与执行顺序

```text
NONE
  └─ 提交评论 → PENDING
                  └─ 开始采集 → SNAPSHOTTING
                                  └─ 快照完成 → PLANNING
                                                  └─ 校验通过 → APPLYING
                                                                  └─ 原子应用 → NONE
                                                  └─ 异常/无有效 Patch → FAILED

FAILED ── POST revision/retry → PENDING（沿用当前 revisionSeq）
任意处理中状态收到新评论 → revisionSeq + 1，旧 token 的事件和 PM 结果不再生效
```

`Requirement.automationStatus` 继续表示业务阶段，`Requirement.revisionState` 只表示计划修订阶段。`CodingTaskScheduler` 在 `revisionState != NONE` 时不启动新的任务，包括 `FAILED`；已启动 Run 的回调仍可写回，但调度器只解析最新 `EffectiveTaskGraph`。

评论事务提交后发布 `PlanRevisionRequestedEvent`，`PlanRevisionEventListener` 异步调用 `PlanRevisionOrchestrationService`。编排过程执行以下步骤：

1. 通过 `PlanRevisionStateService` 的 CAS 将 `PENDING` 转为 `SNAPSHOTTING`；过期 token 直接忽略。
2. 解析当前 loop 最新计划和有效任务图，为每个有效任务调用 `TaskHandoffSnapshotService.capture`，同时装配完整评论历史。
3. 将状态转为 `PLANNING`，在数据库长事务之外调用 `PmAgentClient.generatePlanPatch`。
4. PM 返回后再次校验 `requirementId + loopId + revisionSeq + revisionState`，防止旧结果覆盖最新评论。
5. 将状态转为 `APPLYING`，由 `PlanPatchApplicationService.apply` 在单个事务内创建新版计划、变更任务并 CAS 更新 `appliedRevisionSeq`。
6. 发布 `PlanRevisionAppliedEvent`，选择性收口被替代的运行中任务，然后恢复同一 loop 的调度。

## 3. PlanPatch 契约

`PlanPatch` 包含 `requirementId`、`loopId`、`revisionSeq`、`basePlanId`、`basePlanVersion`、`summary` 和 `operations`。每个 `PlanPatchOperation` 必须有唯一 `taskKey`、明确 `action` 和非空 `reason`。

| 动作 | 实际语义 |
|---|---|
| `KEEP` | 复用源任务 ID、状态、依赖结果和已有 Run；不得重新定义任务字段。 |
| `AMEND` | 将源任务退出有效图，创建 `supersedesTaskId` 指向源任务的新 `PENDING` 任务；新任务复用源 Run 与交接快照作为恢复上下文。 |
| `ADD` | 创建没有 `supersedesTaskId` 的新 `PENDING` 任务。 |
| `SUPERSEDE` | 从有效图移除源任务，不创建替代任务；非运行任务立即标为 `SUPERSEDED`。 |
| `REVALIDATE` | 创建指向源任务的独立验证任务，必须分配给 `test-agent`。 |

服务端的 `PlanPatchValidator` 是 PM 输出的信任边界。当前允许的 `area` 为 `frontend`、`backend`、`full-stack`、`validation`，允许的 Agent 为 `frontend-dev-agent`、`backend-dev-agent`、`test-agent`。校验还会拒绝：

- Patch 与输入的 requirement、loop、revision 或基础计划不一致；
- 重复任务键、未知源任务、同一源任务被多次处置；
- 空执行字段、不兼容的 area/Agent、绝对路径、反斜线或 `..` 路径；
- 前端任务越界到 `backend/`，或后端任务越界到 `frontend/`；
- 依赖未指向 Patch 输出任务、自依赖或 DAG 环。

新版 `ExecutionPlan` 保持原 `loopId`，`version = basePlan.version + 1`，并记录 `basePlanId`、`triggerCommentId`、`revisionSeq` 和完整 `changeSetJson`。`revisionSeq = 0` 表示增量修订功能上线前的历史数据。

## 4. 选择性取消与成果交接

评论提交本身不会取消任何 Run。首次快照会保存任务最新 Run、Git HEAD/base commit、变更文件、diff stat、截断后的 diff 摘要、进度账本和 Run 摘要。快照按 `(coding_task_id, revision_seq)` 唯一，重复采集返回已有记录；刷新使用 `TaskHandoffSnapshotService.refresh` 更新同一条记录。

Patch 应用后的处理规则如下：

- `KEEP` 任务不进入 `taskIdsToSettle`，运行中的 Run 继续执行。
- `AMEND` 或 `SUPERSEDE` 的源任务若为 `RUNNING`/`VALIDATING`，由 `PlanRevisionSettlementService` 刷新快照、调用 `CodingTaskExecutionService.cancelRun`，再把源任务标为 `SUPERSEDED`。
- `cancelRun` 先持久化 `cancelRequested=true`，再尽力终止外部 Agent 进程。晚到的取消回调保存 Run 摘要和失败原因，但不会把已 `SUPERSEDED` 的任务覆盖为 `CANCELLED`。
- 修订任务执行时，如果自身没有历史 Run，会以 `supersedesTaskId` 对应的最新 Run 作为 parent/补偿来源；提示词包含完整评论历史、当前工作区差异和源任务的 handoff 摘要。
- 所有任务继续使用需求级工作区，因此已提交、未提交和未跟踪成果不会因为创建新版计划而丢失。

快照只保存截断后的摘要，不保存环境变量、凭证和完整原始日志。排查问题时仍应按敏感信息规范处理 `diff_summary` 和 `progress_snapshot_json`。

## 5. API 与配置

### 5.1 提交评论

```http
POST /requirement/{id}/comments
Content-Type: application/json

{
  "content": "保留已完成的后端接口，只调整前端筛选交互",
  "metadataJson": null
}
```

返回值仍为 `RequirementCommentDto`。客户端随后查询 Requirement 或执行进度 overview 获取权威修订状态。

### 5.2 重试失败修订

```http
POST /requirement/{id}/revision/retry
```

接口返回 `RequirementDto`。只允许当前 `activeLoopId` 的最新修订处于 `FAILED` 时重试；重试沿用当前 `revisionSeq`，通过 CAS 将状态改回 `PENDING` 并重新发布 `PlanRevisionRequestedEvent`。重复请求不会创建新 revision：第一次成功后状态已不再是 `FAILED`，后续请求返回失败。如果失败后用户又提交了新评论，新评论产生的新 `revisionSeq` 自动使旧 token 失效。

### 5.3 Feature flag

配置项：

```yaml
onlinecode:
  automation:
    incremental-comment-revision:
      enabled: true
```

对应完整属性名为 `onlinecode.automation.incremental-comment-revision.enabled`，默认值为 `true`。设为 `false` 时，评论处理回退到原有“中断当前活跃 loop 并创建新 `CHANGE_REQUEST` loop”的兼容路径。该开关只影响后续评论，不会撤销已发布的异步修订事件，也不会删除已创建的计划版本。

## 6. 并发、幂等与一致性

- 每条触发修订的评论通过数据库更新原子递增 `revisionSeq`；状态迁移均带 `activeLoopId + revisionSeq + expectedState` 条件。
- PM 调用不占用数据库事务。调用前后的 token 校验保证耗时较长的旧 PM 结果只被记录为过期，不修改任务。
- Patch 应用同时校验基础计划 ID/version，并用 `appliedRevisionSeq < revisionSeq` 抢占 CAS；计划、任务和 Requirement 更新在同一事务内回滚。
- 数据库只允许同一 requirement/loop 的正数 `revisionSeq` 对应一个 ExecutionPlan；历史 `revisionSeq=0` 允许多条旧计划。
- handoff 唯一索引和 `SUPERSEDED` 状态检查使快照、settlement 重放保持幂等。
- 调度器按需求加进程内锁，并从最新计划的有效任务图解析唯一 taskKey；旧 loop 任务仍按既有规则标为 `STALE`。

当前修订事件是 Spring 进程内异步事件，不是持久化消息或 outbox。服务在事件发布后、处理完成前异常退出，可能留下 `PENDING`、`SNAPSHOTTING`、`PLANNING` 或 `APPLYING`。发布、重启和回滚前必须确认没有处理中修订；当前公开 retry API 只接受 `FAILED`，不能自动恢复这些中间状态。

## 7. 监控与排障

权威监控字段：

| 来源 | 字段 | 含义 |
|---|---|---|
| `RequirementDto` | `revisionSeq`、`appliedRevisionSeq`、`revisionState`、`revisionTriggerCommentId`、`revisionFailureReason` | 当前请求、已应用版本、状态和错误。 |
| `RequirementExecutionOverviewDto` | 上述序号/状态/错误、`planVersion`、`activeLoopId`、`snapshotVersion` | 前端和巡检使用的聚合快照。 |
| `RequirementProgressEvent` | `requirementId`、`loopId`、`revisionSeq`、`revisionState`、`revisionFailureReason`、`snapshotVersion` | 修订字段用于即时展示，同时触发 overview 刷新；执行进度事件才保证携带 `snapshotVersion`。 |
| `oc_execution_plan` | `version`、`revision_seq`、`base_plan_id`、`trigger_comment_id`、`change_set_json` | 计划版本链和 PM 决策审计。 |
| `oc_task_handoff_snapshot` | task/run/revision 关联及 Git/进度摘要 | 被替代任务的恢复证据。 |

建议告警：

- `revision_state` 在非 `NONE` 状态停留超过一次正常 PM 执行时长；
- `revision_state=FAILED` 或 `revision_failure_reason` 非空；
- `revision_seq > applied_revision_seq` 长时间不收敛；
- 同一需求短时间连续递增大量 revision；
- settlement 后源任务仍有 `RUNNING` Run 或新版计划长期没有可调度任务。

后端已有修订日志均带 `requirementId` 和 `revisionSeq`，编排入口还带 `loopId`；计划版本和触发评论可通过新计划行关联。常见排查顺序：

1. 查询 Requirement 的 active loop、revision 序号、状态和失败原因。
2. 按 requirement/loop 查询最高 `ExecutionPlan.version`，核对 `base_plan_id` 和 `change_set_json`。
3. 用有效图中的 taskKey 核对 KEEP/新建/被替代任务，查看 `supersedes_task_id` 和 `disposition_reason`。
4. 对被替代运行任务检查 `cancel_requested`、Run 终态及 handoff 快照。
5. 若状态为 `FAILED`，先修复 PM 输出、配置或工作区问题，再调用 retry；不要直接创建新 loop 掩盖现场。

## 8. 发布步骤

1. 备份数据库并确认 Flyway 当前版本正常，记录发布前仍在执行的需求。
2. 在生产环境先显式设置 `onlinecode.automation.incremental-comment-revision.enabled=false`。
3. 发布包含 V10 的后端；V10 仅加列、索引和 `oc_task_handoff_snapshot`，旧数据默认 `revision_seq=0`、`applied_revision_seq=0`、`revision_state=NONE`。
4. 完成冒烟测试：旧数据查询、评论接口兼容、完成需求创建 CHANGE_REQUEST loop、FAILED retry 的权限与状态约束。
5. 小范围开启 feature flag，验证同一 loop 的 `version` 递增、KEEP 不取消、AMEND 产生 handoff 和替代任务。
6. 观察状态停留时间、失败率、PM 结果过期率和取消收口情况，再逐步全量开启。

发布或重启前，应等待所有 Requirement 进入 `NONE` 或 `FAILED`。不要在 `SNAPSHOTTING`、`PLANNING`、`APPLYING` 时滚动重启所有实例。

## 9. 回滚步骤

1. 将 `onlinecode.automation.incremental-comment-revision.enabled` 设为 `false`，阻止后续评论进入增量路径。
2. 等待已开始的修订收敛到 `NONE` 或 `FAILED`；开关不会取消在途事件。
3. 如需回滚应用版本，先确认没有处于中间状态的 revision，再回滚后端。V10 是向后兼容迁移，保留新增列、表和历史数据，不执行破坏性 down migration。
4. 回滚后验证评论走兼容的新 loop 路径、旧 loop Run 已停止或可审计、历史 Requirement/ExecutionPlan 仍可读取。
5. 若实例故障导致 revision 卡在中间状态，先备份并保全 Requirement、ExecutionPlan、CodingTask、Run 和 handoff 数据；当前没有公开的“强制重置中间状态”接口，必须按事故流程评估后再做数据库修复，不能直接删除计划或 Run。

重新开启功能前，应先消除所有卡住的 revision，确认最新 `revisionSeq` 与 `appliedRevisionSeq` 的关系符合预期，并执行一次包含 KEEP、AMEND 和连续评论的回归验收。
