# PRD 确认后一直停留在规划中

## 现象

PRD 确认成功后，需求状态变为 `PLANNING`，但没有生成执行计划，状态也不再推进。

## 根因

`RequirementService.confirmPrd` 在原事务的 `afterCommit` 回调中调用
`RequirementAutomationService.startInitialLoop`。该方法会保存新的 `activeLoopId`，随后发布异步规划事件。

Spring 在 `afterCommit` 阶段仍可能保留原事务资源，但原事务已经完成提交。如果此时没有开启新事务，
`activeLoopId` 的保存不会获得后续提交。异步监听器重新查询需求时读不到该 loop，
`executePreparedLoop` 将事件判定为 stale 并直接返回；原事务中已保存的 `PLANNING` 状态因此永久保留。

## 修复

为 `startInitialLoop` 增加 `REQUIRES_NEW` 事务边界，确保 `activeLoopId` 在发布异步事件前独立提交。

## 回归防护

新增测试锁定 `startInitialLoop` 的 `REQUIRES_NEW` 事务契约，防止后续重构移除该边界。

## 修改文件

- `RequirementAutomationService.java`
- `RequirementAutomationServiceTest.java`

## 预防建议

所有从 `TransactionSynchronization.afterCommit` 调用且包含数据库写入的服务入口，都应显式使用
`REQUIRES_NEW`，或将写入移动到原事务提交前、仅在提交后发布事件。
