# Workspace Memory 工程化实现计划

> 状态：审核定稿
> 日期：2026-07-10
> 范围：把“目标工作区项目规范、代码现状、需求冲突、设计依据”工程化为可持久化、可追溯、可校验的项目记忆能力，并接入 PRD、概览设计、详细设计、CodingTask 执行链路。

## 1. 目标

当前需求生成链路需要从“依赖 agent 临场理解”升级为“依赖工作区内可持续维护的项目记忆”。

目标能力：

- 新建项目工作区成功后自动初始化项目记忆。
- 平台 seed 记忆模板内容可配置，允许同时存在多个已发布模板；新项目可显式选择，未选择时使用全局唯一默认模板。
- 对已有项目，按项目已绑定模板补齐项目记忆文件并初始化平台记忆；未绑定模板时使用全局默认模板。
- 每次 PRD、概览设计、详细设计生成时，显式消费同一套需求级设计上下文。
- 每个 CodingTask 成功完成后，根据代码变更增量回写平台记忆。
- 项目自维护记忆优先级最高；平台生成记忆只作为结构化镜像和补充。
- 平台记忆有版本、有状态、有来源、有校验结果，可回溯每份文档当时使用的依据。

## 2. 非目标

第一版不做以下能力：

- 不新增 memory mock 数据和 mock handler。
- 不提供 `agent-memory` 在线编辑 UI。
- 不提供 `platform-memory` 在线编辑 UI。
- 不做历史版本 diff UI。
- 不引入向量库。
- 不把 `NormClaim` / `RealityClaim` / `ConflictFinding` 拆成明细表。
- 不自动修改 `agent-memory/**`。
- 不自动重写已确认的 PRD、概览设计、详细设计。
- 不要求第一版端到端真实 agent 全链路自动化测试。

## 3. 目录策略

目标工作区根目录新增两个非隐藏目录：

```text
<workspace>/agent-memory/
<workspace>/platform-memory/
```

### 3.1 `agent-memory`

`agent-memory` 是项目自维护记忆，最高优先级，由用户或项目 agent 维护，平台只读不写。

固定文件：

```text
agent-memory/
  project-memory.md
  memory-rules.md
  decisions.md
  modules.md
```

读取优先级：

```text
P0 agent-memory/project-memory.md
P1 agent-memory/*.md
P2 agent-memory/memory-rules.md
P3 AGENTS.md / CLAUDE.md / README.md / docs/**
P4 代码现状扫描
P5 平台默认规则
P6 platform-memory/*.md 上一版平台结果，仅作参考
```

规则：

- `project-memory.md` 为最高优先级。
- `memory-rules.md` 只能增强扫描、冲突检查、文档章节和忽略规则。
- `decisions.md` 维护已确认、已替代、待确认决策。
- `modules.md` 维护模块边界和集成点。
- 平台不自动修改 `agent-memory/**`。
- CodingTask 后发现长期规则建议时，写入 `platform-memory/decision-log.md` 的 `Generated Suggestions`，由人确认后迁移到 `agent-memory`。

### 3.2 `platform-memory`

`platform-memory` 是平台生成的 latest 镜像，由平台写入，工作区只保留最新版本。历史版本只保存在平台 DB。

固定文件：

```text
platform-memory/
  workspace-norms.md
  workspace-snapshot.md
  requirement-conflict-report.md
  design-basis.md
  decision-log.md
  metadata.json
```

规则：

- 平台可覆盖 `platform-memory/**`。
- 用户不应手改 `platform-memory/**`。
- 如果检测到 `platform-memory` 被手动修改，标记 `PLATFORM_MEMORY_DRIFT`，提示用户把长期修改迁移到 `agent-memory`。
- 直接手改 `platform-memory` 不作为权威输入。
- `platform-memory` 模板只能随平台 `memorySpecVersion` 演进。

## 4. `agent-memory` 文件模板

所有 `agent-memory` Markdown 文件使用少量 front matter：

```markdown
---
owner: project
memoryVersion: 1
lastReviewedAt:
---
```

### 4.1 `project-memory.md`

```markdown
# Project Memory

## Hard Rules

## Current Reality

## Preferred Direction

## Forbidden Choices

## Domain Terms

## Open Questions
```

### 4.2 `memory-rules.md`

```markdown
# Memory Rules

## Scan Focus

## Conflict Checks

## Required Sections

## Domain-Specific Rules

## Ignore Rules
```

### 4.3 `decisions.md`

```markdown
# Decisions

## Confirmed Decisions

## Superseded Decisions

## Pending Decisions
```

### 4.4 `modules.md`

```markdown
# Modules

## Module Boundaries

## Existing Modules

## Planned Modules

## Integration Points
```

## 5. `platform-memory` 文件模板

所有 Markdown 文件使用统一 front matter：

```markdown
---
generatedBy: sei-online-code
workspaceMemoryId: xxx
requirementDesignContextId: xxx
memorySpecVersion: 1
generatedAt: 2026-07-10T10:00:00+08:00
freshness: FRESH
source: platform
doNotEdit: true
---
```

### 5.1 `workspace-norms.md`

```markdown
# Workspace Norms

## Project Memory Overrides

## Hard Rules

## Preferred Direction

## Forbidden Choices

## Documentation Rules

## Testing And Delivery Rules

## Source Files
```

### 5.2 `workspace-snapshot.md`

```markdown
# Workspace Snapshot

## Modules

## Entrypoints

## API Surface

## Data Model

## UI Surface

## State Model

## Integration Points

## Scan Limits

## Source Files
```

### 5.3 `requirement-conflict-report.md`

```markdown
# Requirement Conflict Report

## High Severity

## Medium Severity

## Low Severity

## Open Questions

## Assumptions

## Source Files
```

说明：该文件是 latest，会随当前需求上下文更新。历史上下文只在 DB 保留。

### 5.4 `design-basis.md`

```markdown
# Design Basis

## Requirement

## Active Workspace Memory

## Applicable Norms

## Relevant Current Reality

## Conflicts To Address

## Decisions

## Constraints For PRD

## Constraints For Overview Design

## Constraints For Detailed Design

## Constraints For Coding
```

### 5.5 `decision-log.md`

```markdown
# Decision Log

## Confirmed Decisions

## Generated Suggestions

## Coding Task Updates

## Open Decisions
```

### 5.6 `metadata.json`

```json
{
  "workspaceMemoryId": "xxx",
  "requirementDesignContextId": "xxx",
  "memorySpecVersion": 1,
  "status": "CURRENT",
  "freshness": "FRESH",
  "generatedAt": "2026-07-10T10:00:00+08:00",
  "agentMemoryFingerprint": "...",
  "projectRuleFingerprint": "...",
  "sourceFingerprints": [],
  "scanTruncated": false,
  "doNotEdit": true
}
```

## 6. 记忆规范演进

平台维护全局记忆规范：

```text
memorySpecVersion = 1
agentMemorySeedVersion = 1
WorkspaceNorms schema v1
WorkspaceSnapshot schema v1
ConflictReport schema v1
DesignBasis template v1
```

规则：

