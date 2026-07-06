# 编码前交互设计 — 规划书 + 功能设计（两层产物）

> 状态：设计稿（待评审）。覆盖编码前的用户交互流程，止于"功能设计 → 编码执行（FeatureDesignBuildService）"。
不改动现有 Spec/DispatchService；编码执行由新增 FeatureDesignBuildService 承接。后端遵循 `eadp-backend`；前端遵循 `suid`。

## 1. 背景与定位

现有平台领域模型为 Project Design → Spec（需求智能体细化）→ Dispatch → Task/Run → 编码。
本次**重新设计编码前前置流程**，抛开现有 Spec/Task 模型，按"规划书 + 功能设计"两层重做：

- 用户新增项目 → 系统保存（状态：生成中）→ 后端用智能体 + 技能规划 → 产出**项目规划书**并落库（状态：规划中）
- 用户可查看 / 修改 / 重新生成规划书
- 用户确认规划书 → 针对规划书每个功能生成相应**功能设计**（状态：功能设计中）
- 用户可对每个功能设计查看 / 修改 / 重新生成
- 用户全部确认后点"执行编码" → FeatureDesignBuildService 起编码（功能设计=Task，不二次切分）

### 范围边界

- 前置流程止于"功能设计 → 编码执行"，编码执行由**新增 `FeatureDesignBuildService`** 承接（见下），不改动现有 Spec/DispatchService。
- 功能设计粒度由规划智能体自定（份数与结构不预设）。
- 修改 / 重新生成保留版本历史。
- 规划与功能设计生成为异步智能体运行，进度 / 日志复用现有 WS Hub。
- **功能设计 = 编码执行单元（不二次切分）**：每份已确认的功能设计直接作为一个 `Task`（`fileScope` 直接复用），由 `FeatureDesignBuildService` 起一次 `ClaudeRunner` 运行，复用现有 `Run` / WS Hub，但**不走现有 `DispatchService`**（后者输入是 Spec 且会切分 Task，与本设计"功能设计即 Task、不二次切分"矛盾）。

> 衔接决策：现有 `DispatchService` 输入是 Spec 并负责切分 Task，与本设计语义不符。故新增 `FeatureDesignBuildService`：功能设计 → 单个 `Task`（fileScope 复用）→ `ClaudeRunner.execute`（复用 `Run`/WS Hub）。现有 Spec/DispatchService 不改动，新旧两套编码入口并存（Spec 路径与 FeatureDesign 路径），由各自的 controller/service 边界隔离，互不引用。

## 2. 两层产物

### 规划书（Plan）— 项目级整体设计，每项目一份

结构化 JSON + 描述文本，落库于 `plan` 表：

```
{
  summary:        string,            // 项目目标与范围
  techAssumptions: string[],         // 技术假设（沿用模板栈：Vite+React+TS+@ead/suid+MSW）
  features: [                        // 功能项清单
    { featureId: string, title: string, outline: string }  // featureId 由智能体分配
  ],
  nonGoals: string[]                 // 本期不做项
}
```

> `fileScope` 不在规划书层预估，只在功能设计层定一次，避免双层冗余。

### 功能设计（FeatureDesign）— 每个功能项一份，多份

智能体自定粒度，但固定骨架，落库于 `feature_design` 表。`content` JSON 仅含设计内容；**编码执行生命周期由独立的 `build_status` 列承载**（见 §3 构建状态机），不混入 content：

```
content JSON:
{
  featureId:  string,                // 与 plan.content.features[].featureId 对应
  goal:       string,                // 该功能的用户故事
  design:     object,                // 页面/组件/交互/数据/接口契约片段（智能体按粒度填）
  acceptance: string[],              // 验收点
  fileScope:  string[]               // 编码时触及的文件边界，最终交给 FeatureDesignBuildService
}

列:
  status        VARCHAR  -- 设计状态: PENDING/GENERATING/DRAFT/CONFIRMED/STALE/FAILED
  build_status  VARCHAR  -- 构建状态: IDLE/BUILDING/BUILT/BUILD_FAILED/STALE
```

## 3. 状态机

### 项目状态 `ProjectState`（编码前部分，编码侧复用现有）

