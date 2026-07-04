# 编码前接口契约 — 规划书 + 功能设计

> **状态**：已冻结。Track B（后端）与 Track F（前端）唯一依据。
>
> **设计决策溯源**：本契约严格遵循 `PRE-BUILD-IMPLEMENTATION-PLAN.md` 中 **D1–D15** 评审决策；与设计稿 `PRE-BUILD-INTERACTION-DESIGN.md` 冲突时，以 D1–D15 为准。

---

## 1. Scope 范围

| 项 | 说明 |
|---|---|
| **In Scope** | Plan/FeatureDesign 两层 CRUD、状态机流转、编码执行互斥、内置 agent/skill seed、`FeatureDesignBuildService`、Testcontainers PG 测试基建 |
| **Out Scope** | 改动现有 Spec/DispatchService、真实 git clone、多租户、WebSocket 端点改动（复用现有） |

---

## 2. Domain Payload 领域载荷

### 2.1 PlanDto — 规划书 DTO

```typescript
interface PlanDto {
  id: string;                          // String-UUID
  projectId: string;                   // 所属项目
  version: number;                     // 版本号（从1起，递增）
  status: PlanStatus;                  // 规划状态
  content: PlanContent;                // 规划内容
  modifyHint?: string;                 // 上次重生时的修改提示
  isLatest: boolean;                   // 是否最新版本
  // 审计字段（BaseEntityDto）
  creatorId?: string;
  creatorAccount?: string;
  creatorName?: string;
  createdDate?: string;                // ISO-8601
  lastEditorId?: string;
  lastEditorAccount?: string;
  lastEditorName?: string;
  lastEditedDate?: string;             // ISO-8601
}
```

### 2.2 PlanContent — 规划内容（JSON）

```typescript
interface PlanContent {
  summary: string;                      // 项目目标与范围
  techAssumptions: string[];           // 技术假设（如：Vite+React+TS+@ead/suid+MSW）
  features: PlanFeature[];             // 功能项清单
  nonGoals: string[];                  // 本期不做项
}

interface PlanFeature {
  featureId: string;                   // 智能体分配的功能ID
  title: string;                       // 功能标题
  outline: string;                     // 功能概要（供功能设计智能体展开）
}
```

### 2.3 FeatureDesignDto — 功能设计 DTO

```typescript
interface FeatureDesignDto {
  id: string;                          // String-UUID
  projectId: string;                   // 所属项目
  featureId: string;                   // 对齐 PlanContent.features[].featureId
  version: number;                     // 版本号
  status: FeatureDesignStatus;         // 设计状态
  buildStatus: FeatureDesignBuildStatus; // 构建状态（编码执行生命周期）
  content?: FeatureDesignContent;      // 设计内容
  modifyHint?: string;                 // 上次重生时的修改提示
  isLatest: boolean;                   // 是否最新版本
  // 审计字段（BaseEntityDto）
  creatorId?: string;
  creatorAccount?: string;
  creatorName?: string;
  createdDate?: string;
  lastEditorId?: string;
  lastEditorAccount?: string;
  lastEditorName?: string;
  lastEditedDate?: string;
}
```

### 2.4 FeatureDesignContent — 功能设计内容（JSON）

```typescript
interface FeatureDesignContent {
  featureId: string;                   // 对齐 PlanContent.features[].featureId
  goal: string;                        // 该功能的用户故事
  design: object;                      // 页面/组件/交互/数据/接口契约片段（智能体自定粒度）
  acceptance: string[];                // 验收点
  fileScope: string[];                 // 编码时触及的文件边界（供 FeatureDesignBuildService 复用）
}
```

### 2.5 请求体 DTO

```typescript
// 编辑规划书
interface EditPlanRequest {
  content: PlanContent;
}

// 重新生成规划书
interface RegeneratePlanRequest {
  modifyHint?: string;
}

// 编辑功能设计
interface EditFeatureDesignRequest {
  content: FeatureDesignContent;
}

// 重新生成功能设计
interface RegenerateFeatureDesignRequest {
  modifyHint?: string;
}

// 批量确认功能设计
interface ConfirmFeatureDesignsRequest {
  ids: string[];
}
```

---

## 3. 枚举 Enums

### 3.1 PlanStatus — 规划书状态

