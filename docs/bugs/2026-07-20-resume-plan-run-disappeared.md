# 恢复执行计划后 Run 与任务消失

## 现象

点击“恢复执行计划”后调度事件已触发，Agent 也开始执行，但任务没有推进，并出现日志：

```text
finishRun skipped because run/task disappeared
```

## 根因

恢复服务通过 `TransactionUtil.afterCommit` 发布同步调度事件。事件监听器调用
`CodingTaskScheduler.schedule()`，该公开入口原本没有独立事务；事务注解位于同类内部调用的
`doSchedule()` 上，无法经过 Spring 代理生效。

在 `afterCommit` 阶段调用 `executePlanTask()` 时，它会复用已完成提交但资源尚未解绑的旧事务。
新建 Run 和 CodingTask 的 `RUNNING` 状态没有获得新的提交机会，异步 Agent 完成后无法从数据库
查询对应记录。

后续补充发现，仅给 `schedule()` 增加 `REQUIRES_NEW` 仍不充分。sei-core 的
`TransactionUtil.afterCommit` 在外层 after-commit 回调执行期间会设置 `IN_COMMIT`；此时新事务
内部再次调用该工具，会立即执行回调，而不是注册到新事务。结果仍是 Agent 在 Run 提交前查询，
返回“Run 不存在”，CLI 从未启动，Run 则在提交后遗留为 `RUNNING` 并最终超时。

## 修复

- 将事务边界移动到公开的 `schedule()` 入口。
- 使用 `Propagation.REQUIRES_NEW`，保证恢复、补偿和任务完成事件触发的每次调度都有独立事务。
- Agent 启动不再使用带 `IN_COMMIT` 快捷执行逻辑的 sei-core `TransactionUtil`，改为直接向当前
  Spring 事务注册 `TransactionSynchronization.afterCommit`，确保启动前 Run 与任务状态已经提交。

## 验证

- `CodingTaskSchedulerTest` 验证公开调度入口必须使用 `REQUIRES_NEW`。
- `CodingTaskExecutionServiceTest` 验证 Agent 在事务提交后启动。
- `CodingTaskExecutionServiceTest` 覆盖“外层 afterCommit → REQUIRES_NEW → 内层 afterCommit”，
  验证内层提交前不得调用 Agent。
- `RequirementAutomationServiceTest` 验证当前计划恢复并重新投递调度。

## 预防

事务注解必须放在由其他 Spring Bean 调用的公开方法上。不要依赖同类自调用触发
`@Transactional`，尤其不要从事务完成回调直接进入没有新事务边界的持久化流程。
嵌套 after-commit 场景还必须确认所用工具不会因“正在提交”标记而绕过当前新事务；外部进程
启动应绑定到实际持久化事务的 synchronization。
