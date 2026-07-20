# 恢复执行计划后 Run 与任务消失

## 现象

点击“恢复执行计划”后调度事件已触发，Agent 也开始执行，但任务没有推进，并出现日志：

```text
finishRun skipped because run/task disappeared
```

## 根因

恢复服务通过 `TransactionUtil.afterCommit` 发布同步调度事件。事件监听器调用
`CodingTaskScheduler.schedule()`，但该公开入口原本没有事务；事务注解位于同类内部调用的
`doSchedule()` 上，无法经过 Spring 代理生效。

在 `afterCommit` 阶段调用 `executePlanTask()` 时，它会复用已完成提交但资源尚未解绑的旧事务。
新建 Run 和 CodingTask 的 `RUNNING` 状态没有获得新的提交机会，异步 Agent 完成后无法从数据库
查询对应记录。

## 修复

- 将事务边界移动到公开的 `schedule()` 入口。
- 使用 `Propagation.REQUIRES_NEW`，保证恢复、补偿和任务完成事件触发的每次调度都有独立事务。
- Agent 仍通过 after-commit 回调启动，因此启动前 Run 与任务状态已经提交。

## 验证

- `CodingTaskSchedulerTest` 验证公开调度入口必须使用 `REQUIRES_NEW`。
- `CodingTaskExecutionServiceTest` 验证 Agent 在事务提交后启动。
- `RequirementAutomationServiceTest` 验证当前计划恢复并重新投递调度。

## 预防

事务注解必须放在由其他 Spring Bean 调用的公开方法上。不要依赖同类自调用触发
`@Transactional`，尤其不要从事务完成回调直接进入没有新事务边界的持久化流程。
