# API Contract — Requirement-Driven Flow

> 本契约定义需求驱动重构后的前后端接口：
> `Project -> Requirement(PRD) -> OverviewDesign -> DetailedDesign -> CodingTask -> Run`。
> 旧 `Plan/Spec/FeatureDesign/Task` 接口保留但不进入新 UI 主路径。

## 1. Domain Model & Status Enums

### 1.1 Project

| Field | Type | Description |
|-------|------|-------------|
| id | string | UUID |
| name | string | 项目名称 |
| design | string | 用户输入的原始项目描述（仅作元数据） |
| gitUrl | string? | 项目 Git 地址 |
| workspacePath | string | 项目工作区路径；为空时按平台配置自动生成 |
| autoRunCodingTask | boolean | 确认详细设计后是否自动执行编码任务 |
| state | string | 兼容旧生命周期状态，新流程不直接依赖 |
| currentSpecId | string? | 兼容旧字段 |

### 1.2 RequirementStatus

```
PRD_GENERATING -> PRD_REVIEW -> PRD_CONFIRMED
                 -> FAILED
```

- `PRD_GENERATING`: 已创建需求，PRD 代理生成中。
- `PRD_REVIEW`: PRD 生成完成，待用户确认/编辑/重生成。
- `PRD_CONFIRMED`: PRD 已确认，下游概览设计已生成或生成中；PRD 冻结。
- `FAILED`: PRD 生成失败。

### 1.3 OverviewDesignStatus

```
GENERATING -> DRAFT -> CONFIRMED
          -> FAILED
```

- `GENERATING`: 概览设计生成中。
- `DRAFT`: 生成完成，待确认/编辑/重生成。
- `CONFIRMED`: 已确认，已拆分为 feature 级详细设计；概览设计冻结。
- `FAILED`: 生成失败。

### 1.4 DetailedDesignStatus

```
GENERATING -> REVIEW -> CONFIRMED
          -> FAILED
```

- `GENERATING`: 详细设计生成中。
- `REVIEW`: 生成完成，待确认/编辑/重生成。
- `CONFIRMED`: 已确认，已创建 CodingTask；详细设计冻结。
- `FAILED`: 生成失败。

### 1.5 CodingTaskStatus

```
PENDING -> RUNNING -> SUCCEEDED
                    -> FAILED
                    -> CANCELLED
        -> STALE   (详细设计版本升级后旧任务标记)
```

- `PENDING`: 等待执行。
- `RUNNING`: 正在执行。
- `SUCCEEDED`: 最近一次 Run 成功。
- `FAILED`: 最近一次 Run 失败。
- `CANCELLED`: 已取消。
- `STALE`: 绑定的详细设计版本已升级，任务不再可执行。

### 1.6 RunStatus

沿用现有 `RunState`：`RUNNING | SUCCEEDED | FAILED | CANCELLED`。

### 1.7 Freeze Rules

1. `Requirement` 进入 `PRD_CONFIRMED` 后，PRD 不可编辑、不可重生成。
2. `OverviewDesign` 进入 `CONFIRMED` 后，概览设计不可编辑、不可重生成；但此时仍允许重新生成尚未确认的 `DetailedDesign`。
3. `DetailedDesign` 进入 `CONFIRMED` 后，详细设计不可编辑、不可重生成。
4. 上游冻结后，只允许对下游未确认阶段进行操作或发起 rerun。

## 2. DTOs

### 2.1 ProjectDto

```json
{
  "id": "string",
  "name": "string",
  "design": "string",
  "gitUrl": "string?",
  "workspacePath": "string?",
  "autoRunCodingTask": "boolean",
  "state": "string",
  "currentSpecId": "string?",
  "createdDate": "datetime",
  "lastEditedDate": "datetime"
}
```

### 2.2 RequirementDto

```json
{
  "id": "string",
  "projectId": "string",
  "title": "string",
  "description": "string",
  "status": "PRD_GENERATING | PRD_REVIEW | PRD_CONFIRMED | FAILED",
  "prdVersion": "integer",
  "prdContent": "object | string",
  "failureCode": "string?",
  "failureSummary": "string?",
  "createdDate": "datetime",
  "lastEditedDate": "datetime"
}
```

### 2.3 OverviewDesignDto

```json
{
  "id": "string",
  "projectId": "string",
  "requirementId": "string",
  "status": "GENERATING | DRAFT | CONFIRMED | FAILED",
  "version": "integer",
  "content": {
    "modules": [
      {
        "moduleId": "string",
        "moduleTitle": "string",
        "features": [
          {
            "featureId": "string",
            "featureTitle": "string"
          }
        ]
      }
    ]
  },
  "failureSummary": "string?",
  "createdDate": "datetime",
  "lastEditedDate": "datetime"
}
```

### 2.4 DetailedDesignDto