- 全局 schema/template/prompt 只能平台侧维护。
- 项目差异通过 `agent-memory/memory-rules.md` 增强。
- 项目规则只能增强，不能删除全局必填字段和校验。
- 模板破坏性变化升级 `memorySpecVersion`。
- 规范变化后不自动覆盖历史记忆，只标记过期并触发重建。
- 已有 `WorkspaceMemory` 保留创建时使用的 `memorySpecVersion`。
- 平台统一 seed 记忆模板通过 `agentMemorySeedVersion` 管理，seed 模板变化不自动覆盖已有项目的 `agent-memory`。

项目不能通过 `memory-rules.md` 做以下事情：

- 删除 `platform-memory` 必填文件。
- 删除 Markdown front matter。
- 删除 `sourceFiles`。
- 删除 freshness/status/version 信息。
- 关闭冲突分析。
- 关闭代码现状扫描。
- 要求平台自动修改 `agent-memory`。

## 6.1 平台统一 Seed 记忆模板

空项目不能只初始化空白 `agent-memory`，否则 PRD 生成阶段没有稳定的默认关注点。平台应提供一套所有新项目共享的 seed 记忆模板，作为项目记忆的起始基线。

seed 模板内容必须可配置。内置 classpath 模板只作为首次启动或 DB 模板缺失时的兜底，不作为唯一配置来源。

配置来源优先级：

```text
1. 项目显式选择的 ACTIVE MemorySeedTemplate
2. DB 中 ACTIVE 且 is_default=true 的 MemorySeedTemplate
3. classpath 内置 default seed
```

第一版支持多模板选择：平台可同时维护多个 `ACTIVE` 模板，但只能有一个 `ACTIVE + is_default=true` 的全局默认模板。项目创建时可传入 `memorySeedTemplateId`；未传时使用全局默认模板。

建议资源目录：

```text
backend/sei-online-code-service/src/main/resources/memory-seeds/default/agent-memory/
  project-memory.md
  memory-rules.md
  decisions.md
  modules.md
```

后续如果存在不同项目模板，可扩展为：

```text
memory-seeds/default/agent-memory/**
memory-seeds/suid-frontend/agent-memory/**
memory-seeds/java-service/agent-memory/**
memory-seeds/fullstack-suid-eap/agent-memory/**
```

第一版只内置 `default` seed，并在初始化时导入 DB 作为兜底默认模板；其他可选模板由平台配置并发布。

### 6.1.0 Seed 模板配置模型

新增 `MemorySeedTemplate` 配置对象，保存四个 `agent-memory` 文件模板内容。

核心字段：

```text
code
name
description
version
status: DRAFT / ACTIVE / ARCHIVED
is_default
source_type: BUILTIN / USER_CONFIG
project_memory_template
memory_rules_template
decisions_template
modules_template
published_at
```

规则：

- 同一时间只能有一个 `ACTIVE + is_default=true` 模板。
- 可以同时存在多个 `ACTIVE` 模板，只有 `ACTIVE` 模板可供项目选择。
- 发布模板不会自动将其设为默认；设为默认是独立操作。
- 新项目显式选择模板时使用所选 `ACTIVE` 模板，未选择时使用全局默认模板。
- 修改模板内容不会自动影响已创建项目。
- 修改模板后需要发布新版本，不能直接覆盖已发布版本。
- 已创建项目记录实际使用的 `memory_seed_template_id` 和 `agent_memory_seed_version`。
- 如果 DB 默认模板不存在，平台从 classpath `memory-seeds/default` 创建一条 `ACTIVE + is_default=true` 模板。
- 如果 DB 默认模板存在，classpath 模板变化不自动覆盖 DB 配置，只能作为“可同步建议”。

### 6.1.1 Seed 文件 front matter

seed 写入 `agent-memory` 时，front matter 标记来源：

```markdown
---
owner: project
origin: platform_seed
memorySeedTemplateCode: default
memorySeedTemplateId: xxx
agentMemorySeedVersion: 1
reviewStatus: unreviewed
lastReviewedAt:
---
```

含义：

- `origin: platform_seed` 表示该内容由平台初始化生成，不是项目成员主动确认的长期记忆。
- `memorySeedTemplateId` 表示写入该项目时使用的 DB 模板版本。
- `reviewStatus: unreviewed` 表示它只能作为默认起点。
- 如果后续项目明确维护该文件，可由用户或项目流程把 `reviewStatus` 改为 `reviewed`。

### 6.1.2 Seed 优先级

需要区分“项目自己维护的 agent-memory”和“平台初始化写入的 seed agent-memory”。

优先级修订为：

```text
P0 agent-memory 中 reviewStatus=reviewed 或 origin=project 的项目自维护内容
P1 agent-memory/*.md 中明确的项目补充内容
P2 agent-memory/memory-rules.md 中明确的项目补充规则
P3 AGENTS.md / CLAUDE.md / README.md / docs/**
P4 代码现状扫描
P5 平台默认规则
P5S agent-memory 中 origin=platform_seed 且 reviewStatus=unreviewed 的 seed 内容
P6 platform-memory/*.md 上一版平台结果，仅作参考
```

规则：

- seed 内容是“默认基线”，不是高优先级项目事实。
- 对空项目，seed 会成为主要输入。
- 对已有项目，seed 只能补齐缺失记忆文件，不能覆盖已有项目规范和代码事实。
- seed 与项目规范冲突时，以项目规范为准。
- seed 与代码现状冲突时，代码现状进入 `RealityClaim`，冲突进入 `ConflictFinding`。

### 6.1.3 Seed 演进

平台 seed 模板通过 `agentMemorySeedVersion` 演进。

规则：

- 新项目使用显式选择的当前 `ACTIVE` seed；未选择时使用全局默认 seed。
- 已有项目不因 seed 版本升级而自动覆盖 `agent-memory`。
- 若 seed 版本升级，已有项目可选择手动执行“补充新 seed 建议”，平台将差异写入 `platform-memory/decision-log.md` 的 `Generated Suggestions`。
- 项目发展过程中的真实演进来自 `WorkspaceMemory` 版本、CodingTask 后回写、项目规范变化和人工确认，不依赖自动覆盖 seed。

## 7. 结构化模型

平台记忆将输入拆成三类结构化结论。

### 7.1 `NormClaim`

表示规范、目标、约束、设计意图。

示例：

```json
{
  "id": "norm-001",
  "type": "tech_stack",
  "content": "前端使用 React + UmiJS",
  "priority": "P0",
  "source": "agent-memory/project-memory.md",
  "sourceHash": "...",
  "confidence": "explicit",
  "overrides": ["norm-017"]
}
```

### 7.2 `RealityClaim`

表示代码现状和事实。代码事实不被项目记忆覆盖。

示例：

```json
{
  "id": "reality-001",
  "type": "frontend_stack",
  "content": "当前 package.json 使用 Vue",
  "source": "package.json",
  "sourceHash": "...",
  "confidence": "source_backed"
}
```

### 7.3 `ConflictFinding`

表示规范、需求和代码现状之间的冲突。

示例：

