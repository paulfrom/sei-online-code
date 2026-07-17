# Run / Execution 进度账本实施任务板

状态：APPROVED / READY FOR HANDOFF

接手入口：
[`run-execution-reliability-handoff.md`](./run-execution-reliability-handoff.md)

依据：

- [ADR-001](../architecture/adr-001-run-execution-progress-ledger.md)
- [数据模型](../architecture/run-execution-progress-data-model.md)
- [完整评审记录](../architecture/run-execution-reliability-design-draft.md)
- [详细验收方案](../acceptance/run-execution-reliability-acceptance-plan.md)

## 1. 交付目标

在允许 Run 重复调用的前提下，使后续 Run 能读取同一 Execution 的可信进度、跳过 `VERIFIED` 步骤、
对账 `UNKNOWN` 效果并安全接管未完成步骤。同时在现有 RequirementWorkspace 中提供结构化进度、
Run observation、证据和 MR 状态查看。

完成边界为：

- 三类失败均可安全续作；
- 同一需求始终只有一个 worktree、一个 feature branch 和一个写 owner；
- 平台只 commit、push、创建或更新 MR，不自动合并；
- Requirement 自动化 `COMPLETED` 只表示“MR 已提交”。

## 2. 实施约束

### Worktree 与提交

- 本计划的所有任务属于同一个 Requirement，必须共享一个 requirement worktree 和 feature branch。
- 不允许按任务、Execution 或 Run 创建额外 worktree。
- 每个实施任务完成后形成 checkpoint commit；后续任务从同一分支最新已确认 HEAD 继续。
- 任务失败时保留未提交变更并先对账，不得 reset 或覆盖。
- 全部验收通过后只创建或更新一个 MR，不自动合并。

### Skill 门禁

- 后端任务启动前必须启用并读取项目要求的 `eadp-backend` skill。
- 前端任务启动前必须启用并读取项目要求的 `suid` skill。
- 当前会话没有暴露这两个 skill；未启用前，对应编码任务状态必须保持 `BLOCKED`。

### API 与类型

- HTTP DTO 使用 camelCase，时间使用 ISO 8601。
- 权威状态由后端进度账本聚合，前端不得从摘要、退出码、日志或评论推断。
- 通用 CRUD 不得直接修改 step、effect、workspace owner 或 fencing token。
- 证据详情由后端授权、脱敏和分页。

## 3. API 基线

### 需求进度聚合

```http
GET /executionProgress/findOverview?requirementId={requirementId}
```

成功响应的 `data` 至少包含：

```json
{
  "requirementId": "string",
  "automationStatus": "DEVELOPING",
  "mrStatus": "OPEN",
  "activeLoopId": "string",
  "planVersion": 1,
  "snapshotVersion": 12,
  "workspace": {
    "branch": "feature/REQ-001",
    "currentHead": "sha",
    "ownerRunId": "string",
    "ownerExecutionId": "string",
    "leaseExpiresAt": "2026-07-17T12:00:00Z",
    "stale": false
  },
  "stepSummary": {
    "required": 8,
    "verified": 3,
    "applied": 1,
    "unknown": 0,
    "blocked": 0
  },
  "currentStep": {},
  "latestCheckpoint": {},
  "nextAction": "verify:module-compile",
  "recentRuns": [],
  "serverTime": "2026-07-17T11:59:30Z",
  "updatedAt": "2026-07-17T11:59:28Z",
  "staleAfter": "2026-07-17T12:00:30Z"
}
```

### 分页详情

```http
GET /executionProgress/findSteps?executionId={id}
GET /executionProgress/findCheckpoints?stepId={id}&page=1&rows=20
GET /executionProgress/findEffects?executionId={id}&status=UNKNOWN&page=1&rows=20
GET /runObservation/findByRun?runId={id}&page=1&rows=20
POST /runObservation/appendManual
```

人工 observation 请求：

```json
{
  "runId": "string",
  "summary": "已核对远端分支未产生变更",
  "detail": "string",
  "verificationStatus": "CONFIRMED",
  "evidence": {}
}
```

该接口只能追加 observation，不能直接修改 step/effect 状态。状态变化必须调用受控 reconcile 命令。