```json
{
  "id": "string",
  "projectId": "string",
  "requirementId": "string",
  "overviewDesignId": "string",
  "moduleId": "string",
  "moduleTitle": "string",
  "featureId": "string",
  "featureTitle": "string",
  "status": "GENERATING | REVIEW | CONFIRMED | FAILED",
  "version": "integer",
  "content": "object | string",
  "failureSummary": "string?",
  "createdDate": "datetime",
  "lastEditedDate": "datetime"
}
```

### 2.5 CodingTaskDto

```json
{
  "id": "string",
  "projectId": "string",
  "requirementId": "string",
  "detailedDesignId": "string",
  "detailedDesignVersion": "integer",
  "status": "PENDING | RUNNING | SUCCEEDED | FAILED | CANCELLED | STALE",
  "title": "string",
  "description": "string",
  "fileScope": ["string"],
  "failureSummary": "string?",
  "createdDate": "datetime",
  "lastEditedDate": "datetime"
}
```

### 2.6 RunDto

```json
{
  "id": "string",
  "codingTaskId": "string",
  "runNo": "integer",
  "triggerSource": "MANUAL | AUTO | RERUN",
  "state": "RUNNING | SUCCEEDED | FAILED | CANCELLED",
  "userPrompt": "string?",
  "failureSummary": "string?",
  "failureReason": "string?",
  "worktreePath": "string?",
  "startedDate": "datetime",
  "finishedDate": "datetime"
}
```

## 3. Endpoints

### 3.1 Project (extensions)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/project/save` | 创建/编辑项目；新增 `gitUrl`、`workspacePath`、`autoRunCodingTask`；创建项目不触发规划代理。 |
| GET | `/project/findOne` | 返回包含新字段的 ProjectDto。 |
| POST | `/project/findByPage` | 分页查询。 |

### 3.2 Requirement

Base path: `requirement`

| Method | Path | Description |
|--------|------|-------------|
| POST | `/requirement/save` | 创建需求；status=`PRD_GENERATING`，并异步启动 `prd-agent`。 |
| GET | `/requirement/findOne` | 查询需求详情。 |
| POST | `/requirement/findByPage` | 按 `projectId` 分页查询需求列表。 |
| POST | `/requirement/{id}/regeneratePrd` | 重生成 PRD；仅 `PRD_REVIEW`/`FAILED` 允许；需 `prompt`。 |
| POST | `/requirement/{id}/editPrd` | 编辑 PRD 内容；仅 `PRD_REVIEW` 允许。 |
| POST | `/requirement/{id}/confirmPrd` | 确认 PRD；status 变为 `PRD_CONFIRMED`，并创建 `OverviewDesign(GENERATING)`。 |

### 3.3 Overview Design

Base path: `overview-design`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/overview-design/findOne?requirementId=` | 按需求查询当前概览设计。 |
| POST | `/overview-design/{id}/regenerate` | 重生成；仅 `DRAFT`/`FAILED` 允许；需 `prompt`。 |
| POST | `/overview-design/{id}/edit` | 编辑 content；仅 `DRAFT` 允许。 |
| POST | `/overview-design/{id}/confirm` | 确认；status=`CONFIRMED`，按 `content.modules[].features[]` 拆分创建 `DetailedDesign(GENERATING)`。 |

### 3.4 Detailed Design

Base path: `detailed-design`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/detailed-design/findOne?id=` | 查询单条。 |
| GET | `/detailed-design/findByOverview?overviewDesignId=` | 查询某概览设计下的全部详细设计。 |
| POST | `/detailed-design/{id}/regenerate` | 重生成；仅 `REVIEW`/`FAILED` 允许；需 `prompt`。 |
| POST | `/detailed-design/{id}/edit` | 编辑 content；仅 `REVIEW` 允许。 |
| POST | `/detailed-design/{id}/confirm` | 确认；创建一条 `CodingTask(PENDING)`；若 `Project.autoRunCodingTask=true` 则触发执行。 |
| POST | `/detailed-design/batchConfirm` | 批量确认 ID 列表；每条创建对应 CodingTask。 |

### 3.5 Coding Task

Base path: `coding-task`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/coding-task/findOne?id=` | 查询任务。 |
| POST | `/coding-task/findByPage` | 分页查询；支持 `projectId`、`requirementId`、`status`。 |
| POST | `/coding-task/{id}/run` | 首次/手动运行；首次 `userPrompt` 可空。同一任务同时只能有一个 `RUNNING` Run。 |
| POST | `/coding-task/{id}/rerun` | 重跑；必须提供非空 `rerunPrompt`；系统把上一次失败原因拼入 prompt。 |
| POST | `/coding-task/{id}/cancel` | 取消当前运行中的 Run。 |

### 3.6 Run

Base path: `run`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/run/findByCodingTask?codingTaskId=` | 查询某 CodingTask 的全部 Run 历史，按 `runNo` 升序。 |
| GET | `/run/findOne?id=` | 查询 Run 详情。 |

## 4. Request DTOs

### 4.1 SaveProjectRequest (extends ProjectDto)

新增字段：

- `gitUrl?: string`
- `workspacePath?: string`
- `autoRunCodingTask?: boolean` (default false)