```json
{
  "id": "conflict-001",
  "type": "tech_stack",
  "severity": "HIGH",
  "summary": "项目记忆要求 React，但代码现状为 Vue",
  "normClaimIds": ["norm-001"],
  "realityClaimIds": ["reality-001"],
  "recommendedHandling": "clarify"
}
```

规则：

- 高优先级项目记忆覆盖低优先级规范结论。
- 同优先级冲突进入待确认。
- 代码现状作为 `RealityClaim`，不被项目记忆直接覆盖。
- “项目记忆 vs 代码现状”的差异进入 `ConflictFinding`。
- PRD、概览设计、详细设计、CodingTask 必须同时消费 `NormClaim`、`RealityClaim`、`ConflictFinding`。

## 8. 数据库设计

新增四张表：

```text
oc_memory_seed_template
oc_workspace_memory
oc_requirement_design_context
oc_memory_job
```

第一版 JSON 字段可用 `TEXT` + converter 或已有 JSON converter 承载，不拆明细表。

### 8.1 `oc_memory_seed_template`

表示平台可配置的项目记忆 seed 模板。

核心字段：

```text
code
name
description
version
status
is_default
source_type

project_memory_template
memory_rules_template
decisions_template
modules_template

published_at
archived_at
```

枚举：

```text
MemorySeedTemplateStatus:
DRAFT / ACTIVE / ARCHIVED

MemorySeedTemplateSourceType:
BUILTIN / USER_CONFIG
```

索引建议：

```text
idx_memory_seed_template_code(code)
idx_memory_seed_template_status(status)
idx_memory_seed_template_default(is_default, status)
uk_memory_seed_template_code_version(code, version)
```

业务规则：

- 同一时间只有一个 `ACTIVE + is_default=true` 模板。
- 可同时存在多个 `ACTIVE + is_default=false` 的已发布模板。
- 发布新版本时新版本改为 `ACTIVE`，被替代的同 code 旧版本改为 `ARCHIVED`；发布动作不自动改变全局默认模板。
- 设为默认时，在同一事务中取消原默认模板的 `is_default`，再把目标 `ACTIVE` 模板设为默认。
- 新项目初始化优先读取项目显式选择的 `ACTIVE` 模板，未选择时读取全局默认模板。
- classpath `memory-seeds/default` 只用于首次 bootstrap 或 DB 缺失兜底。
- 模板修改不回写已创建项目的 `agent-memory`。

### 8.2 `oc_workspace_memory`

表示目标项目工作区的一版记忆。

核心字段：

```text
project_id
version
status
freshness
memory_spec_version
memory_seed_template_id
agent_memory_seed_version
workspace_path

agent_memory_fingerprint
agent_memory_markdown
project_rule_fingerprint
project_rule_markdown

source_fingerprints_json
norm_claims_json
reality_claims_json
conflict_findings_json
workspace_norms_json
workspace_snapshot_json

failure_summary
failure_detail
generated_at
```

枚举：

```text
WorkspaceMemoryStatus:
CURRENT / ARCHIVED / FAILED

WorkspaceMemoryFreshness:
FRESH
STALE_BY_SOURCE_CHANGE
STALE_BY_SPEC_CHANGE
STALE_BY_RULE_CHANGE
STALE_BY_PROJECT_MEMORY_CHANGE
PLATFORM_MEMORY_DRIFT
```

索引建议：

```text
idx_workspace_memory_project(project_id)
idx_workspace_memory_project_status(project_id, status)
```

业务规则：

- 每个 `project_id` 只有一个 `CURRENT WorkspaceMemory`。
- 重建成功：新记录 `CURRENT`，旧 `CURRENT` 改 `ARCHIVED`。
- 重建失败：新增 `FAILED` 记录，旧 `CURRENT` 不变。
- 历史版本只在 DB 保留，工作区只保留 `platform-memory` latest。

### 8.3 `oc_requirement_design_context`

表示某个需求生成 PRD、概览设计、详细设计时使用的设计依据。

核心字段：

```text
project_id
requirement_id
workspace_memory_id
version
status
context_status

requirement_fingerprint
requirement_related_snapshot_json
requirement_conflict_report_json
design_basis
validation_result_json
source_fingerprints_json

failure_summary
failure_detail
generated_at
```

枚举：

```text
MemoryRecordStatus:
CURRENT / ARCHIVED / FAILED

RequirementDesignContextStatus:
READY / STALE / FAILED
```

业务规则：

- 每个 `Requirement` 一个 `CURRENT RequirementDesignContext`。
- 生成 PRD 前按需创建。
- 需求编辑、`WorkspaceMemory` 更新、`memorySpecVersion` 更新会使 context 失效。
- 历史上下文只在 DB 保留。
- `platform-memory/requirement-conflict-report.md` 和 `platform-memory/design-basis.md` 只保存 latest。

### 8.4 `oc_memory_job`

统一表示记忆初始化、刷新、重建、CodingTask 后回写、校验任务。

核心字段：

```text
project_id
requirement_id nullable
coding_task_id nullable
run_id nullable

job_type
status
trigger_source

previous_workspace_memory_id
new_workspace_memory_id
base_workspace_memory_id

idempotency_key
priority
retry_count
max_retry_count
next_retry_at
started_at
finished_at
failure_summary
failure_detail
```

枚举：

```text
MemoryJobType:
MEMORY_INITIALIZE
MEMORY_REBUILD
MEMORY_REFRESH_BY_SOURCE_CHANGE
MEMORY_REFRESH_BY_PROJECT_MEMORY_CHANGE
MEMORY_REFRESH_BY_RULE_CHANGE
MEMORY_REFRESH_BY_SPEC_CHANGE
MEMORY_UPDATE_AFTER_CODING_TASK
MEMORY_VALIDATE

MemoryJobStatus:
PENDING
RUNNING
SUCCEEDED
FAILED
CANCELLED

MemoryJobTriggerSource:
PROJECT_WORKSPACE_READY
MANUAL
AUTO_BEFORE_PRD
SOURCE_CHANGE
PROJECT_MEMORY_CHANGE
RULE_CHANGE
SPEC_CHANGE
CODING_TASK_SUCCEEDED
VALIDATION
```

索引建议：

```text
idx_memory_job_project_status(project_id, status)
idx_memory_job_next_retry(status, next_retry_at)
uk_memory_job_idempotency(idempotency_key)
```

并发规则：

- 同一个 `projectId` 同一时间只允许一个 active job。
- active job 指 `PENDING` 或 `RUNNING`。
- 第一版按 `projectId` 串行执行，不做复杂取消。
- 使用 `idempotency_key` 防重复投递。

## 9. 现有实体修改

### 9.1 `Project`

新增字段：

```text
memory_seed_template_id
```

规则：

- 创建项目时允许传入 `memorySeedTemplateId`，必须指向 `ACTIVE` 模板。
- 未传时解析并保存当时的全局默认模板 id，避免后续默认模板切换改变该项目的补齐来源。

### 9.2 `Requirement`

新增字段：

```text
design_context_id
memory_validation_status
memory_validation_result_json
```

### 9.3 `OverviewDesign`

新增字段：

```text
design_context_id
memory_validation_status
memory_validation_result_json
```

### 9.4 `DetailedDesign`

新增字段：

```text
design_context_id
memory_validation_status
memory_validation_result_json
```