```typescript
enum PlanStatus {
  GENERATING = 'GENERATING',           // 智能体生成中
  DRAFT = 'DRAFT',                     // 草稿（可编辑）
  CONFIRMED = 'CONFIRMED',             // 已确认（不可编辑）
  FAILED = 'FAILED'                    // 生成失败
}
```

### 3.2 FeatureDesignStatus — 功能设计状态

```typescript
enum FeatureDesignStatus {
  PENDING = 'PENDING',                 // 待生成（初始态）
  GENERATING = 'GENERATING',           // 智能体生成中
  DRAFT = 'DRAFT',                     // 草稿（可编辑）
  CONFIRMED = 'CONFIRMED',             // 已确认（不可编辑，可编码）
  STALE = 'STALE',                     // 已失效（规划书改动后，需重新生成）
  FAILED = 'FAILED'                    // 生成失败
}
```

### 3.3 FeatureDesignBuildStatus — 构建状态（编码执行生命周期）

```typescript
enum FeatureDesignBuildStatus {
  IDLE = 'IDLE',                       // 未构建（默认）
  BUILDING = 'BUILDING',               // 编码进行中（互斥态）
  BUILT = 'BUILT',                     // 上次编码完成
  BUILD_FAILED = 'BUILD_FAILED',       // 上次编码失败
  STALE = 'STALE'                      // 已构建但设计已改动，产物过时
}
```

---

## 4. 状态机 State Machines

### 4.1 项目状态 ProjectState（实时聚合，不持久化）

```
DRAFTING → PLANNING → DESIGNING → READY_TO_BUILD → [FeatureDesignBuildService 编码]
                   ↓
                 FAILED
```

**聚合规则**（含 D7 FAILED）：

| ProjectState | 聚合规则 |
|---|---|
| `DRAFTING` | 首次创建未起智能体（短暂态） |
| `PLANNING` | 最新 Plan ∈ {GENERATING, DRAFT} |
| `DESIGNING` | Plan = CONFIRMED 且 (任一 FeatureDesign ∈ {PENDING, GENERATING, DRAFT, STALE} 或 FD 空集) |
| `READY_TO_BUILD` | Plan = CONFIRMED 且 全部 FeatureDesign = CONFIRMED |
| `FAILED` | Plan = FAILED **或** 任一 FeatureDesign = FAILED |

> **D15**：Plan = CONFIRMED 且 FD 空集 → `DESIGNING`（视为未就绪）。

### 4.2 规划书状态流转 PlanStatus

```
GENERATING → DRAFT → CONFIRMED
                 ↑     │
                 └─────┘  (编辑或重新生成 → 退回 DRAFT，关联 FeatureDesign → STALE)
                ↓
              FAILED
```

### 4.3 功能设计状态流转 FeatureDesignStatus

```
PENDING → GENERATING → DRAFT → CONFIRMED
              ↑           │
              └───────────┘  (编辑或重新生成 → DRAFT)
              ↑
           STALE ← 规划书被改动时，所有已生成 FeatureDesign 置此态
              ↓
            FAILED
```

### 4.4 功能设计构建状态流转 FeatureDesignBuildStatus

```
                  ┌─────────── (re-execute, design=CONFIRMED) ───────────┐
                  ▼                                                     │
IDLE ──[执行]──> BUILDING ──[完成]──> BUILT                              │
                   │                    │                                │
                   │ [失败]             │ [编辑/重生/规划书改动]           │
                   ▼                    ▼                                │
              BUILD_FAILED ──────────> STALE ──[重新确认后执行]──> BUILDING
                   │  (retry, design=CONFIRMED)
                   └──────────────────────────────> BUILDING
```

**执行前置条件**：`design.status = CONFIRMED` 且 `build_status != BUILDING`。

---

## 5. 端点表 Endpoints P1–P14 + P12a

**通用响应信封**：所有接口返回 `ResultData<T>` 或 `PageResult<T>`（与现有 API 风格一致）。

**失败码约定**（D1）：
- 仅**编码执行互斥**（P12/P12a）、**BUILDING 态编辑**（P8/P9）返回 **HTTP 409**
- 其余业务错误（未确认/不存在/STALE 不可确认等）返回 **HTTP 200** + `ResultData.fail(msg)`
- **无 422**

