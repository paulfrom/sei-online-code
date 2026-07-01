# sei-online-code

SEI 在线编码 Monorepo 项目，前端 + 后端 统一管理。

## 项目结构

```
sei-online-code/
├── backend/                       ← 后端（Java + Spring Boot + sei-core）
│   ├── sei-online-code-api/       ← Feign API 接口定义
│   └── sei-online-code-service/   ← 业务服务实现
├── frontend/                      ← 前端（React + UmiJS + @ead/suid）
├── cicd/                          ← cicd相关配置
└── .claude/skills/                ← AI skill 定义
```

## AI 编程工具

- 后端开发使用 `eadp-backend` skill
- 前端开发使用 `suid` skill
- 详见各子目录下的 CLAUDE.md"# sei-online-code" 
"# sei-online-code" 