```
DRAFTING → PLANNING → DESIGNING → READY_TO_BUILD → [FeatureDesignBuildService 编码]
                                                    （编码侧：DISPATCHING/DEVELOPING/...）
任意阶段失败 → FAILED
```

`ProjectState` 编码前部分**不持久化列**，由 `ProjectStateService` 查询时按 plan / feature_design 实时聚合：

| Project 状态 | 聚合规则 |
|---|---|
| `DRAFTING` | 首次创建未起智能体（短暂态，起运行后即 PLANNING） |
| `PLANNING` | 最新 Plan ∈ {GENERATING, DRAFT} |
| `DESIGNING` | Plan = CONFIRMED 且 任一 FeatureDesign ∈ {PENDING, GENERATING, DRAFT, STALE} |
| `READY_TO_BUILD` | Plan = CONFIRMED 且 全部 FeatureDesign = CONFIRMED |

### 规划书状态 `PlanStatus`

```
GENERATING → DRAFT → CONFIRMED
                 ↑     │
                 └─────┘  (编辑或重新生成 → 退回 DRAFT，关联 FeatureDesign → STALE)
任意阶段失败 → FAILED
```

### 功能设计状态 `FeatureDesignStatus`

```
PENDING → GENERATING → DRAFT → CONFIRMED
              ↑           │
              └───────────┘  (编辑或重新生成 → DRAFT)
              ↑
           STALE ← 规划书被改动时，所有已生成 FeatureDesign 置此态
任意阶段失败 → FAILED
```

`STALE` 态：不可确认；用户须重新生成（`modifyHint` 可空）使其 `DRAFT`。

### 功能设计构建状态 `FeatureDesignBuildStatus`（编码执行生命周期）

`build_status` 与设计 `status` 分离，专门管控"该 feature 的编码执行"，保证 **同一 feature 不会同时编码**、**执行完成后可改可重跑**：

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

| build_status | 含义 | 可否执行编码 |
|---|---|---|
| `IDLE` | 未构建（默认 / 当前设计从未编码） | 是（需 design=CONFIRMED） |
| `BUILDING` | 编码进行中（互斥态） | **否**（拒绝，防同 feature 并发） |
| `BUILT` | 上次编码完成 | 是（可重跑） |
| `BUILD_FAILED` | 上次编码失败 | 是（可重试） |
| `STALE` | 已构建但设计已改动，产物过时 | 是（需重新确认后执行） |

**执行前置条件**（P12a / P12 统一校验）：`design.status = CONFIRMED` 且 `build_status != BUILDING`。

**状态推进规则**：

| 事件 | design.status | build_status |
|---|---|---|
| 执行编码（进入 BUILDING） | CONFIRMED（不变） | 旧态 → `BUILDING` |
| Run 回调成功 | CONFIRMED（不变） | `BUILDING` → `BUILT` |
| Run 回调失败 | CONFIRMED（不变） | `BUILDING` → `BUILD_FAILED` |
| 编辑功能设计（P8） | → `DRAFT` | 若 ∈ {BUILT, BUILD_FAILED} → `STALE`；BUILDING 时**拒绝编辑** |
| 重新生成功能设计（P9） | → `GENERATING`→`DRAFT` | 同上 |
| 规划书改动级联 | → `STALE` | 若 ∈ {BUILT, BUILD_FAILED} → `STALE` |

> `BUILT`/`BUILD_FAILED` → `STALE` 仅在"设计被改动"时发生；`STALE` 重新确认后可再次执行。`BUILT` 不经改动也可直接重跑（重新执行编码）。

### 编码执行互斥（per-feature）

- **互斥粒度**：单个 `feature_id`。同一 feature 同时只允许一个 `BUILDING`。
- **实现**：后端 Service 用**条件 UPDATE** 抢占，天然抗并发，不依赖内存锁：

```sql
-- 原子抢占：仅当当前非 BUILDING 且设计已确认时才置 BUILDING
UPDATE feature_design
   SET build_status = 'BUILDING', updated_at = now()
 WHERE id = ? AND is_latest = TRUE AND build_status <> 'BUILDING';
-- affected rows = 0 → 已在构建中，返回 409 拒绝
```