---

| # | Method | Path | Request | Response Data | 状态推进 | 失败码 |
|---|---|---|---|---|---|---|
| **P1** | `POST` | `/project` | `{description: string}` | `{projectId: string}` | 复用现有 `BaseEntityApi.save`；Project=DRAFTING→PLANNING, Plan=GENERATING；后端起规划智能体 | 200+fail |
| **P2** | `GET` | `/plan/{projectId}` | — | `PlanDto` | — | 200+fail |
| **P3** | `PUT` | `/plan/{projectId}` | `EditPlanRequest` | `PlanDto` | Plan→DRAFT；关联 FeatureDesign→STALE；Project→PLANNING | 200+fail |
| **P4** | `POST` | `/plan/{projectId}/regenerate` | `RegeneratePlanRequest` | `PlanDto` | version+1；Plan=GENERATING→DRAFT；FeatureDesign→STALE；起规划智能体 | 200+fail |
| **P5** | `POST` | `/plan/{projectId}/confirm` | — | `PlanDto` | Plan=CONFIRMED；批量起 FeatureDesign 智能体；Project=DESIGNING | 200+fail（非 DRAFT 时拒绝） |
| **P6** | `POST` | `/featureDesign/findByPage` | `Search`（继承 `FindByPageApi`，带 `projectId` filter） | `PageResult<FeatureDesignDto>` | — | 200+fail |
| **P7** | `GET` | `/featureDesign/{id}` | — | `FeatureDesignDto` | — | 200+fail |
| **P8** | `PUT` | `/featureDesign/{id}` | `EditFeatureDesignRequest` | `FeatureDesignDto` | 该条→DRAFT；`build_status`∈{BUILT,BUILD_FAILED}→STALE | **409**（BUILDING 时拒绝）；否则 200+fail |
| **P9** | `POST` | `/featureDesign/{id}/regenerate` | `RegenerateFeatureDesignRequest` | `FeatureDesignDto` | version+1；=GENERATING→DRAFT；`build_status`∈{BUILT,BUILD_FAILED}→STALE；起设计智能体 | **409**（BUILDING 时拒绝）；否则 200+fail |
| **P10** | `POST` | `/featureDesign/confirm` | `ConfirmFeatureDesignsRequest` | `FeatureDesignDto[]` | 逐条→CONFIRMED；全部 CONFIRMED 则 Project=READY_TO_BUILD | 200+fail（STALE 条不可确认） |
| **P11** | `POST` | `/featureDesign/{id}/confirm` | — | `FeatureDesignDto` | 该条→CONFIRMED；全部 CONFIRMED 则 Project=READY_TO_BUILD | 200+fail（STALE 不可确认） |
| **P12** | `POST` | `/project/{projectId}/build` | — | `Array<{id: string, runId?: string, skipped?: boolean, reason?: string}>` | 对每条 CONFIRMED 且 build_status≠BUILDING 的 feature 条件 UPDATE 抢占→BUILDING，交 FeatureDesignBuildService；已在 BUILDING 的 skipped | **409**（按条返回，整批仍 200） |
| **P12a** | `POST` | `/featureDesign/{id}/build` | — | `{runId: string}` | 条件 UPDATE 抢占→BUILDING，交 FeatureDesignBuildService | **409**（已在 BUILDING 时拒绝）；否则 200+fail（非 CONFIRMED 时拒绝） |
| **P13** | `GET` | `/plan/{projectId}/history` | — | `PlanDto[]` | — | 200+fail |
| **P14** | `GET` | `/featureDesign/{id}/history` | — | `FeatureDesignDto[]` | — | 200+fail |

---

**关键决策说明**：
- **D2 P1**：复用现有 `POST /project`（`BaseEntityApi.save`），不新增端点；body 仅 `{description}`
- **D14 P6**：沿用 `FindByPageApi.findByPage(Search)`（POST），非设计稿的 GET；`Search` 带 `projectId` filter
- **D13 P12**：`POST /project/{projectId}/build`（PathVariable）

---

## 6. 409 机制（D1）

### 6.1 异常定义

```java
// 新增 ConflictException
package com.changhong.onlinecode.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
```

### 6.2 异常处理器