校验状态枚举：

```text
MemoryValidationStatus:
NOT_RUN
PASSED
WARNING
FAILED
```

上下文引用规则：

- PRD 生成时写 `Requirement.design_context_id`。
- 概览设计默认使用 `Requirement.design_context_id`，生成时写 `OverviewDesign.design_context_id`。
- 详细设计默认使用 `OverviewDesign.design_context_id`，为空时回退 `Requirement.design_context_id`，生成时写 `DetailedDesign.design_context_id`。
- 确认和校验时使用文档自身引用的 context。

## 10. 后端新增组件

新增实体、DTO、DAO：

```text
MemorySeedTemplate / MemorySeedTemplateDto / MemorySeedTemplateDao
WorkspaceMemory / WorkspaceMemoryDto / WorkspaceMemoryDao
RequirementDesignContext / RequirementDesignContextDto / RequirementDesignContextDao
MemoryJob / MemoryJobDto / MemoryJobDao
```

新增服务：

```text
MemorySeedTemplateService
AgentMemoryTemplateService
WorkspaceMemoryService
WorkspaceMemoryScannerService
RequirementDesignContextService
MemoryJobService
PlatformMemoryWriterService
DesignContextPromptAssembler
DesignMemoryValidationService
```

### 10.1 `MemorySeedTemplateService`

职责：

- 管理平台可配置 seed 模板。
- 首次启动或 DB 缺失时，从 classpath `memory-seeds/default` bootstrap 默认模板。
- 查询当前 active default seed。
- 保存 draft 模板。
- 发布模板新版本。
- 将指定 `ACTIVE` 模板设为全局默认。
- 归档旧模板。
- 保证同一时间只有一个 `ACTIVE + is_default=true` 模板。

### 10.2 `AgentMemoryTemplateService`

职责：

- 优先按项目保存的 `memory_seed_template_id` 读取不可变 seed 版本，即使该版本后续已归档仍可用于该项目缺文件补齐；项目未绑定时解析全局默认 seed，并把解析结果写回项目。
- 从 seed 模板创建 `agent-memory` 四个初始文件。
- 新建项目工作区成功后自动创建。
- 已存在项目接入时缺文件补齐。
- 不覆盖已有文件。
- 不把平台推断结论写入 `agent-memory`。
- 记录 `agentMemorySeedVersion`。
- seed 文件写入时保留 `origin: platform_seed`、`memorySeedTemplateId`、`memorySeedTemplateCode` 和 `reviewStatus: unreviewed`。

### 10.3 `WorkspaceMemoryService`

职责：

- 查询当前 `WorkspaceMemory`。
- 初始化工作区记忆。
- 归档旧版本。
- 创建新版本。
- 标记 freshness。
- 提供 PRD 生成前的 `ensureCurrentWorkspaceMemory(projectId)`。

### 10.4 `WorkspaceMemoryScannerService`

职责：

- 读取 `agent-memory/**`。
- 读取项目规范文件。
- 扫描代码现状。
- 生成 `NormClaim`、`RealityClaim`、`ConflictFinding`。
- 生成 `WorkspaceNorms`、`WorkspaceSnapshot`。
- 记录 `sourceFingerprints`。

### 10.5 `RequirementDesignContextService`

职责：

- PRD 生成前按需准备需求上下文。
- 做需求相关补扫。
- 生成 `RequirementConflictReport`。
- 生成 `DesignBasis`。
- 保存 `RequirementDesignContext`。
- 写入 `platform-memory/requirement-conflict-report.md` 和 `platform-memory/design-basis.md` latest。

### 10.6 `MemoryJobService`

职责：

- 投递 memory job。
- 基于 `idempotency_key` 防重复。
- 按 `projectId` 串行执行。
- 处理 retry。
- 记录失败。
- 暴露 job 查询和重试能力。

### 10.7 `PlatformMemoryWriterService`

职责：

- 将 DB 结构化记忆渲染为 `platform-memory` Markdown。
- 写 `metadata.json`。
- 检测 `PLATFORM_MEMORY_DRIFT`。
- 重建失败时不覆盖 `platform-memory` latest。

### 10.8 `DesignContextPromptAssembler`

职责：

- 将 `RequirementDesignContext` 转成统一 prompt 段。
- 注入 PRD、概览设计、详细设计、CodingTask 执行 prompt。

固定 prompt 段：

```text
【项目自维护记忆】
【工作区规范 NormClaim】
【代码现状 RealityClaim】
【冲突与待确认 ConflictFinding】
【设计依据 DesignBasis】
```

### 10.9 `DesignMemoryValidationService`

职责：

- 生成后校验 PRD、概览设计、详细设计。
- 编辑后重新校验。
- 输出 `PASSED / WARNING / FAILED`。

校验结果：

```json
{
  "status": "PASSED",
  "findings": [
    {
      "severity": "HIGH",
      "message": "",
      "suggestedAction": ""
    }
  ]
}
```

第一版校验项：

- 是否包含必填章节。
- 是否引用了 `RequirementDesignContext`。
- 是否出现 forbidden choices。
- 是否遗漏 high severity `ConflictFinding`。
- 是否声称复用不存在的模块或接口。
- 是否缺少 source-backed 影响点。

## 11. 代码扫描策略

第一版采用“分层扫描 + 需求相关补扫”，不做无限全仓理解。

### 11.1 固定扫描层

初始化或重建 `WorkspaceMemory` 时扫描：

```text
项目根目录结构
package.json / pnpm-workspace.yaml / build.gradle / settings.gradle / pom.xml
main / app / bootstrap / routes / router
controller / api / endpoint / client / request
service / usecase / domain
entity / dto / vo / model / schema / migration
pages / routes / store / models / components / services
test 配置和关键测试目录
```

### 11.2 排除目录

默认跳过：

```text
.git
node_modules
dist
build
target
out
coverage
logs
tmp
.cache
.idea
.vscode
```

### 11.3 扫描预算

第一版配置化：

```text
maxFiles = 200
maxFileBytes = 128KB
maxTotalBytes = 5MB
maxDepth = 8
```

超限时记录：

```text
scanTruncated = true
truncatedReason = "maxFiles exceeded"
```

### 11.4 需求相关补扫

用户提出具体需求后，基于以下线索补扫：

```text
需求关键词
模块名
实体名
路由名
接口名
页面名
```

补扫结果进入 `RequirementDesignContext`，不直接修改全局 `WorkspaceSnapshot`，除非用户触发刷新工作区记忆。

### 11.5 代码图谱

规则：

- 有可用代码图谱时，优先用图谱查模块、调用、符号。
- 无图谱时，退回文件扫描。
- `WorkspaceSnapshot` 只存摘要和来源，不存大段源码。

## 12. MemoryJob 生命周期

### 12.1 触发点

```text
项目工作区建设成功
  -> MEMORY_INITIALIZE

用户手动刷新项目记忆
  -> MEMORY_REBUILD

PRD 生成前无 CURRENT WorkspaceMemory
  -> MEMORY_INITIALIZE 或 MEMORY_REBUILD，并等待完成

PRD 生成前 WorkspaceMemory 过期
  -> source/project-memory/rule 变化时自动刷新
  -> spec 版本变化时提示用户确认后刷新

CodingTask 成功完成
  -> MEMORY_UPDATE_AFTER_CODING_TASK

用户修改 agent-memory/**
  -> 第一版不实时监听，下次进入项目或生成前做指纹检查
```