- 抢占成功 → 交 `FeatureDesignBuildService`（功能设计 = Task 输入单元，`fileScope` 直接复用，不二次切分）。
- Run 回调按 `runId` → `feature_id` 定位行，更新 `build_status` 为 `BUILT`/`BUILD_FAILED`。
- **跨 feature 不互斥**：不同 feature 可并行编码（受现有 worktree 隔离与全局并发上限约束，本设计不额外限制）。

### 规划书 / 功能设计失效规则

- Plan 编辑或重新生成提交 → Plan = `DRAFT`；该项目下所有 FeatureDesign 的 `status` 置 `STALE`，`build_status` ∈ {BUILT, BUILD_FAILED} 置 `STALE`；Project 退回 `PLANNING`。
- 这是"允许随时改规划书"的代价：改后已生成的功能设计失效，需重生。

## 4. 页面流与用户操作

### 4 个视图（现有平台 UI 内，`suid` 组件）

```
项目列表页 ──[新增项目]──> 项目详情页(规划阶段)
                              │
                              ├─ 规划书 Tab：查看 / 编辑 / 重新生成 / 确认
                              │     └─[确认规划书] → 项目进 DESIGNING
                              │
                              ├─ 功能设计 Tab：列表(多选) + 逐项操作
                              │     ├─ 每行：查看 / 编辑 / 重新生成 / 确认
                              │     ├─[批量确认]
                              │     └─ 全部 CONFIRMED → [执行编码]亮起
                              │            └─[执行编码] → FeatureDesignBuildService
                              │
                              └─ 运行日志 Tab：复用现有 WS 日志流（规划 / 设计生成进度）
```

### 操作 → 状态推进映射

| 用户操作 | 触发 | 状态推进 |
|---|---|---|
| 新增项目（填描述） | 后端起规划智能体异步运行 | Project=DRAFTING→PLANNING, Plan=GENERATING |
| 查看规划书 | 读 Plan 当前版本 | — |
| 编辑规划书 | 直接改 content | Plan=DRAFT；关联 FeatureDesign=STALE；Project=PLANNING |
| 重新生成规划书 | 带 modifyHint 起规划智能体 | version+1；Plan=GENERATING→DRAFT；FeatureDesign=STALE |
| 确认规划书 | — | Plan=CONFIRMED；批量起各 FeatureDesign 智能体；Project=DESIGNING |
| 查看功能设计 | 读单条 | — |
| 编辑功能设计 | 直接改 content | 该条=DRAFT |
| 重新生成功能设计 | 带 modifyHint 起设计智能体 | version+1；该条=GENERATING→DRAFT |
| 确认功能设计（单 / 批） | — | 该条=CONFIRMED；全部 CONFIRMED → Project=READY_TO_BUILD |
| 执行编码（单 feature / 批量） | 条件 UPDATE 抢占 build_status | `design=CONFIRMED` 且 `build_status≠BUILDING` → `BUILDING`，交 `FeatureDesignBuildService`；同 feature 已 BUILDING → 409 拒绝 |
| 编码完成（Run 回调） | `FeatureDesignBuildService`/Run 回调 | `BUILDING` → `BUILT`（成功）/ `BUILD_FAILED`（失败） |
| 执行后修改功能设计 | 编辑(P8)/重生(P9) | `design`→DRAFT/STALE；`build_status`∈{BUILT,BUILD_FAILED}→`STALE`；BUILDING 时拒绝 |
| 重新执行（重跑 / 重试） | 重新确认后 P12a，或 BUILT 直接 P12a | `STALE`/`BUILT`/`BUILD_FAILED` → `BUILDING` |

### 批量确认

功能设计列表页多选 → "批量确认"；全部 `CONFIRMED` 后项目自动 `READY_TO_BUILD`，"执行编码"按钮亮起。

## 5. 数据模型 DDL（PostgreSQL）

复用现有 String-UUID 主键（`IdGenerator.nextIdStr()`）+ 审计基类列。
版本历史采用**单表多行 + `is_latest` 标记**。