```java
// 新增 PreBuildExceptionHandler
package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.exception.ConflictException;
import com.changhong.sei.core.dto.ResultData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PreBuildExceptionHandler {

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ResultData<Void>> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ResultData.fail(e.getMessage()));
    }
}
```

### 6.3 409 触发场景

| 场景 | 端点 | 条件 |
|---|---|---|
| BUILDING 态编辑功能设计 | P8 | `build_status = BUILDING` |
| BUILDING 态重生功能设计 | P9 | `build_status = BUILDING` |
| 编码执行抢占失败 | P12a | 条件 UPDATE `affected rows = 0`（已在 BUILDING） |
| 编码执行抢占失败 | P12 | 按条返回，整批仍 200 |

### 6.4 与仓库"全 200"规范的张力

> **待清理项**：本契约引入 HTTP 409 用于互斥场景，与仓库"全 200+业务码"规范不一致。后续可考虑统一为 200+特定业务码（如 `code: 409001`），但本次按 D1 决策执行。

---

## 7. WebSocket 端点（D4）

**WS 路径**：`/ws/run/{iterationId}`（复用现有 `RunLogWebSocketHub`，按 `iterationId` 索引）

**非** `/ws/run/{runId}`。

**RunLogFrame** 结构（复用现有）：
```typescript
interface RunLogFrame {
  runId: string;
  taskId?: string;
  state: string;
  // ... 其他现有字段
}
```

**前端订阅方式**：按 `frame.runId` 过滤各 feature/plan 流。

---

## 8. DDL（D15/D9）

**数据库**：PostgreSQL。

### 8.1 oc_plan — 规划书表

```sql
CREATE TABLE oc_plan (
    id                  VARCHAR(36)  NOT NULL,
    project_id          VARCHAR(36)  NOT NULL,
    version             INT          NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    content             TEXT,                    -- JSON（D15：非 JSONB；GENERATING 态可为 null）
    modify_hint         TEXT,
    is_latest           BOOLEAN      NOT NULL DEFAULT TRUE,
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_plan PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_plan_proj_ver       ON oc_plan (project_id, version);
CREATE UNIQUE INDEX uk_plan_proj_latest    ON oc_plan (project_id) WHERE is_latest = TRUE;  -- PG partial index
CREATE INDEX idx_plan_project              ON oc_plan (project_id);
```

### 8.2 oc_feature_design — 功能设计表

```sql
CREATE TABLE oc_feature_design (
    id                  VARCHAR(36)  NOT NULL,
    project_id          VARCHAR(36)  NOT NULL,
    feature_id          VARCHAR(128) NOT NULL,
    version             INT          NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    build_status        VARCHAR(32)  NOT NULL DEFAULT 'IDLE',
    content             TEXT,                    -- JSON（D15：非 JSONB）
    modify_hint         TEXT,
    is_latest           BOOLEAN      NOT NULL DEFAULT TRUE,
    -- 审计字段（BaseAuditableEntity）
    creator_id          VARCHAR(36),
    creator_account     VARCHAR(100),
    creator_name        VARCHAR(100),
    created_date        TIMESTAMP,
    last_editor_id      VARCHAR(36),
    last_editor_account VARCHAR(100),
    last_editor_name    VARCHAR(100),
    last_edited_date    TIMESTAMP,
    CONSTRAINT pk_oc_feature_design PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_fd_proj_feat_ver    ON oc_feature_design (project_id, feature_id, version);
CREATE UNIQUE INDEX uk_fd_proj_feat_latest ON oc_feature_design (project_id, feature_id) WHERE is_latest = TRUE;  -- PG partial index
CREATE INDEX idx_fd_project                ON oc_feature_design (project_id);
```

### 8.3 oc_task 新增列（D8）

> **D8 裁决**：读 `Task.java` 确认无合适复用字段后，V6 给 `oc_task` 新增列：

```sql
ALTER TABLE oc_task ADD COLUMN feature_design_id VARCHAR(36);
CREATE INDEX idx_task_feature_design ON oc_task (feature_design_id);
```

---

## 9. 包名约定（D5）

**统一使用旧拼写包**（与 `FindByPageApi` 一致）：

```java
package com.changhong.sei.core.dto.serach;  // 注意：serach（非 search）

// Search / PageResult 均从该包导入
```

---

## 10. 内置 Seed（D3/D9）