### 12.2 失败策略

```text
MEMORY_INITIALIZE 失败：
项目可创建成功，但 PRD 生成不可继续，UI 显示初始化失败并允许重试。

MEMORY_REBUILD 失败：
旧 CURRENT 继续可用，UI 显示刷新失败。

MEMORY_UPDATE_AFTER_CODING_TASK 失败：
不影响 CodingTask 成功状态，只显示记忆回写失败并允许重试。

MEMORY_VALIDATE 失败：
不影响主流程，只记录 warning。
```

### 12.3 重试规则

```text
maxRetryCount = 3
retry delays = 1min / 5min / 15min
失败 3 次后进入 FAILED
用户可手动重试，创建新 job
```

### 12.4 幂等键

`MEMORY_UPDATE_AFTER_CODING_TASK` 幂等键：

```text
projectId + codingTaskId + runId + baseWorkspaceMemoryId
```

其他 job 幂等键按类型定义：

```text
MEMORY_INITIALIZE: projectId + MEMORY_INITIALIZE + workspaceReadyVersion
MEMORY_REBUILD: projectId + MEMORY_REBUILD + requestedAt/manual token
MEMORY_REFRESH_BY_PROJECT_MEMORY_CHANGE: projectId + agentMemoryFingerprint
MEMORY_REFRESH_BY_SOURCE_CHANGE: projectId + sourceFingerprintSetHash
```

## 13. 新建项目和已有项目初始化

### 13.1 新建项目

流程：

```text
1. 项目创建。
2. 工作区创建/克隆/脚手架完成。
3. 创建时校验显式选择的 `memorySeedTemplateId`；未选择时解析全局默认模板，并把最终模板 id 保存到 Project。
4. 工作区 ready 后立即从项目绑定 seed 创建 agent-memory 初始模板。
5. afterCommit 投递 MEMORY_INITIALIZE job。
6. 后台扫描 agent-memory、项目规范、代码现状。
7. 生成 WorkspaceMemory CURRENT。
8. 写 platform-memory latest。
```

空项目场景下，seed 记忆提供默认关注点，例如：

```text
默认要求生成 PRD 时先明确业务目标、范围、非目标、验收标准。
默认要求概览设计时先做模块划分和现有/新增模块映射。
默认要求详细设计时只围绕当前模块展开，并列出代码影响点和测试要点。
默认要求 CodingTask 后把新增事实写入 platform-memory，而不是自动改 agent-memory。
```

### 13.2 已有项目接入

流程：

```text
1. 定位目标工作区。
2. 检查 agent-memory。
3. 缺文件则优先从项目绑定 seed 补齐；项目未绑定时绑定当前全局默认 seed 后补齐。
4. 已有文件绝不覆盖。
5. 投递 MEMORY_INITIALIZE job。
6. 生成 WorkspaceMemory CURRENT。
7. 写 platform-memory latest。
```

已有项目中，seed 只用于补齐缺失文件，不作为高优先级事实压过已有规范和代码现状。

### 13.3 agent-memory 为空

允许继续初始化，但新建项目原则上不应出现完全空的 `agent-memory`，因为平台会先写入 seed 模板。

此时：

- 没有 reviewed 的 P0/P1 项目自维护输入。
- 可以使用 `origin=platform_seed` 的 P5S 默认基线。
- 平台基于项目原始规范和代码现状生成 `WorkspaceMemory`。
- 推断结果只写 DB 和 `platform-memory`。

## 14. RequirementDesignContext 生成和失效

### 14.1 生成时机

```text
用户点击生成 PRD
  -> ensure WorkspaceMemory CURRENT
  -> 做需求相关补扫
  -> 生成 RequirementConflictReport
  -> 生成 DesignBasis
  -> 保存 RequirementDesignContext CURRENT
  -> 开始 PRD 生成
```

### 14.2 失效条件

```text
Requirement.title / description 改变
引用的 WorkspaceMemory 不再是 CURRENT
agent-memory 变化导致 WorkspaceMemory 刷新
CodingTask 后 WorkspaceMemory 更新，且需求仍处于未完成状态
memorySpecVersion 变化
用户手动要求重建上下文
```

### 14.3 失效处理

- 可用于生成：`status=CURRENT && context_status=READY`。
- 历史查看：`status=ARCHIVED`。
- 失败记录：`status=FAILED`。
- 已确认文档不自动重写。
- 未确认文档遇到 context 失效时提示重新生成。
- 第一版 stale context 禁止确认。

## 15. PRD / 概览设计 / 详细设计接入

### 15.1 PRD

`RequirementAgentService.buildPrdPrompt(...)` 注入：

```text
WorkspaceNorms
WorkspaceSnapshot
RequirementConflictReport
DesignBasis
```

PRD 必须包含：

```text
与现有系统关系
复用/扩展/重构范围
冲突与待确认项
非目标
验收标准
规范符合性说明
```

生成完成后：

```text
Requirement.design_context_id = 当前 context id
执行 DesignMemoryValidationService
保存 memory_validation_status
保存 memory_validation_result_json
```

### 15.2 概览设计

`OverviewDesignAgentService.buildOverviewPrompt(...)` 注入同一版 `RequirementDesignContext`。

概览设计必须包含：

```text
模块清单表
模块与现有代码映射
新增/复用/调整模块说明
架构影响
接口/页面/数据影响范围
风险与待确认
```

生成完成后：

```text
OverviewDesign.design_context_id = 实际使用的 context id
执行 DesignMemoryValidationService
保存 memory_validation_status
保存 memory_validation_result_json
```

### 15.3 详细设计

`DetailedDesignAgentService.buildDetailedPrompt(...)` 注入同一版 `RequirementDesignContext` 和当前模块相关补扫摘要。

详细设计必须包含：

```text
当前模块目标
职责边界
现有代码影响点
新增/修改文件建议
接口设计
数据模型影响
页面/组件设计
状态流转
异常处理
测试要点
兼容和回归风险
```

生成完成后：

```text
DetailedDesign.design_context_id = 实际使用的 context id
执行 DesignMemoryValidationService
保存 memory_validation_status
保存 memory_validation_result_json
```

### 15.4 确认拦截

PRD、概览设计、详细设计确认接口统一检查：

```text
design_context_id 存在
memory_validation_status != FAILED
context 不是 STALE
```

第一版规则：

- `FAILED` 阻止确认。
- `WARNING` 允许确认但前端提示。
- `STALE context` 禁止确认。
- 手动编辑后必须重新校验。

## 16. CodingTask 后记忆回写

### 16.1 触发

CodingTask 成功完成后投递：

```text
MEMORY_UPDATE_AFTER_CODING_TASK
```

失败 run 不触发项目记忆回写。

### 16.2 输入

```text
projectId
codingTaskId
runId
baseWorkspaceMemoryId
任务标题/描述
执行前后变更文件列表
git diff 摘要
运行结果
失败/修复记录
```