```sql
-- plan：规划书，每项目一份，按 version 留历史
CREATE TABLE plan (
  id            VARCHAR(64)  NOT NULL,
  project_id    VARCHAR(64)  NOT NULL,
  version       INT          NOT NULL,            -- 重生递增，从 1 起
  status        VARCHAR(32)  NOT NULL,            -- GENERATING/DRAFT/CONFIRMED/FAILED
  content       JSONB        NOT NULL,            -- {summary,techAssumptions,features[],nonGoals}
  modify_hint   TEXT,                             -- 上次重生时的修改提示
  is_latest     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_by    VARCHAR(64), updated_by VARCHAR(64),
  created_at    TIMESTAMP,   updated_at TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_plan_proj_ver       UNIQUE (project_id, version),
  CONSTRAINT uk_plan_proj_latest    UNIQUE (project_id) WHERE is_latest = TRUE
);
CREATE INDEX idx_plan_project ON plan(project_id);

-- feature_design：每个功能项一份，按 version 留历史
CREATE TABLE feature_design (
  id            VARCHAR(64)  NOT NULL,
  project_id    VARCHAR(64)  NOT NULL,
  feature_id    VARCHAR(128) NOT NULL,            -- 对齐 plan.content.features[].featureId
  version       INT          NOT NULL,
  status        VARCHAR(32)  NOT NULL,            -- PENDING/GENERATING/DRAFT/CONFIRMED/STALE/FAILED
  build_status  VARCHAR(32)  NOT NULL DEFAULT 'IDLE',  -- IDLE/BUILDING/BUILT/BUILD_FAILED/STALE
  content       JSONB,                            -- {goal,design,acceptance[],fileScope}
  modify_hint   TEXT,
  is_latest     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_by    VARCHAR(64), updated_by VARCHAR(64),
  created_at    TIMESTAMP,   updated_at TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_fd_proj_feat_ver    UNIQUE (project_id, feature_id, version),
  CONSTRAINT uk_fd_proj_feat_latest UNIQUE (project_id, feature_id) WHERE is_latest = TRUE
);
CREATE INDEX idx_fd_project ON feature_design(project_id);
```

> `project` 表复用现有；编码前状态不新增列，按 §3 聚合规则实时计算，杜绝双写不一致。

## 6. API 契约骨架（REST + `ResultData`，与现有 API-CONTRACT 风格一致）

| # | 方法 | 路径 | 说明 |
|---|---|---|---|
| P1 | POST | `/api/project` | 新增项目，body: `{description}`；后端起规划智能体，返回 `{projectId}` |
| P2 | GET | `/api/plan/{projectId}` | 取最新 Plan（含 status / version / content） |
| P3 | PUT | `/api/plan/{projectId}` | 编辑 content；Plan→DRAFT，关联 FeatureDesign→STALE，Project→PLANNING |
| P4 | POST | `/api/plan/{projectId}/regenerate` | 重生，body: `{modifyHint?}`；version+1，Plan=GENERATING 起运行 |
| P5 | POST | `/api/plan/{projectId}/confirm` | 确认规划书；Plan=CONFIRMED，批量起 FeatureDesign 智能体，Project=DESIGNING |
| P6 | GET | `/api/featureDesign` | 列表，query: `projectId`，ExtTable remotePaging |
| P7 | GET | `/api/featureDesign/{id}` | 取单条最新版 |
| P8 | PUT | `/api/featureDesign/{id}` | 编辑 content；该条→DRAFT；`build_status`∈{BUILT,BUILD_FAILED}→`STALE`；`BUILDING` 时 409 拒绝 |
| P9 | POST | `/api/featureDesign/{id}/regenerate` | 重生，body: `{modifyHint?}`；version+1，=GENERATING 起运行；`BUILDING` 时 409 拒绝；完成后 `build_status`→`STALE`（若原 BUILT/BUILD_FAILED） |
| P10 | POST | `/api/featureDesign/confirm` | 批量确认，body: `{ids:[]}`；逐条→CONFIRMED；全部 CONFIRMED 则 Project=READY_TO_BUILD |
| P11 | POST | `/api/featureDesign/{id}/confirm` | 单条确认（P10 的便捷版） |
| P12 | POST | `/api/project/{projectId}/build` | 批量执行编码；对每条 `CONFIRMED` 且 `build_status≠BUILDING` 的 feature 条件 UPDATE 抢占→交 `FeatureDesignBuildService`；已在 BUILDING 的跳过并返回 |
| P12a | POST | `/api/featureDesign/{id}/build` | 单 feature 执行编码；校验 `design=CONFIRMED` 且 `build_status≠BUILDING`，条件 UPDATE 抢占，交 `FeatureDesignBuildService`，返回 `{runId}` |
| P13 | GET | `/api/plan/{projectId}/history` | 规划书历史版本列表 |
| P14 | GET | `/api/featureDesign/{id}/history` | 功能设计历史版本列表 |