### 10.1 内置 skill — classpath 资源化（multica 维度 g）

内置 skill（`suid` / `eadp-backend` / `project-planning` / `feature-design`）**不再以 `oc_skill` 行 seed**。
它们 vendor 到后端 classpath `src/main/resources/skills/<name>/`（`SKILL.md` + 可选 `references/**`），
由 `BuiltInSkillRegistry` 经 `builtin:<name>` synthetic id 加载（见 `API-CONTRACT-PHASE3.md` §4）。

- `suid` / `eadp-backend`：从操作机 `~/.claude/skills/<name>/` 拷入完整内容 + `references/`。
- `project-planning` / `feature-design`：内容尚未沉淀，暂为 stub `SKILL.md`（含 `TODO` 占位）。
- 迁移 `V11__skill_builtin_synthetic_id.sql` 删除 V3/V6 种入的 4 行 `oc_skill` LOCAL 指针 stub，
  并把 `oc_agent_skill` 中 `planning-agent` / `feature-design-agent` 的 `skill_id` 改写为
  `builtin:project-planning` / `builtin:feature-design`（join 表 `skill_id` 无 FK，V7 预留）。

### 10.2 oc_agent seed — 三个内置 agent

```sql
-- planning-agent（builtin=true，不可删）
INSERT INTO oc_agent (
    id, name, description, instructions, model, builtin, skill_ids, created_date
) VALUES (
    'AGENT_SEED_PLANNING_001',
    'planning-agent',
    '内置规划 agent：项目描述 → 规划书 JSON',
    'You produce a project Plan JSON from the project description. Use the project-planning skill.',
    '',
    TRUE,
    '["builtin:project-planning"]',  -- V7 起改 oc_agent_skill join 行；V11 起值为 builtin:<name>
    CURRENT_TIMESTAMP
);

-- feature-design-agent（builtin=true，不可删）
INSERT INTO oc_agent (
    id, name, description, instructions, model, builtin, skill_ids, created_date
) VALUES (
    'AGENT_SEED_FEATURE_DESIGN_001',
    'feature-design-agent',
    '内置功能设计 agent：规划书 + feature outline → 功能设计 JSON',
    'You produce one FeatureDesign JSON from the plan and a feature outline. Use the feature-design skill.',
    '',
    TRUE,
    '["builtin:feature-design"]',
    CURRENT_TIMESTAMP
);

-- dev-agent（D3：编码执行 agent，builtin=true，不可删；skill_ids=NULL）
-- 顺带修复 DispatchService 硬编码 "dev-agent" 的悬空引用
INSERT INTO oc_agent (
    id, name, description, instructions, model, builtin, skill_ids, created_date
) VALUES (
    'AGENT_SEED_DEV_AGENT_001',
    'dev-agent',
    '内置编码执行 agent：按功能设计 fileScope 执行编码（写代码），不产设计 JSON',
    'You implement code for one confirmed FeatureDesign. Write files within the fileScope. Do not produce design JSON.',
    '',
    TRUE,
    NULL,  -- 无绑定 skill
    CURRENT_TIMESTAMP
);
```

> 注：`skill_ids` JSON 列在 V7 已迁移为 `oc_agent_skill` join 表（见 `V7__agent_skill_join_table.sql`）。
> 上方 `skill_ids` 仅为种子意图说明；V11 起 join 行 `skill_id` 为 `builtin:<name>` synthetic id。

> **D9 提醒**：`skill_ids` 格式（JSON array 字符串 vs 其他）实现时读 `StringListConverter` 确认；审计列默认值对齐 V3。

---

## 11. 前端 DVA Model（D6）

**路径**：`frontend/src/models/planFeatureDesign.ts`

**不用** `useStore`/`projectStore`（项目无此文件），用 dva。

