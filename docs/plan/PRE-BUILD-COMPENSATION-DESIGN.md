# 编码前补偿机制设计

## 1. 目标

为“概要设计 → 详细设计 → 功能设计 → 编码执行”链路补充统一补偿机制，解决以下问题：

- 生成失败后长期停留在失败态，无自动重试
- 上游已确认，但下游产物缺失，链路断开
- 历史兼容路径导致状态合法但流程未推进
- 失败信息不足，agent 和人工无法快速定位问题

补偿任务由后端定时执行，默认每 1 分钟扫描一次。

## 2. 核心策略

采用“分层策略”：

- 已确认产物：只补下游，不覆盖当前产物
- 未确认产物：只补失败项，不自动覆盖人工草稿
- 人工编辑区：不自动改写
- 所有失败：必须记录结构化失败信息

这意味着补偿器不是“全量重跑器”，而是“状态修复式补偿器”。

## 3. 自动补偿边界

### 3.1 自动补偿

- `Plan.status = FAILED`
- `Spec.state = FAILED`
- `FeatureDesign.status = FAILED`
- `FeatureDesign.buildStatus = BUILD_FAILED`
- `Plan.status = CONFIRMED` 但无对应 `Spec`
- `Spec.state = CONFIRMED` 但无对应 `FeatureDesign`
- `FeatureDesign.status = CONFIRMED` 且已定义为应自动进入编码，但无对应 build/run
- `FeatureDesign.buildStatus = BUILDING` 且超时

### 3.2 不自动补偿

- `Plan.status = DRAFT`
- `Spec.state = SPEC_REVIEW`
- `FeatureDesign.status = DRAFT`
- `FeatureDesign.status = STALE`
- `FeatureDesign.buildStatus = STALE`

原因：

- `DRAFT` 表示人工可能正在编辑
- `SPEC_REVIEW` 需要人工确认，不允许补偿器越权
- `STALE` 表示上游已变化，是否重生应由人工决定

## 4. 补偿路径

### 4.1 概要设计补偿

#### A. `Plan.status = FAILED`

处理：

- 记录失败信息
- 判断是否满足重试条件：`retry_count < max_retry` 且 `now >= next_retry_at`
- 条件更新抢占：`FAILED -> GENERATING`
- 调用 `PlanAgentService.spawnPlanning(projectId, retryHint)`

说明：

- 不新建额外 Plan 版本，优先在当前 latest 失败记录上重试
- `retryHint` 应包含最近一次失败摘要

#### B. `Plan.status = CONFIRMED` 但无 `Spec`

处理：

- 若 `Plan.content.modules` 非空：按每个 module 补建 `Spec`
- 若仅有 legacy `features`：补建一个 fallback `Spec`
- 新建 `Spec.state = GENERATING`
- 调用 `SpecAgentService.spawnRequirement(projectId, null, specId)`

说明：

- 这是断链补全，不修改已确认 Plan

### 4.2 详细设计补偿

#### A. `Spec.state = FAILED`

处理：

- 记录失败信息
- 判断重试窗口
- 条件更新抢占：`FAILED -> GENERATING`
- 调用 `SpecAgentService.spawnRequirement(projectId, retryHint, specId)`

#### B. `Spec.state = CONFIRMED` 但无 `FeatureDesign`

处理：

- 补起功能设计生成
- 复用 `SpecService.confirmSpec(specId)` 的特征提取逻辑，或抽成共享方法
- 若 `spec.moduleId` 缺失但 Plan 已模块化：
  - 汇总所有模块下的 `features`
  - 作为兼容补偿路径触发 `PlanAgentService.spawnFeatureDesigns(projectId, features)`

说明：

- 该规则用于兜底历史兼容路径
- 不允许自动把 `SPEC_REVIEW` 的 Spec 推进为 `CONFIRMED`

### 4.3 功能设计补偿

#### A. `FeatureDesign.status = FAILED`

处理：