异步运行结果通过现有 WS Hub（`/ws/run/{runId}`）推送，产物落库后前端按 P2 / P7 刷新。

## 7. 智能体执行层

### 执行方式：Java spawn `claude` CLI（沿用现有机制）

规划智能体 / 功能设计智能体 = **两个内置 agent 配置**（prompt + 绑定 skill），由现有 `ClaudeRunner`（ProcessBuilder spawn `claude`）执行，与 Phase 1–5 的执行层同构。不引入 Java AI 框架（Spring AI / LangChain4j）或 Node 中间层，避免第二套智能体写法。

| 智能体 | 内置 agent | 绑定 skill | 触发时机 | 输入 | 输出 |
|---|---|---|---|---|---|
| 规划智能体 | `planning-agent`（builtin, 不可删） | `project-planning`（builtin seed skill） | 新增项目 / 重新生成规划书（P1/P4） | 项目描述 + modifyHint | 规划书 JSON（§2 Plan 结构） |
| 功能设计智能体 | `feature-design-agent`（builtin, 不可删） | `feature-design`（builtin seed skill） | 确认规划书后批量起 / 单个重新生成（P5/P9） | 规划书 + 某 feature outline + modifyHint | 该功能设计 JSON（§2 FeatureDesign 结构） |

### 内置 agent 与 skill（系统默认配置，编码实现）

沿用 Phase 3 已建的 Agent/Skill 体系（`Agent`/`Skill` 实体、`builtin=true` 不可删、skill 单文件 `SKILL.md` + sha256 hash-lock、agent↔skill 绑定）。**新增两个内置 agent + 两个内置 seed skill**，由平台在 Flyway seed 阶段写入，非用户运行时创建：

| 内置 agent | 绑定 skill | 职责（agent instructions） | skill 内容（SKILL.md，编码实现） |
|---|---|---|---|
| `planning-agent` | `project-planning` | 接收项目描述 + modifyHint，产出规划书 JSON | 规划方法论：如何从描述拆功能项、分配 `featureId`、估技术假设、列 nonGoals；**强制输出 §2 Plan JSON 骨架**（`summary/techAssumptions/features[]/nonGoals`），禁止预估 `fileScope` |
| `feature-design-agent` | `feature-design` | 接收规划书 + 某 feature outline + modifyHint，产出该功能设计 JSON | 功能设计方法论：从 outline 展开 `goal/design/acceptance[]/fileScope`，粒度自定但骨架固定；`fileScope` 须遵循模板文件边界约定（供下游 FeatureDesignBuildService 复用） |

实现要点：
- 两个 skill 作为 **LOCAL seed skill**（与 Phase 3 的 `suid`/`eadp-backend` seed 同模式），SKILL.md 内容由平台源码编码实现，启动时 seed 入库并 hash-lock。
- 两个 agent 作为 **builtin agent**（与 `requirement/dispatch/deploy` 同模式），`builtin=true`，删除接口拒绝；instructions 内点名使用各自绑定 skill。
- `SkillMaterializer` 在 spawn `claude` 前，将绑定 skill 的 `SKILL.md` 写入运行目录的 `.claude/skills/<name>/`（功能设计智能体用临时目录，不进 worktree，见 §7 技术前提）。
- 用户可在 Agent 管理 UI 查看这两个内置 agent（只读 badge），但不可删除/改绑定 skill（保证规划/设计产物骨架稳定）。

### 技术前提（本轮锁定）