```typescript
import { Effect, Reducer, Subscription } from 'umi';
import { PlanDto, FeatureDesignDto } from '@/services/planFeatureDesign';

export interface PlanFeatureDesignModelState {
  projectId?: string;
  projectState?: 'DRAFTING' | 'PLANNING' | 'DESIGNING' | 'READY_TO_BUILD' | 'FAILED';
  latestPlan?: PlanDto;
  planHistory: PlanDto[];
  featureDesigns: FeatureDesignDto[];
  // buildStatus 映射: featureId -> buildStatus
  buildStatusMap: Record<string, string>;
}

export interface PlanFeatureDesignModelType {
  namespace: 'planFeatureDesign';
  state: PlanFeatureDesignModelState;
  effects: {
    fetchProjectState: Effect;
    fetchLatestPlan: Effect;
    fetchPlanHistory: Effect;
    fetchFeatureDesigns: Effect;
    // ... 其他 effects
  };
  reducers: {
    saveState: Reducer<PlanFeatureDesignModelState>;
    saveBuildStatus: Reducer<PlanFeatureDesignModelState>;
    // ... 其他 reducers
  };
  subscriptions: {
    // WS 订阅：/ws/run/{iterationId}，按 frame.runId 过滤各 feature 流
    wsSubscription: Subscription;
  };
}

const PlanFeatureDesignModel: PlanFeatureDesignModelType = {
  namespace: 'planFeatureDesign',

  state: {
    projectId: undefined,
    projectState: undefined,
    latestPlan: undefined,
    planHistory: [],
    featureDesigns: [],
    buildStatusMap: {},
  },

  effects: {
    // ... effects 实现
  },

  reducers: {
    saveState(state, { payload }) {
      return { ...state, ...payload };
    },
    saveBuildStatus(state, { payload: { featureId, buildStatus } }) {
      return {
        ...state,
        buildStatusMap: {
          ...state.buildStatusMap,
          [featureId]: buildStatus,
        },
      };
    },
  },

  subscriptions: {
    wsSubscription({ dispatch, history }) {
      // D4：WS 端点 /ws/run/{iterationId}，按 frame.runId 过滤
    },
  },
};

export default PlanFeatureDesignModel;
```

---

## 附录 A：D1–D15 决策溯源

| 决策 | 在本契约中的落点 |
|---|---|
| **D1** | 409 机制（§6），注明与"全 200"张力为待清理项 |
| **D2** | P1 复用现有 `POST /project`（§5） |
| **D3** | 内置 dev-agent seed（§10.2） |
| **D4** | WS 路径 `/ws/run/{iterationId}`（§7） |
| **D5** | 包名统一 `com.changhong.sei.core.dto.serach`（§9） |
| **D6** | dva model（§11） |
| **D7** | 聚合规则含 FAILED（§4.1） |
| **D8** | oc_task 新增 feature_design_id 列（§8.3） |
| **D9** | seed 列对齐 V3、skill_ids 格式提醒（§10） |
| **D10** | DDL 注明 PG partial index（§8） |
| **D11** | （D11 在后端实现层，本契约不涉及） |
| **D12** | （D12 在后端实现层，本契约不涉及） |
| **D13** | P12 路径 `POST /project/{projectId}/build`（§5） |
| **D14** | P6 POST `findByPage` 继承 FindByPageApi（§5） |
| **D15** | content=TEXT（§8）、FD 空集→DESIGNING（§4.1） |

---

## 附录 B：与设计稿的冲突点及裁决

| 设计稿原文 | 契约裁决（D1–D15） | 理由 |
|---|---|---|
| §5 DDL `content JSONB` | 契约 `content TEXT` | **D15**：与 `SpecPageListConverter` 一致 |
| §6 P6 `GET /featureDesign` | 契约 `POST /featureDesign/findByPage` 继承 `FindByPageApi` | **D14**：与 sei-core 约定一致 |
| §7 WS `/ws/run/{runId}` | 契约 `/ws/run/{iterationId}` | **D4**：复用现有 Hub 的 iterationId 索引 |

---

## 附录 C：自查清单 Self-Check

- [x] Scope 表（§1）
- [x] Domain payload（§2）
- [x] 枚举（§3）
- [x] 状态机图 + 聚合规则（含 D7 FAILED）（§4）
- [x] 端点表 P1–P14+P12a（§5）
- [x] 409 机制（D1）（§6）
- [x] WS 端点（D4）（§7）
- [x] DDL（D15/D9）（§8）
- [x] 包名（D5）（§9）
- [x] 内置 seed（D3/D9）（§10）
- [x] 前端 dva model（D6）（§11）
- [x] D1–D15 溯源（附录 A）
- [x] 与设计稿冲突点说明（附录 B）