### 实时事件

```text
WebSocket /ws/requirement/{requirementId}/progress
```

```json
{
  "eventType": "step.updated",
  "requirementId": "string",
  "entityId": "string",
  "snapshotVersion": 12,
  "occurredAt": "2026-07-17T11:59:28Z"
}
```

WebSocket 只通知刷新。客户端收到更高版本后重新读取 overview；断线时降级为活动态 5 秒、非活动态
30 秒轮询。

## 4. 任务依赖图

```text
EXE-001 数据层
   └── EXE-002 核心进度协议
         ├── EXE-003 查询与事件 ───────────────┐
         ├── EXE-004 Runner preflight/checkpoint ─┐
         │      └── EXE-005 Worktree/Git 接管 ───┤
         │              └── EXE-006 Effect/MR ───┤
         │                       └── EXE-007 补偿对账
         └── EXE-008 前端（依赖 EXE-003 契约） ──┤
                                                  └── EXE-009 兼容切换
                                                       ├── ACC-001 并发验收
                                                       ├── ACC-002 故障恢复验收
                                                       └── ACC-003 前端可观测验收
                                                              └── DEL-001 提交 MR
```

EXE-003 与 EXE-004 可在 EXE-002 后并行。EXE-008 可以基于 EXE-003 的冻结 DTO 契约开发，不必等待
Runner、effect 和补偿器完成。

## 5. 实施任务

### EXE-001 数据库、实体和 DAO 基线

- Agent：`backend-agent`
- 必需 skill：`eadp-backend`
- 优先级：P0
- 复杂度：High
- 依赖：无
- Scope：
  - `docs/db/`
  - `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/dao/`
  - 对应 DAO/entity 测试

工作内容：

- 为 workspace、Execution、step、checkpoint、effect、Run observation 建立 PostgreSQL 迁移。
- 扩展 `oc_run` 的 Execution、invocation、thread/turn、heartbeat、恢复点和验证字段。
- 实现实体、枚举和 DAO；数据库唯一约束与 JPA 映射一致。
- 迁移保持历史 Run 可空兼容，不伪造历史 VERIFIED step。

验收条件：

- Hibernate `ddl-auto=validate` 对新 schema 通过。
- `executionKey`、requirement workspace、step key、effect key、sequence 唯一约束存在。
- 新 RunState 支持 `QUEUED/UNKNOWN`，原状态数据可读取。
- DAO 并发 insert 冲突能回读唯一业务记录。
- migration 有前向验证和明确回滚说明。

### EXE-002 ProgressService 核心原子协议

