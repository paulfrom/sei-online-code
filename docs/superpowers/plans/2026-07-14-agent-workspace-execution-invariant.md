# Agent 工作区执行不变量实施计划

## 目标

所有项目级 Agent 必须绑定由 `WorkspaceManager.resolve(projectId)` 解析出的工作区后才能启动。业务服务不得直接取得 `CliRunner`，也不得向 runner 传入任意 cwd。

## 不变量

1. `projectId` 是工作区解析的唯一业务输入。
2. 解析结果封装为不可由业务包构造的 `AgentWorkspace`。
3. `CliRunnerRegistry` 在每次执行前重新解析并比对项目与路径，配置漂移时拒绝启动。
4. `CliRunnerRegistry.resolve` 不再向业务包公开，所有 Agent 统一通过工作区绑定入口执行。
5. Skill 和 Agent brief 写入同一个绑定工作区，不再创建临时目录作为 Agent cwd。

## 实施步骤

1. 增加 `AgentWorkspace` 值对象及 registry 的 resolve/validate/execute 入口。
2. 迁移需求、规划、功能设计、PRD、记忆评审、PM、开发、测试评审 Agent。
3. 增加错误路径拒绝、配置漂移拒绝和正确 cwd 传递测试。
4. 运行 service 相关单测、`compileJava` 与 `git diff --check`。

## 验证执行模型修正

- 测试、验证、打包不再由服务端固定命令执行。
- `ValidationLoopService` 只负责创建 `test-agent` Run，并通过 `CliRunnerRegistry.workspace(projectId)` 绑定项目工作区后启动 Agent。
- `test-agent` 必须在绑定工作区内读取工作区说明、构建文件、脚本和验收条件，自行选择测试 / build / package 命令。
- 服务端只解析 `test-agent` 返回的 `passed` 结果并记录 `VALIDATION_RESULT`，解析不到通过结论时按失败处理。
- 原 `ValidationCommandExecutor` / `ProcessValidationCommandExecutor` / 固定 Gradle 命令兼容逻辑已移除，避免重新形成服务端直跑验证命令的路径。