优先使用：

```text
git diff --name-only
git diff --stat
必要时读取变更文件片段
```

### 16.3 输出

更新平台侧记忆：

```text
DB 新 WorkspaceMemory 版本
platform-memory/workspace-snapshot.md
platform-memory/decision-log.md
platform-memory/metadata.json
```

不更新：

```text
agent-memory/**
```

### 16.4 更新类型

```text
Reality Updates:
代码现状变化，例如新增接口、新增页面、新增实体。

Decision Suggestions:
建议写入 agent-memory 的长期规则或决策。

Conflict Updates:
实现结果和原设计、项目记忆、代码现状仍存在的不一致。
```

### 16.5 降级全量重建

触发条件：

```text
变更文件数 > 50
涉及构建配置 / 框架入口 / 路由根 / 数据库迁移大量变化
baseWorkspaceMemoryId 不是当前 CURRENT
增量分析失败
```

降级为：

```text
MEMORY_REBUILD
```

## 17. API 设计

第一版提供 seed 模板配置、项目级记忆管理和需求级上下文查看。

### 17.1 Seed 模板 API

```text
GET  /api/memory/seed-templates
GET  /api/memory/seed-templates/active-default
GET  /api/memory/seed-templates/{id}
POST /api/memory/seed-templates
POST /api/memory/seed-templates/{id}/save-draft
POST /api/memory/seed-templates/{id}/publish
POST /api/memory/seed-templates/{id}/set-default
POST /api/memory/seed-templates/{id}/archive
POST /api/memory/seed-templates/bootstrap-default
```

规则：

- `save-draft` 只保存草稿，不影响新项目。
- `publish` 发布新版本，使其成为可选 `ACTIVE` 模板，但不自动设为默认。
- `set-default` 仅接受 `ACTIVE` 模板，并以事务方式切换全局唯一默认模板。
- `archive` 只能归档非当前默认模板；当前默认模板必须先通过 `set-default` 切换默认后才能归档。
- `bootstrap-default` 用于 DB 缺失时从 classpath 重新导入默认模板，正常启动时由服务自动保证。

### 17.2 项目级 API

```text
GET  /api/memory/workspace/current?projectId=xxx
GET  /api/memory/workspace/history?projectId=xxx
POST /api/memory/workspace/initialize?projectId=xxx
POST /api/memory/workspace/rebuild?projectId=xxx
GET  /api/memory/jobs?projectId=xxx
POST /api/memory/jobs/{jobId}/retry
```

### 17.3 需求级 API

```text
GET  /api/memory/requirement-context/current?requirementId=xxx
POST /api/memory/requirement-context/prepare?requirementId=xxx
```

### 17.4 API 实现文件

新增：

```text
MemorySeedTemplateApi / MemorySeedTemplateController
MemoryWorkspaceApi / MemoryWorkspaceController
MemoryRequirementContextApi / MemoryRequirementContextController
MemoryJobApi / MemoryJobController
```

第一版不提供编辑 `agent-memory` 的 API，但提供配置平台 seed 模板的 API。

## 18. 前端设计

前端不新增 memory mock。

### 18.1 平台 Seed 模板配置入口

在平台设置页增加 seed 模板配置区域。

展示：

```text
当前 active default 模板
全部已发布可选模板
模板 code / name / version / status
四个模板文件内容预览
草稿保存
发布模板
设为默认模板
归档旧模板
从内置 default bootstrap
```

编辑范围：

```text
project-memory.md 模板内容
memory-rules.md 模板内容
decisions.md 模板内容
modules.md 模板内容
```

规则：

- 修改 seed 模板只影响之后新建或缺文件补齐的项目。
- 不影响已经生成的项目 `agent-memory`。
- 发布模板需要生成新版本。
- 发布后模板进入可选列表，但只有单独执行“设为默认”才会改变全局默认模板。
- 项目创建表单允许选择一个已发布模板，并提示未选择时使用全局默认模板。
- UI 需要提示“已存在项目不会被自动覆盖”。

### 18.2 新增项目记忆入口

在项目工作区增加 tab：

```text
项目记忆
```

展示：

```text
WorkspaceMemory 当前状态
WorkspaceMemory 版本号
freshness
最近生成时间
agent-memory 指纹状态
platform-memory 写入状态
WorkspaceNorms 摘要
WorkspaceSnapshot 摘要
ConflictFindings 摘要
MemoryJob 列表
手动刷新 / 重试按钮
```

### 18.3 需求工作区状态条

在 PRD、概览设计、详细设计页面顶部显示：

```text
设计依据：WorkspaceMemory vX / RequirementContext vY
上下文状态：READY / STALE / FAILED
校验状态：PASSED / WARNING / FAILED / NOT_RUN
```

行为：

- `validation FAILED`：显示失败项，禁用确认按钮。
- `validation WARNING`：显示提示，确认按钮可用。
- `context STALE`：禁用确认按钮，提示重建上下文并重新生成或重新校验。

### 18.4 新增前端 service

```text
frontend/src/services/memorySeedTemplate.js
frontend/src/services/memoryWorkspace.js
frontend/src/services/memoryRequirementContext.js
frontend/src/services/memoryJob.js
```

### 18.5 修改页面

实现时按当前前端结构定位，预计涉及：

```text
Settings / 平台设置页
ProjectDetail / 项目工作区 tabs
RequirementWorkspace
PrdPanel
OverviewDesignPanel
DetailedDesignPanel
```

### 18.6 MOCK 行为

规则：

- 保留现有 MSW/mock 基础设施。
- 不为项目记忆或 seed 模板配置新增 mock db / handlers。
- memory service 全部调用真实后端 API。
- `MOCK=yes` 时项目记忆入口显示“记忆能力依赖后端服务”或后端不可用空态。
- 现有业务 mock 不因本次设计扩展。

## 19. 后端实现分期

本计划按四个阶段实施。

### Phase 1：记忆基础设施

目标：

- 先打通表结构、模板创建、platform-memory 写入、MemoryJob 投递和状态流转。

任务：

```text
1. 新增枚举。
2. 新增 WorkspaceMemory / RequirementDesignContext / MemoryJob 实体。
3. 新增 MemorySeedTemplate 实体。
4. 新增 DTO / DAO。
5. 新增 V24__memory_foundation.sql。
6. 修改 Project，增加 memory_seed_template_id；修改 Requirement / OverviewDesign / DetailedDesign，增加 design_context_id 和 validation 字段。
7. 新增平台 default seed 记忆模板资源。
8. 实现 MemorySeedTemplateService，支持 bootstrap、draft、publish、set-default、archive 和项目模板解析。
9. 实现 AgentMemoryTemplateService，从项目绑定 seed 创建 agent-memory 文件。
10. 实现 PlatformMemoryWriterService 的基础写入能力。
11. 实现 MemoryJobService 的投递、查询、重试、状态流转。
12. 接入项目工作区 ready 后的 MEMORY_INITIALIZE 投递点。
13. 新增基础 Memory API 和 Controller。
```

验收：

