# SEI Monorepo 项目规范

## 技术栈

| 模块 | 技术栈 | 构建工具 |
|------|--------|---------|
| 后端 | Java + Spring Boot + sei-core | Gradle |
| 前端 | React + UmiJS + @ead/suid | pnpm |

## AI Skill 使用规则

- **后端开发**：必须使用 `eadp-backend` skill，遵循 sei-core 分层架构规范
- **前端开发**：必须使用 `suid` skill，优先使用 @ead/suid 组件库

## CI/CD 规则
- 前后端独立版本号，git tag 格式：v1.0.0-web / v1.0.0-service

## 分支策略

- Trunk-based：主干 main + 短生命周期 feature 分支
- 合并前需对应 CODEOWNERS 审查

## 文档管理

- 所有的文档都按照需求放入到docs下管理

## 计划与验收节奏

- PM 制定执行计划时，必须把验收表达为独立任务，由 `test-agent` 执行；不要在每个 coding task 完成后隐式触发 test-agent。
- 验收任务的数量、范围和依赖时点由 PM 根据需求复杂度、前后端依赖和风险自行判断。
- coding task 完成后只进入后续计划任务调度；只有计划中的验收任务才运行 test-agent。
- 验收任务未通过时，PM 必须根据 `test-agent` 的失败报告分析原因，补充并调度对应的修复任务；修复完成后再次执行 `test-agent` 验收。验收任务及整体计划保持未完成状态，直到所有阻断问题修复且验收通过。若失败源于测试用例或环境，则补充相应的测试或环境修复任务后重新验收。

## 任务交付审阅与动态调度

- 每个执行任务结束时，执行 agent 必须提交结构化交付摘要，至少包含：完成内容、交付物及修改文件、验收条件对应证据、自测结果、对下游提供的契约或产物，以及未解决问题、风险和偏差。
- PM 在调度后续任务前，必须对交付摘要进行轻量审阅，只检查交付物完整性、验收证据、范围偏差、依赖满足情况及下游输入是否可用；轻量审阅不代替代码审查或 `test-agent` 验收。
- 交付完整且无异常时，PM 只记录“审阅通过”并继续调度，不重读全部代码、完整日志或重述整体计划。
- 仅在交付物缺失、证据不足、范围偏移、跨任务契约变更、依赖失败、交付结果冲突或高风险任务出现异常时，PM 才进行深度审阅，并据此补充、拆分、重开或重排尚未完成的任务。
- 若某个 run 失败的唯一原因是其他任务的交付物不足，PM 应将其归类为“上游依赖交付不完整”：保留当前 run 的失败证据，将当前任务标记为依赖阻塞，重开上游任务或新建修复任务，并按“上游修复 → 当前任务重试 → 计划中的验收任务”更新依赖。
- PM 可以根据审阅结果优化未完成任务的说明、依赖、顺序和验收条件，但不得为了让任务通过而默默降低原定验收标准；需求或标准变更必须显式记录。

## 提交规范

```
<type>: <description>
```

类型：feat, fix, refactor, docs, test, chore, perf, ci

## 目录边界约束
- Frontend MUST NOT reference backend internals (backend)
- Backend MUST NOT reference frontend code (frontend)

## 租户隔离

- 本项目不进行租户隔离（单租户 / 无 `tenant_code` 维度）：不引入 `ITenant`、不带 `tenant_code` 列、不做多租户上下文过滤
- `eadp-backend` skill 中描述的 `ITenant` / `tenantCode` 仅为 sei-core 框架能力说明，本项目不采用

## rules
- 如果任务超过3个文件的修改(不包括测试文件)，使用superpowers进行任务拆解和执行
- 项目内同级目录下的CLAUDE.md和AGENT.md都是相同内容，你只用了解其中一个
