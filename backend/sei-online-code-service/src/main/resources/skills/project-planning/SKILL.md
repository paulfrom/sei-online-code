---
name: project-planning
description: 规划书生成 skill。将项目描述精炼为 Plan JSON（summary/techAssumptions/features[featureId,title,outline]/nonGoals）。
---

# project-planning Skill (stub)

> TODO: replace with real skill content. 完整技能内容尚未沉淀；当前为占位 stub，
> 由 `BuiltInSkillRegistry` 从 classpath 加载（multica 维度 g）。

强制输出 Plan JSON 骨架：

- `summary`: 项目一句话摘要
- `techAssumptions`: 技术假设（如 Vite+React+TS+@ead/suid+MSW）
- `features[]`: `{ featureId, title, outline }`
- `nonGoals`: 明确不做的事项

禁止预估 fileScope（留给 feature-design 阶段）。
