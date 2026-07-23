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