```text
数据库迁移成功。
四张表可创建。
classpath default seed 可 bootstrap 到 oc_memory_seed_template。
平台可配置多个已发布 seed 模板，并保证全局只有一个默认模板。
项目创建时可选择已发布模板，未选择时绑定全局默认模板。
Project 工作区 ready 后能投递 MEMORY_INITIALIZE job。
agent-memory 四个 seed 模板能创建，且不覆盖已有文件。
agent-memory seed 文件包含 origin=platform_seed、memorySeedTemplateId、agentMemorySeedVersion、reviewStatus=unreviewed。
platform-memory 目录可写。
MemoryJob 可查询、可重试。
```

测试：

```text
MemorySeedTemplateService 单测。
WorkspaceMemoryService 单测。
AgentMemoryTemplateService 单测。
PlatformMemoryWriterService 单测。
MemoryJobService 单测。
项目模板选择、默认模板回退及已归档绑定模板补齐测试。
全局默认模板切换并发测试。
WorkspaceMemory / RequirementDesignContext CURRENT 唯一约束并发测试。
./gradlew :sei-online-code-service:compileJava
./gradlew :sei-online-code-service:compileTestJava
```

### Phase 2：WorkspaceMemory 生成

目标：

- 能从工作区生成可用的 `WorkspaceMemory CURRENT`。

任务：

```text
1. 实现 WorkspaceMemoryScannerService。
2. 读取 agent-memory 四文件。
3. 读取 AGENTS.md / CLAUDE.md / README.md / docs/** / 构建配置。
4. 执行固定范围代码扫描。
5. 生成 NormClaim / RealityClaim / ConflictFinding。
6. 生成 WorkspaceNorms / WorkspaceSnapshot。
7. 实现 sourceFingerprints / agentMemoryFingerprint / projectRuleFingerprint。
8. 实现 freshness 检查。
9. MEMORY_INITIALIZE job 执行生成 WorkspaceMemory。
10. 写 platform-memory latest。
11. 前端项目记忆 tab 展示当前状态、摘要和 job。
```

验收：

```text
MEMORY_INITIALIZE 能生成 CURRENT WorkspaceMemory。
reviewed/origin=project 的 agent-memory 优先级高于项目规范。
origin=platform_seed 且 reviewStatus=unreviewed 的 seed 内容只作为默认基线。
代码事实进入 RealityClaim，不被项目记忆覆盖。
项目记忆与代码事实冲突进入 ConflictFinding。
platform-memory latest 文件生成。
memory 页面能展示状态、规范摘要、快照摘要、job。
```

测试：

```text
WorkspaceMemoryScannerService 单测。
优先级覆盖测试。
seed 默认基线不覆盖已有项目规范测试。
代码事实冲突测试。
platform-memory 渲染测试。
frontend pnpm build。
```

### Phase 3：RequirementDesignContext 接入文档生成

目标：

- PRD、概览设计、详细设计、CodingTask 全部消费需求级设计上下文。

任务：

```text
1. 实现 RequirementDesignContextService。
2. PRD 生成前 ensure WorkspaceMemory CURRENT。
3. PRD 生成前生成 RequirementDesignContext。
4. 实现 DesignContextPromptAssembler。
5. RequirementAgentService 注入 design context。
6. OverviewDesignAgentService 注入 design context。
7. DetailedDesignAgentService 注入 design context。
8. CodingTaskExecutionService 注入 design context。
9. 实现 DesignMemoryValidationService。
10. 生成后保存 memory_validation_status 和 result。
11. confirmPrd / confirmOverview / confirmDetailedDesign 增加拦截。
12. 前端需求工作区状态条展示 context 和 validation。
```

验收：

```text
PRD 生成前会生成 RequirementDesignContext。
PRD / 概览 / 详细设计记录 design_context_id。
Prompt 中包含 design context。
生成后执行校验。
FAILED 阻止确认。
WARNING 允许确认。
STALE context 阻止确认。
手动编辑后重新校验。
```

测试：

```text
RequirementDesignContextService 单测。
DesignContextPromptAssembler 单测。
DesignMemoryValidationService 单测。
Requirement/Overview/Detailed confirm 拦截测试。
后端服务测试。
frontend pnpm build。
```

### Phase 4：CodingTask 后记忆回写

目标：

- CodingTask 成功后，根据代码结果增量更新平台记忆。

任务：

```text
1. CodingTask 成功后投递 MEMORY_UPDATE_AFTER_CODING_TASK。
2. 实现变更文件收集。
3. 实现 diff 摘要采集。
4. 实现增量 Reality Updates。
5. 实现 Generated Suggestions 写入 decision-log。
6. 实现 Conflict Updates。
7. 增量成功后创建新 WorkspaceMemory CURRENT。
8. 旧 WorkspaceMemory ARCHIVED。
9. 大范围变更降级 MEMORY_REBUILD。
10. 回写失败不影响 CodingTask 成功状态。
```

验收：

```text
CodingTask 成功后投递 MEMORY_UPDATE_AFTER_CODING_TASK。
增量更新创建新 WorkspaceMemory CURRENT。
旧 WorkspaceMemory ARCHIVED。
回写失败不影响 CodingTask 成功。
Generated Suggestions 写入 platform-memory/decision-log.md。
大范围变更降级 MEMORY_REBUILD。
```

测试：

```text
MemoryJob 执行器测试。
CodingTaskExecutionService 投递测试。
增量回写测试。
降级 rebuild 测试。
失败不影响 coding task 测试。
```

## 20. 前端实现分期

前端跟随后端阶段落地。

### Phase 1 前端

```text
1. 新增 memory service 文件。
2. 新增 memorySeedTemplate service。
3. 平台设置页增加 seed 模板配置入口。
4. 增加 MemoryJob 查询和重试调用。
5. 项目创建表单增加已发布 seed 模板选择，未选择时明确显示将使用全局默认模板。
6. 先不暴露完整项目记忆页面，可预留接口类型。
```

### Phase 2 前端

```text
1. 项目工作区新增“项目记忆”tab。
2. 展示 WorkspaceMemory 状态和摘要。
3. 展示 MemoryJob 列表。
4. 支持手动刷新和重试。
5. MOCK=yes 时展示后端依赖提示，不接 memory mock。
```

### Phase 3 前端

```text
1. PRD panel 增加设计依据状态条。
2. OverviewDesign panel 增加设计依据状态条。
3. DetailedDesign panel 增加设计依据状态条。
4. validation FAILED 禁用确认按钮。
5. context STALE 禁用确认按钮。
6. WARNING 显示提示但允许确认。
```

### Phase 4 前端

```text
1. 项目记忆页展示 CodingTask Updates。
2. 展示 Generated Suggestions。
3. 展示最近一次 MEMORY_UPDATE_AFTER_CODING_TASK 状态。
```

## 21. 迁移计划

新增迁移：

```text
V24__memory_foundation.sql
```

内容：