- 记录失败信息
- 判断上游 `Spec.state = CONFIRMED`
- 判断重试窗口
- 条件更新抢占：`FAILED -> GENERATING`
- 调用 `PlanAgentService.spawnFeatureDesign(projectId, featureId, retryHint)`

#### B. `FeatureDesign.status = CONFIRMED` 但缺失编码任务

处理：

- 仅当系统定义“确认后最终必须自动编码”时启用
- 条件更新抢占：`build_status in (IDLE, BUILD_FAILED) -> BUILDING`
- 调用 `FeatureDesignBuildService.buildOne(featureDesignId)`

说明：

- 如果保留“人工点击执行编码”语义，则这里只记录告警或展示异常，不自动 build
- 本设计默认支持自动补偿 build，但建议通过配置开关控制

### 4.4 编码执行补偿

#### A. `FeatureDesign.buildStatus = BUILD_FAILED`

处理：

- 记录失败信息
- 前置条件：`FeatureDesign.status = CONFIRMED`
- 判断重试窗口
- 条件更新抢占：`BUILD_FAILED -> BUILDING`
- 调用 `FeatureDesignBuildService.buildOne(featureDesignId)`

#### B. `FeatureDesign.buildStatus = BUILDING` 且超时

处理：

- 先标记为 `BUILD_FAILED`
- 写入失败原因：`failure_code = BUILD_TIMEOUT`
- 不在同一轮直接重跑
- 下一轮按 `BUILD_FAILED` 逻辑进入重试

说明：

- 避免同一轮内“超时判定 + 立即重跑”导致重复运行

## 5. 扫描顺序

每分钟固定按以下顺序执行：

1. 补偿 `Plan FAILED`
2. 补偿 `Plan CONFIRMED but Spec missing`
3. 补偿 `Spec FAILED`
4. 补偿 `Spec CONFIRMED but FeatureDesign missing`
5. 补偿 `FeatureDesign FAILED`
6. 补偿 `FeatureDesign CONFIRMED but build missing`
7. 补偿 `BUILD_FAILED`
8. 扫描并收口 `BUILDING timeout`

原因：

- 先修上游，再修下游
- 先补设计，再补编码
- 避免下游重试时仍依赖缺失的上游产物

## 6. 失败信息模型

所有失败节点必须补充统一失败字段。

### 6.1 建议字段

- `failureCode`
- `failureStage`
- `failureSummary`
- `failureDetail`
- `lastFailedAt`
- `lastRetryAt`
- `retryCount`
- `nextRetryAt`
- `lastTriggerSource`

### 6.2 字段语义

- `failureCode`
  - 稳定枚举，供程序识别
- `failureStage`
  - 当前失败发生在哪个阶段
- `failureSummary`
  - 给人看的短摘要
- `failureDetail`
  - 原始 stderr、异常栈、模型输出截断头部等
- `lastFailedAt`
  - 最近失败时间
- `lastRetryAt`
  - 最近补偿重试时间
- `retryCount`
  - 已重试次数
- `nextRetryAt`
  - 下一次允许重试时间
- `lastTriggerSource`
  - 来源：人工触发 / 定时补偿 / 链式补偿

### 6.3 推荐枚举

`failureCode`：

- `AGENT_TIMEOUT`
- `AGENT_JSON_PARSE_FAILED`
- `AGENT_PROCESS_EXIT_NONZERO`
- `UPSTREAM_MISSING`
- `CHAIN_BROKEN`
- `BUILD_TIMEOUT`
- `BUILD_CONFLICT`
- `UNKNOWN`

`failureStage`：

- `PLAN`
- `SPEC`
- `FEATURE_DESIGN`
- `BUILD`

`lastTriggerSource`：

- `USER_ACTION`
- `SCHEDULED_COMPENSATION`
- `CHAIN_COMPENSATION`

## 7. 重试与退避

建议加入统一退避策略，避免失败任务每分钟反复触发：

