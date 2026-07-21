# 调度器跨 Loop 错误解析同名任务依赖

## 现象

需求进入新的执行 loop 后，部分前置任务已经成功，但依赖这些任务的后续 CodingTask 仍保持
`BLOCKED`，且当前没有活动 Run，自动调度无法继续推进。

## 根因

`CodingTaskScheduler` 原先按 `requirementId` 查询全部历史 CodingTask，并直接以
`planTaskKey` 构造依赖映射。不同 loop 会复用相同任务 key；发生冲突时映射静默保留查询结果中的
第一条记录，而 DAO 查询没有稳定排序。

当映射选中旧 loop 的同名 `STALE` 任务时，调度器会把已经由当前 loop 成功完成的依赖误判为
失败，从而持续将后续任务保留为 `BLOCKED`。具体选中哪个版本依赖数据库返回顺序，因此表现为
部分任务可以继续、部分任务永久阻塞。

## 修复

- 仍查询全部任务，以便将非当前 loop 的任务收敛为 `STALE`。
- 依赖映射、活动 lane/fileScope 统计和候选任务遍历仅使用 `loopId` 等于需求
  `activeLoopId` 的任务。
- 当前 loop 内若出现重复 `planTaskKey`，不再通过 merge function 静默选择任一记录。

## 验证

- 新增回归测试：旧 loop 的 `BE-002=STALE` 排在当前 loop 的同名
  `BE-002=SUCCEEDED` 之前，依赖它的当前任务仍应从 `BLOCKED` 恢复并启动。
- 临时撤回修复后，该用例按预期失败；恢复修复后 `CodingTaskSchedulerTest` 全部通过。

## 预防

任务依赖 key 只在单个 execution loop 内唯一。任何按 `planTaskKey` 建图、聚合或判断状态的逻辑，
都必须先限定到当前 `activeLoopId`，不能依赖状态过滤或数据库返回顺序区分历史版本。
