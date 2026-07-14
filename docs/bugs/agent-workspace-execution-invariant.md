# Agent 未统一绑定项目工作区

## 现象

部分 Agent 在临时技能目录或调用方传入的目录运行，而 validation command 在项目配置的工作区运行，导致开发结果与验证对象不一致。

## 根因

- `CliRunnerRegistry.resolve()` 向所有业务服务公开，业务层可直接取得 runner。
- `CliRunner.execute(...)` 接受任意 cwd 字符串，平台没有统一校验 cwd 与 projectId 的关系。
- Spec、Plan、PRD、memory-review Agent 将临时技能目录直接作为 cwd。
- 工作区解析分散在各业务服务中，无法形成系统级不变量。

## 修复

- 增加不可由业务包构造的 `AgentWorkspace` 绑定对象。
- `CliRunnerRegistry.workspace(projectId)` 统一通过 `WorkspaceManager` 解析并校验目录。
- `CliRunnerRegistry.execute(...)` 启动前重新解析工作区；配置发生漂移时拒绝启动。
- `CliRunnerRegistry.resolve()` 收紧为包可见，业务服务无法再直接取得 runner。
- 所有项目级 Agent 统一迁移到绑定执行入口；技能和 brief 写入同一项目工作区。

## 验证失败 RCA

- **Problem**：首次 `compileJava` 时 `ValidationLoopService` 两处分支使用了相反的工作区类型。
- **Root Cause**：机械迁移时按变量名替换，没有先区分 validation command 与 test-agent review 两个语义分支。
- **Fix Applied**：validation command 保留 `WorkspaceResolveResult`；test-agent review 使用 `AgentWorkspace`。
- **Prevention**：迁移同名局部变量时按方法边界逐处编译，并用统一入口测试覆盖实际 cwd。
- **CD Impact**：一次编译失败后修正，无运行时数据影响。

## 防回归

- 测试验证 runner 收到的 cwd 必须等于项目解析工作区。
- 测试验证准备与执行之间工作区配置变化时拒绝旧绑定。
- 生产源码扫描不得存在业务服务直接调用 `runner.execute` 或 `registry.resolve`。

