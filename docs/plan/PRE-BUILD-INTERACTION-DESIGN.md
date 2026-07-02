# 编码前交互设计 — 规划书 + 功能设计（两层产物）

> 状态：设计稿（待评审）。覆盖编码前的用户交互流程，止于"功能设计 → 现有 Dispatch"。
> 不重做编码侧（Dispatch/Task/Run）。后端遵循 `eadp-backend`；前端遵循 `suid`。

## 1. 背景与定位

现有平台领域模型为 Project Design → Spec（需求智能体细化）→ Dispatch → Task/Run → 编码。
本次**重新设计编码前前置流程**，抛开现有 Spec/Task 模型，按"规划书 + 功能设计"两层重做：

- 用户新增项目 → 系统保存（状态：生成中）→ 后端用智能体 + 技能规划 → 产出**项目规划书**并落库（状态：规划中）
- 用户可查看 / 修改 / 重新生成规划书
- 用户确认规划书 → 针对规划书每个功能生成相应**功能设计**（状态：功能设计中）
- 用户可对每个功能设计查看 / 修改 / 重新生成
- 用户全部确认后点"执行编码" → 交现有 Dispatch 开始编码

### 范围边界

- 前置流程止于"功能设计 → 现有 Dispatch/Task/Run"，编码侧不动。
- 功能设计粒度由规划智能体自定（份数与结构不预设）。
- 修改 / 重新生成保留版本历史。
- 规划与功能设计生成为异步智能体运行，进度 / 日志复用现有 WS Hub。

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

智能体自定粒度，但固定骨架，落库于 `feature_design` 表：

```
{
  featureId:  string,                // 与 plan.content.features[].featureId 对应
  goal:       string,                // 该功能的用户故事
  design:     object,                // 页面/组件/交互/数据/接口契约片段（智能体按粒度填）
  acceptance: string[],              // 验收点
  fileScope:  string[]               // 编码时触及的文件边界，最终交给 Dispatch
}
```

## 3. 状态机

### 项目状态 `ProjectState`（编码前部分，编码侧复用现有）

```
DRAFTING → PLANNING → DESIGNING → READY_TO_BUILD → [交现有 Dispatch]
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

### 规划书 / 功能设计失效规则

- Plan 编辑或重新生成提交 → Plan = `DRAFT`；该项目下所有 FeatureDesign 置 `STALE`；Project 退回 `PLANNING`。
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
                              │            └─[执行编码] → 交现有 Dispatch
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
| 执行编码 | — | 校验 READY_TO_BUILD；交现有 Dispatch |

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
| P8 | PUT | `/api/featureDesign/{id}` | 编辑 content；该条→DRAFT |
| P9 | POST | `/api/featureDesign/{id}/regenerate` | 重生，body: `{modifyHint?}`；version+1，=GENERATING 起运行 |
| P10 | POST | `/api/featureDesign/confirm` | 批量确认，body: `{ids:[]}`；逐条→CONFIRMED；全部 CONFIRMED 则 Project=READY_TO_BUILD |
| P11 | POST | `/api/featureDesign/{id}/confirm` | 单条确认（P10 的便捷版） |
| P12 | POST | `/api/project/{projectId}/build` | 执行编码；校验 READY_TO_BUILD，交现有 Dispatch |
| P13 | GET | `/api/plan/{projectId}/history` | 规划书历史版本列表 |
| P14 | GET | `/api/featureDesign/{id}/history` | 功能设计历史版本列表 |

异步运行结果通过现有 WS Hub（`/ws/run/{runId}`）推送，产物落库后前端按 P2 / P7 刷新。

## 7. 异步执行与错误处理（复用现有机制）

- **异步运行**：规划 / 设计智能体运行复用现有 `ClaudeRunner` + WS Hub 模式，前端按 `runId` 订阅日志流。生成完成回调更新产物 `content` 并 `status=DRAFT`。
- **失败态**：智能体运行失败 → 产物 `status=FAILED`，Project 视情况置 `FAILED`；前端展示失败原因 + "重试"按钮（等价重新生成，`modifyHint` 可空）。
- **并发约束**：同一项目同一时刻只允许一个规划运行；同一功能设计同时只允许一个设计运行。后端 Service 层用状态前置校验拦截（`GENERATING` 态拒绝再次发起）。
- **版本回溯**：编辑 / 重生成保留历史版本（单表多行 + `is_latest`），规划书与每个功能设计各自独立版本链。

## 8. 成功标准

- **输入**：项目描述字符串。
- **输出**：用户走完"新增→规划书确认→各功能设计确认→执行编码"，全部产物落库且状态正确，"执行编码"成功把功能设计 `fileScope` 交给现有 Dispatch。
- **验证**：在平台 UI 端到端跑通；`plan` / `feature_design` 行状态与聚合的 Project 状态一致；规划书改动后功能设计正确转 `STALE`；批量确认后 `READY_TO_BUILD` 自动达成。
