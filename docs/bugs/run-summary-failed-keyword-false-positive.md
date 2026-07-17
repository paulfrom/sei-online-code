# Run 摘要 `FAILED` 关键字误判

- 日期：2026-07-17
- 严重程度：高
- 影响范围：开发任务 run 完成状态判定

## 现象

Agent 成功完成任务并产生工作区变更，但只要完成摘要正文包含大写单词 `FAILED`，run 就会被标记为失败。业务枚举说明（例如 `ENROLLED/WAITLISTED/FAILED`）也会触发误判。

## 根因

`CodingTaskExecutionService#decideCompletion` 在 `AgentExecutionResult.succeeded()` 为 `true` 后，仍使用 `result.output().contains("FAILED")` 扫描整段自然语言输出。该字符串没有结构化语义，无法区分任务状态与正文内容。

## 修复

移除自然语言关键字判定。run 是否成功继续由以下结构化或可验证信号决定：

- `AgentExecutionResult.succeeded()`；
- 输出是否为空；
- 指定工作区是否产生代码或文档变更。

新增回归测试，覆盖成功摘要包含 `ENROLLED/WAITLISTED/FAILED` 且工作区有变更的场景，预期 run 状态为 `SUCCEEDED`。

## 预防

后续如需支持 Agent 主动声明业务失败，应增加结构化状态字段，不应扫描自然语言摘要中的裸关键字。