- Agent：`backend-agent`
- 必需 skill：`eadp-backend`
- 优先级：P0
- 复杂度：Very High
- 依赖：EXE-001
- Scope：
  - `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/progress/`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/progress/`
  - 对应 service 测试

工作内容：

- 实现 Execution find-or-create、Requirement lease、step declare/claim/heartbeat。
- 实现 checkpoint、markApplied、markVerified、observation 和 reconcile 命令。
- 所有写命令校验 runId、claimToken、fencingToken、loop 和 plan version。
- 每次可观测提交递增 requirement `snapshotVersion`。
- 生成 `ExecutionProgressSnapshot` 和 `nextActions`。

验收条件：

- 两个 Run 并发 claim 只有一个成功。
- 旧 claim/fencing token 返回稳定 `STALE_OWNER`，不产生部分写入。
- 关键 checkpoint 写入失败时 step 状态与 snapshot version 一并回滚。
- `VERIFIED` 不能由普通更新回退。
- `APPLIED/UNKNOWN` 的处理遵循 ADR，不会重新执行实现动作。
- 服务单测覆盖状态矩阵、CAS 冲突和事务回滚。

### EXE-003 聚合查询、人工 Observation 与进度事件

- Agent：`backend-agent`
- 必需 skill：`eadp-backend`
- 优先级：P1
- 复杂度：High
- 依赖：EXE-002
- Scope：
  - `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/api/`
  - `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/progress/`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/controller/`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/ws/`
  - 查询/Controller/WS 测试

工作内容：

- 冻结第 3 节 HTTP/WS DTO 契约。
- 实现 overview 聚合以及 step/checkpoint/effect/observation 分页查询。
- 实现受权限控制的人工 observation 追加。
- after-commit 发布 requirement progress event；事件不承担权威状态。
- 在 DTO 映射前执行证据授权、路径与敏感字段脱敏。

验收条件：

- overview 在一致性读中返回服务端推导状态和单调 snapshot version。
- 查询数量受控，不随 observation/checkpoint 总量线性增长。
- WebSocket 事件只在事务提交后发送，包含正确版本号。
- 未授权用户不能读取敏感证据或追加人工 observation。
- 未知枚举保持原值，不被映射成成功。

### EXE-004 Runner preflight、Run 绑定和自动 checkpoint

- Agent：`backend-agent`
- 必需 skill：`eadp-backend`
- 优先级：P1
- 复杂度：Very High
- 依赖：EXE-002
- Scope：
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/agent/`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/CodingTaskExecutionService.java`
  - Runner/AgentExecution/CodingTaskExecution 测试

工作内容：

- 调度入口使用 invocation key 创建/复用 Run，并绑定稳定 Execution。
- Agent 启动前强制读取 progress snapshot，已完成 Execution 不启动模型。
- 把 `.agent_context/progress.json` 和 `nextActions` 注入 Agent brief。
- 自动采集 accepted、工具结束、文件变化、Git HEAD、测试报告、terminal 和 heartbeat。
- 保存 threadId/turnId/resumeFromCheckpoint；调整 Codex home 生命周期以支持恢复。
- Run 终态只更新 Run/observation，不直接把 Execution 判为完成。

验收条件：

- 同 invocation key 重入返回同一个 Run。
- 新 invocation key 命中同一 executionKey 时读取已有步骤并跳过 VERIFIED。
- 调用前失败不创建虚假进度。
- Agent 部分完成后 checkpoint 可被下一 Run 读取。
- terminal 回调丢失时仍保留 session、Git 和 checkpoint 对账证据。
- 日志 WebSocket 行为保持兼容。

### EXE-005 Requirement worktree lease、Git checkpoint 与接管

- Agent：`backend-agent`
- 必需 skill：`eadp-backend`
- 优先级：P2
- 复杂度：Very High
- 依赖：EXE-002、EXE-004
- Scope：
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/WorkspaceManager.java`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/WorktreeManager.java`
  - 新增的 workspace/progress 协调组件
  - Workspace/Git 恢复测试

工作内容：

- 数据库唯一 workspace 与现有物理 requirement workspace 建立绑定。
- 写入前同时取得 Requirement lease、本机文件锁和 fencing token。
- `APPLIED` 生成 checkpoint commit，CAS 推进 `currentHead`。
- Run 崩溃后的未提交变更通过 journal、Git diff 和 patch artifact 对账。
- 接管前终止旧 Agent 并等待退出；旧 token 的 Git/checkpoint 写入被拒绝。
- active/unknown/blocked workspace 禁止 GC，终态按配置保留。

验收条件：

- 同一需求不会生成第二个 worktree 或 feature branch。
- 下一步骤基于前一步已确认 HEAD。
- HEAD/parent/token 任一不匹配时进入 UNKNOWN/BLOCKED，不覆盖文件。
- 进程重启后可由数据库与 artifact 重建到 currentHead。
- 不同 Requirement 仍可并行。

### EXE-006 Effect ledger 与 MR 交付改造

- Agent：`backend-agent`
- 必需 skill：`eadp-backend`
- 优先级：P3
- 复杂度：Very High
- 依赖：EXE-002、EXE-005
- Scope：
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/progress/`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/RequirementDeliveryService.java`
  - 评论、memory job 和事件副作用的适配器
  - Effect/Delivery 测试

工作内容：

- 提供 effect execute/query/reconcile 注册机制。
- 接入评论、事件、memory job、push 和 MR 创建/更新。
- push 超时查询远端 branch SHA；MR 超时按 project + source branch 查询。
- 交付前合入最新 target、解决冲突后重新验证候选 HEAD。
- delivery effect 绑定 source SHA、target SHA、MR IID、URL 和 pipeline。
- 保持“只创建/更新 MR，不自动 merge”。

验收条件：

- 相同 effect key/hash 返回首次结果；不同 hash 稳定冲突。
- UNKNOWN effect 不直接重发。
- 重复交付只更新同一个 MR。
- MR source SHA 等于最终 VERIFIED workspace HEAD。
- 自动化 COMPLETED 与 MR OPEN/MERGED 状态独立。
- 代码中不存在 GitLab merge API 调用。

### EXE-007 ProgressReconciler 与补偿器切换

- Agent：`backend-agent`
- 必需 skill：`eadp-backend`
- 优先级：P4
- 复杂度：Very High
- 依赖：EXE-004、EXE-005、EXE-006
- Scope：
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/CompensationService.java`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/CompensationScheduler.java`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/progress/`
  - Compensation/Reconciler 测试

