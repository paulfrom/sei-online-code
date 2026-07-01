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

## 提交规范

```
<type>: <description>
```

类型：feat, fix, refactor, docs, test, chore, perf, ci

## 目录边界约束
- Frontend MUST NOT reference backend internals (backend)
- Backend MUST NOT reference frontend code (frontend)

## rules
- 如果要求前后端同时开发，前后端必须先完成接口契约约定到docs,然后分别使用不同的子agent开发，不能在一个上下文中同时开发前后端
- 项目类所有的CLAUDE.md和AGENT.md都是相同约束，你只用了解其中一个
