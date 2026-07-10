# sei-demo-mono

SEI ams Monorepo项目，前端 + 后端 统一管理。

## 项目结构

```
2668088422724877313/
├── backend/                ← 后端（Java + Spring Boot + sei-core）
│   ├── 2668088422724877313-api/        ← Feign API 接口定义
│   └── 2668088422724877313-service/    ← 业务服务实现
├── frontend/     ← 前端（React + TS + UmiJS + @ead/suid）
├── cicd/         ← cicd相关配置
└── docs/         ← 全局契约文件
```

## AI 编程工具

- 后端开发使用 `eadp-backend` skill
- 前端开发使用 `suid` skill
- 详见各子目录下的 CLAUDE.md