### 4.2 SaveRequirementRequest

```json
{
  "id": "string?",
  "projectId": "string",
  "title": "string",
  "description": "string"
}
```

### 4.3 RegeneratePrdRequest

```json
{
  "prompt": "string"
}
```

### 4.4 EditPrdRequest

```json
{
  "prdContent": "object | string"
}
```

### 4.5 RegenerateOverviewRequest

```json
{
  "prompt": "string"
}
```

### 4.6 EditOverviewRequest

```json
{
  "content": "OverviewDesignContent"
}
```

### 4.7 RegenerateDetailedDesignRequest

```json
{
  "prompt": "string"
}
```

### 4.8 EditDetailedDesignRequest

```json
{
  "content": "object | string"
}
```

### 4.9 BatchConfirmDetailedDesignRequest

```json
{
  "ids": ["string"]
}
```

### 4.10 RunCodingTaskRequest

```json
{
  "userPrompt": "string?"
}
```

### 4.11 RerunCodingTaskRequest

```json
{
  "rerunPrompt": "string"  // required, non-blank
}
```

## 5. State Transitions

### 5.1 Requirement / PRD

```
+ PRD_GENERATING (save)
  |- agent success -> PRD_REVIEW
  |- agent failure -> FAILED
+ PRD_REVIEW
  |- confirmPrd -> PRD_CONFIRMED (freeze)
  |- regeneratePrd -> PRD_GENERATING
  |- editPrd -> PRD_REVIEW
+ PRD_CONFIRMED
  |- frozen
+ FAILED
  |- regeneratePrd -> PRD_GENERATING
```

### 5.2 Overview Design

```
+ GENERATING (created by confirmPrd)
  |- agent success -> DRAFT
  |- agent failure -> FAILED
+ DRAFT
  |- confirm -> CONFIRMED (freeze, spawn detailed designs)
  |- regenerate -> GENERATING
  |- edit -> DRAFT
+ CONFIRMED
  |- frozen
+ FAILED
  |- regenerate -> GENERATING
```

### 5.3 Detailed Design

```
+ GENERATING (created by confirm overview)
  |- agent success -> REVIEW
  |- agent failure -> FAILED
+ REVIEW
  |- confirm -> CONFIRMED (freeze, create CodingTask)
  |- regenerate -> GENERATING
  |- edit -> REVIEW
+ CONFIRMED
  |- frozen
+ FAILED
  |- regenerate -> GENERATING
```

### 5.4 Coding Task

```
+ PENDING
  |- run -> RUNNING
+ RUNNING
  |- success -> SUCCEEDED
  |- failure -> FAILED
  |- cancel -> CANCELLED
+ FAILED
  |- rerun -> RUNNING (rerunPrompt required)
+ SUCCEEDED
  |- rerun -> RUNNING (rerunPrompt required)
+ CANCELLED
  |- rerun -> RUNNING (rerunPrompt required)
+ STALE
  |- no operation
```

## 6. Rerun Rules

1. `rerun` 必须提供非空 `rerunPrompt`。
2. 执行 prompt 必须包含：PRD 内容、OverviewDesign 内容、当前 DetailedDesign 内容、CodingTask 自身数据。
3. 若存在上一次失败的 Run，系统把 `failureReason` 自动拼入 prompt。
4. 同一 CodingTask 同时只能有一个 `RUNNING` Run；新的 `run`/`rerun` 必须等待当前 Run 结束或被取消。

## 7. Workspace Initialization

1. `CodingTaskExecutionService` 在执行前调用 `WorkspaceManager.resolve(projectId)` 获取工作区目录。
2. 若目录已存在则复用；否则按平台配置决定 `CLONE`（`project.gitUrl` 优先于平台模板地址）或 `SCAFFOLD`。
3. 解析结果中的 `worktreePath` 写入当前 `Run`。

## 8. Coding Task Auto-Run

1. `DetailedDesign.confirm` 创建 `CodingTask(PENDING)`。
2. 若对应 `Project.autoRunCodingTask = true`，则立即调用 `CodingTaskExecutionService.run(taskId, null, AUTO)`。
3. 批量确认时逐条创建任务，并逐条按项目配置决定是否自动运行。

## 9. Failure Handling

1. 代理生成失败时，实体进入 `FAILED`，并写入 `failureCode`、`failureSummary`、`failureDetail`、`lastFailedAt`。
2. Run 失败时，Run 写入 `failureSummary`、`failureReason`；CodingTask 进入 `FAILED`。
3. 重跑时系统自动把上一次失败原因加入 prompt。

## 10. Old Flow Compatibility

- 后端 `Plan/Spec/FeatureDesign/Task` 相关类与接口保留，不删除。
- 新 UI 不再链接到 `/online-code/spec`、`PlanTab`、`DetailedDesignTab`、`FeatureDesignTab`、`BuildActions`。
- `ProjectService.save` 不再调用 `PlanAgentService.spawnPlanning`。
- 旧数据不做迁移。
