---
owner: project
origin: platform_seed
memorySeedTemplateCode: default
agentMemorySeedVersion: 1
reviewStatus: unreviewed
lastReviewedAt:
---

# Memory Rules

> 本文件由平台 seed 初始化生成（origin: platform_seed），是记忆扫描与冲突检查的默认起点。
> memory-rules.md 只能增强扫描、冲突检查、文档章节和忽略规则，不能删除 platform-memory 必填文件、front matter、sourceFiles、freshness/status/version 信息，不能关闭冲突分析或代码现状扫描（计划 §6）。

## Scan Focus

指定代码扫描时需要额外关注的目录或文件类型，作为平台固定扫描层的补充。

- 待项目维护：列出需要重点扫描的模块/目录。

## Conflict Checks

指定需要额外检查的冲突类型，作为平台默认冲突分析的增强。

- 待项目维护：列出项目特定的冲突检查项。

## Required Sections

指定 PRD / 概览设计 / 详细设计必须包含的额外章节，作为平台必填章节的补充。

- 待项目维护：列出项目必填章节。

## Domain-Specific Rules

项目领域特定的记忆规则，例如必须校验的契约、禁止的 API 形态等。

- 待项目维护：列出领域特定规则。

## Ignore Rules

扫描时应忽略的路径、文件或模式，避免噪声进入 RealityClaim。默认已排除 .git/node_modules/dist/build/target/out/coverage/logs/tmp/.cache/.idea/.vscode。

- 待项目维护：列出额外忽略规则。