- `claude` CLI 运行于 **API key 模式**（非订阅登录）。并发只受 API 配额约束，无会话数限制。
- 功能设计智能体**只产 JSON、不读 workspace / 不写代码文件**。因此**不需要 worktree 隔离**，进程可在平台临时目录运行。
- 规划智能体同样只产 JSON，单进程运行。

### 并发策略

- **规划智能体**：单进程，不并发。同一项目同一时刻只允许一个规划运行（§7 并发约束）。
- **功能设计智能体**：确认规划书后**并发批量生成**，复用 Phase 2 的 `CompletableFuture` fan-out 模式 + **有界信号量**限流。

```
// 伪代码：PlanService.confirm
Semaphore permits = new Semaphore(MAX_CONCURRENT_FD);  // 如 4，可配
List<CompletableFuture<Void>> futures = features.stream()
    .map(f -> CompletableFuture.runAsync(() -> {
        permits.acquire();
        try {
          String runId = claudeRunner.spawn(fdAgentConfig, promptFor(f));
          // WS Hub 按 runId 推日志；完成后回调落库 content + status=DRAFT
        } finally { permits.release(); }
    }, executor))
    .toList();
```

- **并发上限 `MAX_CONCURRENT_FD`**：可配置（默认 4），防 API 429 与单机资源耗尽。配额超限时 `claude` 进程的 stderr 捕获 429 → 该条 FeatureDesign 置 `FAILED`，不影响其他条；用户可对失败条单独重试（P9）。
- **`runId` 区分多路流**：每个功能设计智能体一个 `runId`，WS `RunLogFrame` 已带 `runId`（Phase 2），前端按 `featureId` 关联展示各自的生成进度。

### 与现有机制的关系

| 现有机制 | 本流程复用方式 |
|---|---|
| `ClaudeRunner`（spawn `claude` + 流式 stdout/stderr） | 直接复用，规划/设计智能体各配一个 agentConfig |
| WS Hub `/ws/run/{runId}` + `RunLogFrame` | 直接复用，前端按 runId 订阅 |
| 内置 agent 配置体系（Phase 3 的 agent CRUD + skill 绑定） | 规划/设计智能体作为两个内置 agent 注册，prompt 里点名用哪个 skill |
| 技能体系（SKILL.md + hash-lock + materialize） | 规划/设计智能体绑定 `project-planning`/`feature-design` 两个内置 seed skill，内容由平台编码实现（见 §7 内置 agent 与 skill） |
| Phase 2 `CompletableFuture` fan-out | 功能设计批量生成的并发骨架 |

> 不复用 WorktreeManager：功能设计智能体不写代码文件，无需 git worktree 隔离。worktree 隔离在后续"执行编码"（P12/P12a 经 FeatureDesignBuildService）阶段才需要。

## 8. 异步执行与错误处理

- **失败态**：智能体运行失败（含 API 429、进程异常、JSON 解析失败）→ 产物 `status=FAILED`，Project 视情况置 `FAILED`；前端展示失败原因 + "重试"按钮（等价重新生成，`modifyHint` 可空）。批量生成中单条失败不影响其他条。
- **并发约束**：同一项目同一时刻只允许一个规划运行；同一功能设计同时只允许一个设计运行。后端 Service 层用状态前置校验拦截（`GENERATING` 态拒绝再次发起）。
- **版本回溯**：编辑 / 重生成保留历史版本（单表多行 + `is_latest`），规划书与每个功能设计各自独立版本链。

## 9. 成功标准

- **输入**：项目描述字符串。
- **输出**：用户走完"新增→规划书确认→各功能设计确认→执行编码"，全部产物落库且状态正确，"执行编码"成功把功能设计 `fileScope` 交给 `FeatureDesignBuildService`。
- **验证**：在平台 UI 端到端跑通；`plan` / `feature_design` 行状态与聚合的 Project 状态一致；规划书改动后功能设计正确转 `STALE`；批量确认后 `READY_TO_BUILD` 自动达成；**同一 feature 并发触发两次 P12a 时第二次返回 409**；**编码完成后修改设计 → `build_status` 转 `STALE`，重新确认后可再次执行**。