工作内容：

- Run 超时改为 `UNKNOWN` + observation，不直接把 CodingTask/Requirement 判失败。
- 对账 step、workspace HEAD、patch、session terminal event、测试和 effect。
- 能证明完成则补记 APPLIED/VERIFIED；不能证明则释放过期 lease 并开放安全接管。
- 仅在 required BLOCKED/UNKNOWN 无法自动解决时进入 WAITING_HUMAN。
- settlement 使用稳定幂等键，重复补偿不重复评论或事件。

验收条件：

- 三类失败分别走正确恢复路径。
- 超时但已完成不会再次启动相同步骤。
- 部分完成只续作 nextAction。
- 旧 Run 晚到终态不会覆盖新 owner 进度。
- 重复 compensation cycle 结果一致且无重复副作用。

### EXE-008 RequirementWorkspace 前端可观测视图

- Agent：`frontend-agent`
- 必需 skill：`suid`
- 优先级：P2
- 复杂度：High
- 依赖：EXE-003 冻结 DTO 契约
- Scope：
  - `frontend/src/services/`
  - `frontend/src/utils/requirement-progress-socket.ts`
  - `frontend/src/pages/OnlineCode/components/RequirementWorkspace/`
  - 对应前端测试

工作内容：

- 扩展 AutomationStatusBar，分开显示自动化和 MR 状态。
- 新增“执行进度”页签，显示阶段/步骤树、owner、lease/stale、checkpoint 和 nextAction。
- Run 列表显示 execution、attempt、恢复点、最新 observation 和验证状态。
- RunLogDrawer 增加执行记录、原始日志、证据三个视图。
- 实现 progress WebSocket 版本通知、全量 refetch、断线轮询和过期提示。
- 未知枚举显示原值；证据分页按需加载。

验收条件：

- 重复 Run 出现时能看见共享 Execution、当前 owner 和跳过步骤。
- `APPLIED/VERIFIED/UNKNOWN/BLOCKED` 视觉和文案明确不同。
- “自动化完成（MR 已提交）”不会显示为“已合入”。
- 乱序/重复事件不会回退 snapshot version 或重复 observation。
- 断线后继续轮询，恢复后全量刷新。
- 页面刷新后状态完全由服务端恢复。

### EXE-009 兼容迁移、开关和旧逻辑退场

- Agent：`backend-agent`
- 必需 skill：`eadp-backend`
- 优先级：P5
- 复杂度：High
- 依赖：EXE-003、EXE-004、EXE-006、EXE-007
- Scope：
  - 后端进度功能配置
  - 历史回填/校验脚本
  - 旧调度和补偿兼容适配
  - 迁移验证测试

工作内容：

- 增加 shadow-read、new-write、authoritative-ledger 分阶段开关。
- 新 Run 强制绑定 Execution；历史 Run 只做可证明的尽力回填。
- shadow 阶段比对旧 CodingTask/Requirement 状态与新聚合结果。
- ledger 成为权威后移除前端和补偿器的旧状态推断入口。
- 提供关闭新调度写入但保留账本只读的回滚路径。

验收条件：

- 任一阶段回滚不会删除 checkpoint/effect/observation。
- legacy Run 可查看且不会被伪装成 VERIFIED。
- 新旧状态差异有指标和可查询日志。
- authoritative 开关启用后，重复 Run 不再受旧 `hasActiveRun` 粗粒度逻辑误阻塞。

