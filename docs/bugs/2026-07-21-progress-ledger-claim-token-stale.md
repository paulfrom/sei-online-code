# 进度账本 claim 后稳定收口失败

- 报告日期：2026-07-21
- 修复日期：2026-07-21
- 严重程度：高
- 状态：已修复；CodingTask 账本已改为非阻断观测模式

## 现象

CodingTask Agent 已成功完成并产生工作区变更，但 Run 最终被标记为 `FAILED`，失败原因固定为：

```text
进度账本更新失败，已阻断旧式成功收口
```

## 当前数据证据

对当前数据库最近的 CodingTask Run、TaskExecution、ExecutionStep 和 ExecutionCheckpoint 做只读核对后确认：

- Run 已绑定 `execution_id`；
- 每个 Execution 已声明 `implement:coding-task` 和 `verify:coding-task` 两个 step；
- 失败发生时 implement step 从 `PENDING` 进入 `IN_PROGRESS`，并生成 owner 与 claim token；
- step 没有进入 `APPLIED`，checkpoint 数始终为 0；
- step 的 `started_at` 与 Run 因“进度账本更新失败”结束的时间几乎相同。

因此账本初始化和 step 声明有效，故障集中在 `claimStep → markApplied` 之间。

## 根因

`ProgressService.claimStep` 在事务中先调用 `findOne` 读取 step/version，再通过 JPA bulk UPDATE 写入新的
owner、claim token 和 fencing token，最后再次调用 `findOne` 返回已 claim 的 step。

`ExecutionStepDao.claimStep` 原先只有 `@Modifying`。JPA bulk UPDATE 不会自动同步一级缓存，第二次
`findOne` 返回的是更新前已经托管的旧对象。`CodingTaskProgressIntegrator` 因而取得旧的空 claim token，
紧接着调用 `markApplied` 时 owner/token 校验必然失败并返回 `STALE_OWNER`。

该缺陷不是概率问题：在当前调用顺序下，每个成功 Agent 都会稳定失败。

## 底层缺陷修复

为 `ExecutionStepDao.claimStep` 增加：

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
```

bulk UPDATE 完成后清理一级缓存，确保随后 `findOne` 从数据库读取刚生成的 claim token，后续
`markApplied`、APPLIED checkpoint、`markVerified` 和 VERIFIED checkpoint 可以继续执行。

同类检查还发现 `unblockForRetry` 也采用“先读取实体、bulk UPDATE、再回读”的结构，已同步增加相同的
缓存刷新配置，避免 BLOCKED 自动恢复返回旧状态。

## 当前架构收口

CodingTask 不再声明 `implement:coding-task` / `verify:coding-task` 两个平行固定步骤，也不再根据账本
状态跳过 Agent。Agent 成功后只追加带变更文件证据的 observation；preflight、摘要、证据和终态记录
全部采用独立事务的 best-effort 语义，失败只告警，不改变 Run/CodingTask 的业务结果。

任务恢复改由执行计划负责：同一 CodingTask 的新 Run 通过 `parentRunId` / `attemptNo` 关联上一次尝试，
在原工作区检查并复用已有成果。

## 回归覆盖

`ProgressServiceTest.claimStep_bulkUpdateClearsPersistenceContextBeforeReload` 固化 claim 与自动解阻塞两条
DAO 写路径的缓存刷新契约；缺少任一配置时测试失败。

相关测试夹具已与当前构造器同步，完整 `:sei-online-code-service:test` 通过。

当前系统只运行新数据，不需要旧固定步骤的数据迁移。低层 claim 修复仍保留，供其他真实 step/effect
协议复用。