```text
1. 创建 oc_memory_seed_template。
2. 创建 oc_workspace_memory。
3. 创建 oc_requirement_design_context。
4. 创建 oc_memory_job。
5. 为 oc_project 增加 memory_seed_template_id。
6. 为 oc_requirement 增加 design_context_id / memory_validation_status / memory_validation_result_json。
7. 为 oc_overview_design 增加 design_context_id / memory_validation_status / memory_validation_result_json。
8. 为详细设计表增加 design_context_id / memory_validation_status / memory_validation_result_json。
9. oc_workspace_memory 包含 memory_spec_version、memory_seed_template_id 和 agent_memory_seed_version。
10. 创建索引，并用数据库唯一约束保证每个 project/requirement 只有一个 CURRENT；service 负责在同一事务中归档旧 CURRENT 后写入新 CURRENT。
```

注意：

- 上线前清理历史 PRD、概览设计和详细设计数据，不提供历史文档 bypass。
- 新生成文档的 `memory_validation_status` 初始为 `NOT_RUN`，生成后必须完成校验。
- 数据库唯一约束的具体 DDL 必须结合实际数据库能力实现并由并发测试验证，不能只依赖普通 `(project_id, status)` 唯一索引，否则会错误限制多个 `ARCHIVED` 版本。

## 22. 关键兼容策略

### 22.1 历史数据

上线前清理历史 PRD、概览设计和详细设计数据，因此第一版不实现历史文档兼容或 bypass 分支。部署检查必须确认清理范围、外键依赖和回滚备份；清理后所有新文档统一执行 context 生成、记忆校验和确认拦截。

### 22.2 工作区不存在

如果 `Project.workspacePath` 为空或不可访问：

```text
MEMORY_INITIALIZE 失败。
记录 failure_summary。
PRD 生成阻塞。
UI 提示工作区不可用。
```

### 22.3 agent-memory 缺失

```text
从项目绑定 seed 自动补齐缺失模板；项目未绑定时先绑定当前全局默认 seed。
不覆盖已有文件。
继续初始化 WorkspaceMemory。
```

### 22.4 platform-memory 写入失败

```text
DB 写入成功但 platform-memory 写入失败：
WorkspaceMemory 可 CURRENT。
记录 warning 或 writer failure。
UI 显示 platform-memory 写入失败。
```

`platform-memory` 写入失败不阻断 DB 主流程；必须记录可重试 job，并持续重试直至 latest 镜像最终写入成功或进入需要人工处理的终态。

## 23. 全局验证命令

后端：

```bash
./gradlew :sei-online-code-service:compileJava
./gradlew :sei-online-code-service:compileTestJava
```

前端：

```bash
cd frontend
pnpm build
```

通用：

```bash
git diff --check
```

部署与专项验证：

```text
1. 历史文档清理脚本在备份数据上演练，验证外键依赖、影响行数和回滚恢复。
2. 并发创建 WorkspaceMemory，验证每个 project_id 最多一个 CURRENT。
3. 并发创建 RequirementDesignContext，验证每个 requirement_id 最多一个 CURRENT。
4. 并发切换 seed 默认模板，验证全局最多一个 ACTIVE + is_default=true。
5. 模拟 platform-memory 首次写入失败，验证 job 重试后 latest 镜像最终写入且 WorkspaceMemory 始终保持 CURRENT。
```

## 24. 分阶段交付检查清单

### Phase 1 checklist

- [ ] V24 迁移创建四张表。
- [ ] 四个新实体和 DAO 可编译。
- [ ] `MemorySeedTemplate` 实体、DAO、DTO 可编译。
- [ ] `Project.memory_seed_template_id` 可持久化，创建项目可选择 `ACTIVE` 模板，未选择时绑定全局默认模板。
- [ ] 四个新 DTO 可用于 API。
- [ ] `Requirement` / `OverviewDesign` / `DetailedDesign` 新字段完成。
- [ ] 平台 default seed 记忆模板资源完成。
- [ ] classpath default seed 可 bootstrap 到 DB。
- [ ] seed 模板支持 draft / publish / set-default / archive，且并发切换后全局只有一个默认模板。
- [ ] `agent-memory` 四个 seed 模板创建且不覆盖。
- [ ] 项目绑定模板归档后，缺文件补齐仍使用该项目原绑定版本。
- [ ] seed front matter 写入 `origin: platform_seed`、`memorySeedTemplateId`、`agentMemorySeedVersion`、`reviewStatus: unreviewed`。
- [ ] `MemoryJob` 可投递、查询、重试。
- [ ] 项目工作区 ready 后投递初始化 job。
- [ ] 后端编译和 focused tests 通过。

### Phase 2 checklist

- [ ] 扫描 `agent-memory`。
- [ ] 扫描项目规范文件。
- [ ] 扫描代码固定范围。
- [ ] 生成 `NormClaim`。
- [ ] 生成 `RealityClaim`。
- [ ] 生成 `ConflictFinding`。
- [ ] 写 `WorkspaceMemory CURRENT`。
- [ ] 写 `platform-memory` latest。
- [ ] 前端项目记忆 tab 可查看状态。
- [ ] 后端和前端构建通过。

### Phase 3 checklist

- [ ] PRD 前生成 `RequirementDesignContext`。
- [ ] PRD prompt 注入 design context。
- [ ] 概览 prompt 注入 design context。
- [ ] 详细设计 prompt 注入 design context。
- [ ] CodingTask prompt 注入 design context。
- [ ] 三类文档记录 `design_context_id`。
- [ ] 生成后校验。
- [ ] FAILED 阻止确认。
- [ ] STALE context 阻止确认。
- [ ] 前端状态条展示。
- [ ] 后端和前端构建通过。

### Phase 4 checklist

- [ ] CodingTask 成功后投递记忆回写 job。
- [ ] 收集变更文件和 diff 摘要。
- [ ] 增量更新 `WorkspaceMemory`。
- [ ] 旧版本归档。
- [ ] 写 `Generated Suggestions`。
- [ ] 大范围变更降级 rebuild。
- [ ] 回写失败不影响 CodingTask 成功。
- [ ] focused tests 通过。

## 25. 审核结论与已确认决策

本节是全文的最终实施口径；前文设计、分期、验收和测试均须与以下决策保持一致：

1. 上线前清理历史 PRD、概览设计和详细设计数据，不实现历史文档 bypass memory validation。
2. `platform-memory` 写入失败不阻断 `WorkspaceMemory CURRENT`，但必须通过可重试 job 保证最终写入。
3. 每个 `project_id` 只有一个 `CURRENT WorkspaceMemory`、每个 `requirement_id` 只有一个 `CURRENT RequirementDesignContext`，由数据库唯一约束兜底，并由 service 事务维护版本切换。
4. 代码扫描预算默认采用 `200 files / 128KB each / 5MB total / depth 8`，并保持配置化。
5. `MEMORY_INITIALIZE` 在项目工作区 ready 后立即投递执行。
6. 第一版不增加 seed 内容从 `unreviewed` 升级为 `reviewed` 的前端确认入口，也不提供 `agent-memory` 在线编辑；仅保留 front matter 能力，由用户或项目 agent 在工作区内维护。
7. 平台允许同时存在多个已发布 `ACTIVE` 可选模板，但全局只能有一个 `ACTIVE + is_default=true` 默认模板。项目创建时可显式选择一个已发布模板，未选择时绑定全局默认模板；项目后续缺文件补齐沿用其已绑定模板。
