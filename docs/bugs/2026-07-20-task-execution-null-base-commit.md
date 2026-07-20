# TaskExecution 空基线提交导致创建失败

- 日期：2026-07-20
- 严重级别：高
- 状态：已修复

## 问题

创建 `CODING_TASK` 类型的 `oc_task_execution` 记录时，PostgreSQL 报错：

```text
null value in column "base_commit" of relation "oc_task_execution" violates not-null constraint
```

## 根因

`CodingTaskChangeCollector.resolveHead()` 在 Git 仓库尚无首个提交或无法读取 HEAD 时返回
`null`。`CodingTaskProgressIntegrator.preflight()` 未规范化该值，直接传给
`ProgressService.findOrCreateExecution()`，与 `oc_task_execution.base_commit NOT NULL`
的数据模型约束冲突。

## 修复

在进度账本 preflight 边界将空白基线提交规范化为 40 位全零 SHA。该约定与
`WorkspaceManager.getCurrentHead()` 对 unborn repository 的既有处理保持一致，并继续允许
统一账本模式为任务创建 Execution。

## 修改文件

- `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/agent/CodingTaskProgressIntegrator.java`
- `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/service/agent/CodingTaskProgressIntegratorTest.java`

## 验证

- 新增 `preflight_nullBaseCommit_usesUnbornHeadPlaceholder` 回归测试。
- 定向运行 `CodingTaskProgressIntegratorTest`，测试通过。

## 预防

所有写入进度账本的 Git commit 字段都应在服务边界转换为非空的持久化表示，不应把
Git 命令的可空结果直接传入数据库实体。