## 6. 独立验收任务

以下任务由 `test-agent` 独立执行，不在 coding task 完成后隐式触发。

### ACC-001 原子性与并发验收

- Agent：`test-agent`
- 优先级：P6
- 依赖：EXE-001、EXE-002、EXE-004、EXE-005、EXE-009
- Scope：测试文件和测试报告

验收条件：

- 50 个并发 Run 争抢同一步骤时恰好一个 owner。
- lease 失效接管与旧 token 晚到写入测试通过。
- checkpoint/observation sequence 不重复。
- Execution/effect/invocation 唯一键竞态返回稳定结果。
- 数据库事务故障注入不产生部分状态。

### ACC-002 三类失败、Effect 与 MR 恢复验收

- Agent：`test-agent`
- 优先级：P6
- 依赖：EXE-006、EXE-007、EXE-009
- Scope：测试文件和测试报告

验收条件：

- 调用前失败：下一个 Run 从首个未完成步骤开始。
- 过程失败：VERIFIED 跳过、APPLIED 只验证、当前步骤从 checkpoint 继续。
- 实际完成但超时：对账后收口，不重新调用 Agent 或 effect。
- push/MR 响应丢失时先查询远端并复用唯一 MR。
- 平台从未调用 merge API，MR OPEN 时自动化可显示已完成但未合入。

### ACC-003 前端可观测与断线验收

- Agent：`test-agent`
- 优先级：P6
- 依赖：EXE-003、EXE-008、EXE-009
- Scope：前端 E2E、契约测试和测试报告

验收条件：

- 正常 WebSocket 下进度变化 3 秒内可见。
- 重复、乱序、漏失事件不会造成页面状态回退。
- WebSocket 断开后出现过期提示并按 5 秒/30 秒策略轮询。
- Run 失败与 Execution/Requirement 完成状态不会混淆。
- 未授权用户看不到敏感证据，也不能追加人工 observation。
- 浏览器刷新后恢复相同进度与时间线。

## 7. 交付任务

### DEL-001 创建或更新唯一 MR

- Agent：`scm-agent`
- 优先级：P7
- 依赖：ACC-001、ACC-002、ACC-003
- Scope：Git 元数据与 MR，不修改业务代码

验收条件：

- 所有实现和修复位于同一 requirement feature branch。
- MR source SHA 等于三项验收使用的最终 SHA。
- MR 描述附 ADR、迁移顺序、验证报告和回滚方式。
- 已请求 CODEOWNERS 审核。
- 不自动合并 MR。

## 8. 风险与控制

| 风险 | 影响 | 控制 |
|---|---|---|
| `eadp-backend`/`suid` skill 未启用 | 无法按项目规范编码 | 实施前置门禁，缺失即 BLOCKED |
| 新旧状态双写漂移 | 错误完成或重复恢复 | shadow 比对、差异指标、分阶段权威切换 |
| checkpoint 写放大 | 数据库压力 | 自动采集 debounce、详情分页、保留策略 |
| Requirement 单写限制吞吐 | 单一大需求耗时增加 | 首期接受，记录 lease 等待和步骤耗时后再评估 |
| 证据包含敏感信息 | 信息泄露 | 后端字段级授权、脱敏、按需加载 |
| WebSocket 丢失或乱序 | UI 暂时过期 | snapshotVersion + 权威 refetch + 轮询 |
| 第三方无查询也无幂等 | UNKNOWN 无法自动恢复 | WAITING_HUMAN，禁止自动重发 |
| 历史 Run 证据不足 | 无法可靠回填 | 保持 legacy/unverified，不伪造进度 |

## 9. 实施启动门禁

计划顺序已经落地。开始 EXE-001 前仍需解除以下环境门禁：

1. 提供或允许安装 `eadp-backend` 与 `suid` skill；
2. 核对当前 `main` 上 8 个未提交后端改动的归属，并决定如何形成可恢复的基线 commit；
3. 创建或确认本 Requirement 的唯一 feature branch/worktree，并把实际路径写入交接文件。

Requirement 级单写、同需求唯一 worktree 和不自动合并 MR 已由 ADR 批准，不在实施计划阶段重新
开放决策。