- `maxRetry = 3`
- 第 1 次失败：1 分钟后重试
- 第 2 次失败：5 分钟后重试
- 第 3 次失败：15 分钟后重试
- 超过上限后停止自动补偿，仅保留失败信息

超过上限后：

- 前端显示“自动重试已停止”
- 允许人工手动重试
- 允许 agent 基于失败详情做定向重试

## 8. 并发与幂等

补偿器必须使用条件更新做抢占，禁止无锁重跑。

### 8.1 设计类抢占

- `FAILED -> GENERATING`

条件：

- 当前状态仍是 `FAILED`
- 未被其他线程修改

### 8.2 构建类抢占

- `BUILD_FAILED/IDLE -> BUILDING`

条件：

- `FeatureDesign.status = CONFIRMED`
- `build_status != BUILDING`

### 8.3 原则

- 补偿器与人工按钮复用同一套状态前置校验
- 只有抢占成功的线程才能继续执行
- 抢占失败视为“已有其他执行在推进”，本轮跳过

## 9. 数据落点建议

### 9.1 最小改动方案

直接在现有表补失败字段：

- `oc_plan`
- `oc_spec`
- `oc_feature_design`

优点：

- 实现简单
- 查询当前失败态直接可得

### 9.2 推荐补充日志表

新增 `oc_compensation_log`，保留每次补偿动作：

- `id`
- `entityType`
- `entityId`
- `action`
- `success`
- `message`
- `detail`
- `triggerSource`
- `createdAt`

用途：

- 追踪补偿执行历史
- 辅助人工排查“为什么重复失败”
- 给后续监控和告警提供基础数据

## 10. 服务落点

建议新增：

- `CompensationScheduler`
  - `@Scheduled(fixedDelay = 60000)`
- `CompensationService`
  - `compensatePlans()`
  - `compensateSpecs()`
  - `compensateFeatureDesigns()`
  - `compensateBuilds()`
- `FailureInfoSupport` 或等价工具类
  - 统一写失败信息
  - 统一计算退避时间

复用现有：

- `PlanAgentService.spawnPlanning`
- `SpecAgentService.spawnRequirement`
- `PlanAgentService.spawnFeatureDesign`
- `PlanAgentService.spawnFeatureDesigns`
- `FeatureDesignBuildService.buildOne`

原则：

- 补偿器只负责“筛选 + 抢占 + 调用 + 记录”
- 不在补偿器内部重写已有业务逻辑

## 11. 前端展示建议

以下页面需补失败信息展示：

- 概要设计页
- 详细设计页
- 功能设计页

至少展示：

- 失败摘要
- 最近失败时间
- 自动重试次数
- 是否已停止自动补偿

建议补一个“失败详情”弹窗，展示：

- `failureSummary`
- `failureDetail`
- `lastRetryAt`
- `lastTriggerSource`

## 12. 关键约束

- 补偿器不得自动确认 `Spec`
- 补偿器不得自动确认 `FeatureDesign`
- 补偿器不得覆盖 `DRAFT`
- `STALE` 只能提示，不自动重生
- `BUILDING timeout` 必须先收口成失败，再进入下一轮重试

## 13. 实施建议

建议分三步实现：

1. 补失败字段与补偿日志表
2. 落地补偿器骨架，只处理 `FAILED / BUILD_FAILED`
3. 再补“上游已确认但下游缺失”的断链补全

原因：

- 先把失败留痕补齐，后续所有补偿都会受益
- 先收口失败重试，再扩展到断链修复，风险更可控

## 14. 本设计的默认结论

- 自动重试：`FAILED / BUILD_FAILED`
- 自动补链：已确认上游缺下游
- 不自动覆盖：`DRAFT / SPEC_REVIEW / STALE`
- 强制留痕：所有失败与补偿动作都要可追踪

这套规则可以在不破坏人工确认边界的前提下，为现有链路提供稳定的自愈能力。
