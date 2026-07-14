# PRD 协作面前端重构 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将需求详情页从「Tab 切换的线性流程」重构为「PRD 单页协作面」：左侧 PRD + 评论流，右侧 Tab 切换执行计划/任务/运行/交付，人类评论作为权威中断信号。

**Architecture:** 容器组件 `RequirementWorkspace` + 集中式 Hook `useRequirementWorkspace`（数据/轮询/WS）+ 纯展示子组件。Service 层按 PRD-loop 新契约重写（评论/执行计划/任务/运行/交付），WS 改为按 `iterationId` 订阅。深度删除 overview/detailed/featureDesign 死代码与 mocks。

**Tech Stack:** React 18 + Umi 4 + `@ead/suid` + `@ead/antd-style` + dva + `@ead/suid-utils-react`(request/useUserContext) + TypeScript(types.ts) / `.jsx`(组件，按项目规范新建文件用 .js/.jsx)。

## 全局约束（Global Constraints）

- 新建文件统一 `.js/.jsx`；仅修改已有 TS 文件时才动 `.ts/.tsx`（`types.ts`、`onlineCodeTypes.ts`、`run-log-socket.ts`、`RequirementDetail.tsx`、`RequirementWorkspace/index.tsx`）。
- 禁止直接用 `antd`，必须用 `@ead/suid` 扩展组件（`ComboList`/`ExtTable`/`Scrollbar`/`Attachment` 等，见 `frontend/CLAUDE.md`）。
- 表格非降级场景用 `ExtTable`；泛型 `ResultData<T>` envelope：`{ success, message, data }`（沿用 `types.ts`）。
- 路由不变：`/online-code/requirement?id=...` → `RequirementDetail.tsx` → `RequirementWorkspace`（容器）。
- 后端契约（已确认，本次前端按实暴露，不新增后端）：
  - `GET  /requirement/findOne?id=`
  - `POST /requirement/{id}/editPrd` `{prdContent}`、`POST /requirement/{id}/confirmPrd`、`POST /requirement/{id}/regeneratePrd` `{prompt}`
  - `POST /requirement/{id}/comments` `{content, metadataJson}` → `RequirementCommentDto`
  - `POST /requirement/{id}/mr/retry`
  - `GET  /requirementComment/requirement/{requirementId}` → `RequirementCommentDto[]`
  - `GET  /executionPlan/requirement/{requirementId}` → `ExecutionPlanDto[]`
  - `GET  /executionPlan/requirement/{requirementId}/latest` → `ExecutionPlanDto`
  - `GET  /coding-task/findOne?id=`、`POST /coding-task/findByPage`、`POST /coding-task/{id}/run` `{userPrompt}`、`POST /coding-task/{id}/rerun` `{rerunPrompt}`、`POST /coding-task/{id}/cancel`
  - `GET  /run/findByCodingTask?codingTaskId=`、`GET /run/findOne?id=`
  - WS：`ws[s]://<host>/ws/run/{iterationId}`，NDJSON `RunLogFrame{iterationId,taskId,runId,stream,line,ts,state?}`
- **已知后端不齐点（待后端补，前端先降级，标注 TODO）**：
  - 无「停止整个自动化」端点 → 停止按钮降级为「批量 `cancel` 当前 `RUNNING` 的 CodingTask」，按钮文案/Tooltip 注明降级，代码留 `// TODO: replace with POST /requirement/{id}/stop once backend exposes it`。
- **测试策略（开工前已确认）**：frontend 无 jest/vitest 测试运行器（`package.json` 无 test script、devDeps 无测试框架）。**不引入测试框架、不写空壳测试**。所有需要单测的逻辑（Task 4 `deriveDelivery`、Task 9 `parsePlanJson`）**抽离为独立纯函数**（顶层 `export function`，无副作用），借助 `pnpm -C frontend tsc --noEmit` 校验签名合法 + 手工对照验证；正式验收靠 Task 12/14 的 `pnpm -C frontend build`。计划中所有形如 `pnpm -C frontend test ...` 的步骤一律改为「`tsc --noEmit` 校验 + 纯函数对照」。
- **tsc 闸门策略（开工前已确认）**：Task 1 重写 `types.ts` 会丢弃老 DTO 类型，但 `RequirementList`/`CodingTaskTab`/`ProjectDetail`/`BuildActions` 等仍引用旧类型直到 Task 13 删除。因此 **Task 2-12 的 `tsc --noEmit` 不要求全绿**，只校验本 Task 新增/修改文件自身可编译（用 `pnpm -C frontend tsc --noEmit` 时允许出现指向未清理老代码的已知报错，记录在 ledger）。Task 13 删完死代码后 `tsc --noEmit` 须全绿，Task 14 `build` 须通过。

## 文件结构（File Structure）

### 新增（按 spec §3.1）
- `components/RequirementWorkspace/useRequirementWorkspace.js` — 集中 Hook：取数/5s 轮询/WS 订阅/action 封装，返回 `requirement/comments/executionPlan/codingTasks/runs/delivery/loading/error/actions`。
- `components/RequirementWorkspace/PrdSection.jsx` — PRD 展示区（替代 `PrdPanel.tsx` 的 Tab 子角色，置入左列）。
- `components/RequirementWorkspace/CommentStream.jsx` — 评论流容器：按 `loopId` 折叠分组。
- `components/RequirementWorkspace/LoopGroup.jsx` — 单 loop 折叠组（header + items）。
- `components/RequirementWorkspace/CommentItem.jsx` — 单条评论：按 `commentType` 渲染。
- `components/RequirementWorkspace/CommentComposer.jsx` — 评论输入（MarkdownEditor）+ 中断警告/danger 按钮/二次确认。
- `components/RequirementWorkspace/AutomationStatusBar.jsx` — 右列顶栏：`automationStatus`/当前 loop/plan version Tag。
- `components/RequirementWorkspace/RightTabs.jsx` — 右侧 Tab 容器。
- `components/RequirementWorkspace/ExecutionPlanTab.jsx` — 执行计划 Tab。
- `components/RequirementWorkspace/TaskTab.jsx` — 任务 Tab（含停止降级按钮）。
- `components/RequirementWorkspace/RunTab.jsx` — 运行 Tab。
- `components/RequirementWorkspace/DeliveryTab.jsx` — 交付 Tab。
- `components/RequirementWorkspace/RunLogDrawer.jsx` — 运行日志 Drawer（WS 流式）。
- `services/requirementComment.js` — `findCommentsByRequirement(requirementId)`。
- `services/executionPlan.js` — `findPlansByRequirement`、`findLatestPlanByRequirement`。

### 修改（已存在）
- `components/RequirementWorkspace/index.tsx` — 重写为容器：`PageHeader` + `WorkspaceLayout`(左 75%/右 25%)，接 `useRequirementWorkspace`，渲染新子组件；移除 Tabs/overview/detailed 调用。
- `components/RequirementWorkspace/types.ts` — 扩展 DTO 与各组件 Props（见 Task 2）。
- `utils/run-log-socket.ts` — 适配新参数：`subscribeRunLog({iterationId, runId, onLine, onTerminal, onError})`，URL `/ws/run/{iterationId}`，frame 类型对齐 `RunLogFrame`。(BuildActions 已证实为孤儿——无人 import 且依赖已删的 `featureDesign`/老 WS 路径，在本计划 Task 13 一并删除，不在 Task 3 改建。)
- `services/requirement.js` — 新增 `addRequirementComment(id, {content, metadataJson})`、`retryMr(id)`、扩展 PRD 现有 3 个方法（已存在）。
- `services/codingTask.js` — 已齐全（run/rerun/cancel/findByPage），无改动。
- `services/run.js` — 已齐全（findByCodingTask/findOne），无改动。

### 删除（深度清理，含 mocks）
- `components/RequirementWorkspace/OverviewDesignPanel.tsx`
- `components/RequirementWorkspace/DetailedDesignPanel.tsx`
- `components/RequirementWorkspace/DesignContextStatusBar.tsx`（仅被 overview/detailed 与 PrdPanel 用）
- `components/RequirementWorkspace/PrdPanel.tsx`（被新 `PrdSection.jsx` 取代）
- `components/RequirementWorkspace/CodingTaskPanel.tsx`（被 `TaskTab.jsx` 取代）
- `components/RequirementWorkspace/RunHistoryPanel.tsx`（被 `RunTab.jsx` 取代）
- `services/overviewDesign.js`、`services/detailedDesign.js`
- `services/memoryRequirementContext.js`（仅 design-context 用）
- `pages/OnlineCode/FeatureDesignTab.tsx`、`DetailedDesignTab.tsx`、`PlanTab.tsx`、`FeatureDesignEditor.tsx`、`Spec.tsx`、`BuildActions.tsx`（孤儿文件，无路由/无引用）
- `services/featureDesign.ts`（由 `BuildActions` 使用，grep 确认无其他引用后随之一并删除）
- `mocks/` 整个目录（`db.ts`、`handlers.ts`、`index.ts`、`browser.ts` 等）及其在入口的引用

### 标记为待清理（不在本计划主路径，但深度删除牵连）—— 单独 Task 收尾
- `pages/OnlineCode/CodingTaskTab.jsx:97` 引用的 `detailedDesignVersion` 列：新 CodingTaskDto 已无此字段 → 删除该列（最小适配，不重构 Tab）。
- `services/onlineCode.ts` 中被 `Spec.tsx` 使用的 `findOneDetailedDesign`/`confirmDetailedDesign`/`regenerateDetailedDesign`/`findDetailedDesignsByProject` 等：随 `Spec.tsx` 删除一并清理（先确认无其他引用，见 Task 末）。
- `services/onlineCodeTypes.ts` 中 `OverviewDesignDto`/`DetailedDesignDto` 等老类型：随引用清空后删除。

---

## Task 1: types.ts — DTO 与本地 Props 类型扩展

**Files:**
- Modify: `components/RequirementWorkspace/types.ts`（整体重写导出）

**Interfaces:**
- Consumes: 后端 DTO（`RequirementDto` 新增 `automationStatus/activeLoopId/acceptedAt/acceptedByAgent/deliveryBranch/deliveryCommitHash/deliveryMrUrl/deliveryTargetBranch`；`RequirementCommentDto`；`ExecutionPlanDto`；新 `CodingTaskDto`（去 `detailedDesignId/detailedDesignVersion`，加 `executionPlanId/planTaskKey/assignedAgent/loopId/area/dependsOn`）；新 `RunDto`（加 `taskId/runType/loopId/cancelRequested/invalidatedByCommentId/memoryContextId/workspaceMemoryId/exitCode/iterationId`）。
- Produces: 全部后续 Task 依赖的类型别名与 Props 接口。

- [ ] **Step 1: 在 `types.ts` 顶部以「覆盖式」替换老 DTO 类型**，新增枚举：

```ts
export type RequirementAutomationStatus =
  | 'IDLE' | 'PLANNING' | 'DEVELOPING' | 'VALIDATING'
  | 'ACCEPTING' | 'DELIVERING' | 'INTERRUPTED' | 'WAITING_HUMAN'
  | 'COMPLETED' | 'FAILED';

export type RequirementStatus = 'PRD_GENERATING' | 'PRD_REVIEW' | 'PRD_CONFIRMED' | 'FAILED';

export interface RequirementDto {
  id: string; projectId: string; title: string; description?: string | null;
  status: RequirementStatus; automationStatus?: RequirementAutomationStatus | null;
  prdVersion?: number; prdContent?: string | null; designContextId?: string | null;
  memoryValidationStatus?: 'NOT_RUN'|'PASSED'|'WARNING'|'FAILED' | null;
  memoryValidationResultJson?: string | null;
  activeLoopId?: string | null;
  acceptedAt?: string | null; acceptedByAgent?: string | null;
  deliveryBranch?: string | null; deliveryCommitHash?: string | null;
  deliveryMrUrl?: string | null; deliveryTargetBranch?: string | null;
  failureSummary?: string | null; createdDate: string; lastEditedDate: string;
}

export type RequirementCommentAuthorType =
  | 'HUMAN' | 'PM_AGENT' | 'FRONTEND_AGENT' | 'BACKEND_AGENT' | 'TEST_AGENT' | 'SYSTEM';
export type RequirementCommentType =
  | 'HUMAN_FEEDBACK' | 'EXECUTION_PLAN' | 'DEV_RESULT' | 'VALIDATION_RESULT'
  | 'ACCEPTANCE' | 'REMEDIATION' | 'INTERRUPTION' | 'FAILURE'
  | 'MR_CREATED' | 'MR_UPDATED' | 'MR_FAILED'
  | 'MEMORY_UPDATED' | 'MEMORY_UPDATE_FAILED' | 'CONTEXT_SUMMARY_FAILED';

export interface RequirementCommentDto {
  id: string; requirementId: string; loopId?: string | null;
  authorType: RequirementCommentAuthorType; authorName?: string | null;
  commentType: RequirementCommentType; content?: string | null;
  metadataJson?: string | null; createdDate: string;
}

export type ExecutionPlanType = 'INITIAL' | 'REMEDIATION' | 'CHANGE_REQUEST';
export type ExecutionPlanStatus =
  | 'PLANNING' | 'READY' | 'DEVELOPING' | 'ACCEPTING'
  | 'NEEDS_REMEDIATION' | 'ACCEPTED' | 'INTERRUPTED' | 'FAILED';

export interface ExecutionPlanTask {
  taskKey: string; title: string; description?: string | null;
  agent: string; area: string; dependsOn?: string[];
  fileScope?: string[]; acceptanceCriteria?: string[];
}
export interface ExecutionPlanJson {
  goal?: string | null;
  tasks?: ExecutionPlanTask[];
  risks?: string[];
  validation?: string | null;
}
export interface ExecutionPlanDto {
  id: string; requirementId: string; loopId?: string | null;
  version?: number; planType: ExecutionPlanType; status: ExecutionPlanStatus;
  planJson?: string | null; summary?: string | null; createdByAgent?: string | null;
  memoryContextId?: string | null; workspaceMemoryId?: string | null; createdDate: string;
}

export type CodingTaskStatus =
  | 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  | 'VALIDATION_FAILED' | 'CANCELLED' | 'STALE' | 'BLOCKED';
export interface CodingTaskDto {
  id: string; projectId: string; requirementId: string; status: CodingTaskStatus;
  title: string; description?: string | null; fileScope?: string[] | null;
  area?: string | null; dependsOn?: string[] | null;
  executionPlanId?: string | null; planTaskKey?: string | null;
  assignedAgent?: string | null; loopId?: string | null;
  failureSummary?: string | null; createdDate: string; lastEditedDate: string;
}

export type RunType =
  | 'DEVELOPMENT' | 'VALIDATION_COMMAND' | 'TEST_REVIEW'
  | 'PM_PLANNING' | 'PM_ACCEPTANCE' | 'DELIVERY';
export type RunState = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export interface RunDto {
  id: string; taskId?: string | null; codingTaskId?: string | null; requirementId?: string | null;
  runNo?: number; triggerSource?: string | null; runType?: RunType | null; loopId?: string | null;
  cancelRequested?: boolean | null; invalidatedByCommentId?: string | null;
  memoryContextId?: string | null; workspaceMemoryId?: string | null;
  userPrompt?: string | null; failureSummary?: string | null; failureReason?: string | null;
  iterationId?: string | null; state?: RunState; worktreePath?: string | null;
  exitCode?: number | null; startedDate?: string | null; finishedDate?: string | null;
}

export interface DeliverySummary {
  mrUrl?: string | null; branch?: string | null; commitHash?: string | null;
  targetBranch?: string | null; status: 'NONE' | 'PENDING' | 'CREATED' | 'UPDATED' | 'FAILED';
}
```

- [ ] **Step 2: 删除老类型与老 Props**（`WorkspaceTab` 改为右列 Tab、`OverviewDesignPanelProps`/`DetailedDesignPanelProps` 删除、`PrdPanelProps` 改 `PrdSectionProps`、新 `Comment/RightTabs/Task/Run/Delivery/Plan` Props）。新 Props 骨架：

```ts
export type RightTab = 'plan' | 'task' | 'run' | 'delivery';

export interface PrdSectionProps { requirement: RequirementDto; onConfirm: () => Promise<void>; onEdit: (c: string) => Promise<void>; onRegenerate: (p: string) => Promise<void>; }
export interface CommentStreamProps { comments: RequirementCommentDto[]; activeLoopId?: string | null; requirement: RequirementDto; onSend: (content: string) => Promise<void>; sending: boolean; onJumpPlan?: () => void; onHighlightTask?: (taskKey: string) => void; }
export interface LoopGroupProps { loopId: string; comments: RequirementCommentDto[]; active: boolean; planVersion?: number | null; }
export interface CommentItemProps { comment: RequirementCommentDto; onJumpPlan?: () => void; onHighlightTask?: (taskKey: string) => void; }
export interface CommentComposerProps { requirement: RequirementDto; onSend: (c: string) => Promise<void>; sending: boolean; }
export interface AutomationStatusBarProps { status?: RequirementAutomationStatus | null; activeLoopId?: string | null; planVersion?: number | null; }
export interface RightTabsProps { plan: ExecutionPlanDto | null; tasks: CodingTaskDto[]; runs: RunDto[]; delivery: DeliverySummary; onRunLog: (run: RunDto) => void; onRun: (t: CodingTaskDto) => Promise<void>; onRerun: (t: CodingTaskDto, p: string) => Promise<void>; onStop: () => Promise<void>; onRetryMr: () => Promise<void>; autoStopEnabled: boolean; highlightTaskKey?: string | null; onHighlightTaskConsumed?: () => void; }
export interface ExecutionPlanTabProps { plan: ExecutionPlanDto | null; tasks: CodingTaskDto[]; onJumpTask?: (taskKey: string) => void; }
export interface TaskTabProps { tasks: CodingTaskDto[]; onRun; onRerun; onViewRun; onStop: () => Promise<void>; stopEnabled: boolean; highlightTaskKey?: string | null; onHighlightTaskConsumed?: () => void; }
export interface RunTabProps { runs: RunDto[]; onOpenLog: (run: RunDto) => void; }
export interface DeliveryTabProps { delivery: DeliverySummary; comments: RequirementCommentDto[]; onRetryMr: () => Promise<void>; }
export interface RunLogDrawerProps { open: boolean; run: RunDto | null; onClose: () => void; }
```

- [ ] **Step 3: `pnpm -C frontend tsc --noEmit` 校验类型**（此时旧引用会报错，记下将在后续 Task 清理，本步只确认 `types.ts` 自身编译通过：可临时新建一个空的 `__typecheck_test.ts` import 全部导出后删除）。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/OnlineCode/components/RequirementWorkspace/types.ts
git commit -m "refactor(workspace): rewrite types.ts for PRD-loop contract"
```

---

## Task 2: service 层 — 评论/执行计划/MR/PRD 扩展

**Files:**
- Create: `services/requirementComment.js`
- Create: `services/executionPlan.js`
- Modify: `services/requirement.js`（新增 `addRequirementComment`、`retryMr`）

**Interfaces:**
- Consumes: `request` from `@ead/suid-utils-react`、`PROJECT_SERVER_PATH` from `@/utils/constants`。
- Produces: `findCommentsByRequirement(id): Promise<ResultData<RequirementCommentDto[]>>`、`findPlansByRequirement(id)`、`findLatestPlanByRequirement(id)`、`addRequirementComment(id,{content,metadataJson?})`、`retryMr(id)`。

- [ ] **Step 1: 写 service**（`requirementComment.js`、`executionPlan.js`、`requirement.js` 增量）；每个函数对应后端路径，`request` 用 GET/POST。

- [ ] **Step 2: 手写最小冒烟测试** — `frontend/src/services/__smoke.test.js`（仅确认函数存在、URL 字符串拼正确，不发真实请求；用 `import` 验证不抛错）。Reason: 锁定 service 签名不被后续改动破坏。

- [ ] **Step 3: 运行** `pnpm -C frontend test services/__smoke`（如无现成测试 runner，则 `pnpm -C frontend exec tsc --noEmit` 校验 import 合法）。

- [ ] **Step 4: Commit** `git add ... && git commit -m "feat(service): add requirementComment/executionPlan/mr-retry services"`

---

## Task 3: run-log-socket.ts 适配 iterationId + RunLogFrame 对齐

**Files:**
- Modify: `utils/run-log-socket.ts`

**说明：** `BuildActions.tsx` 经 grep 确认无任何 import 引用方，且依赖已不存在的 `FeatureDesignDto`/`featureDesign`，属孤儿组件 —— 本计划不动它，Task 13 与 `featureDesign.js` 一并删除。

- [ ] **Step 1: 改 `RunLogFrame`** 为 `{iterationId, taskId?, runId?, stream, line, ts, state?}`；`RunLogSocketOptions` 的 `featureDesignId` → `iterationId`；WS URL `/ws/run/${iterationId}`；过滤 `frame.runId === runId`（runId 为空时返回全部帧）。

- [ ] **Step 2: 校验** `pnpm -C frontend tsc --noEmit`（`buildActions` 此处因孤儿仍失败，记入 Task 13；本步确认 `run-log-socket.ts` 自身可编译——若有 .ts 报错源于 BuildActions 引用旧 frame 字段，在 Task 13 删除后自消失，不回填）。

- [ ] **Step 3: Commit** `git commit -m "refactor(ws): switch run-log socket to iterationId"`

---

## Task 4: useRequirementWorkspace.js — 集中式数据 Hook

**Files:**
- Create: `components/RequirementWorkspace/useRequirementWorkspace.js`

**Interfaces:**
- Consumes: Task 2 services、`run.js`/`codingTask.js`、`subscribeRunLog`(Task 3 用于 run 状态增量——本 Hook 只负责数据，WS 由 RunLogDrawer 自管，Hook 不订阅 WS)。
- Produces:
```js
{
  requirement, comments, executionPlan, codingTasks, runs, delivery, loading, error,
  activeLoopId, planVersion,
  actions: { sendComment(content), confirmPrd(), editPrd(c), regeneratePrd(p),
             runTask(t), rerunTask(t,p), cancelTask(t), stopAutomation(),
             retryMr(), refresh() }
}
```
- 轮询：`automationStatus ∈ {PLANNING,DEVELOPING,VALIDATING,ACCEPTING,DELIVERING}` → 5s；否则 30s；页面不可见（`document.hidden`）暂停。
- `delivery` 由 requirement 字段 + 评论流 `MR_*`/`MEMORY_*` 推导：`mrUrl=deliveryMrUrl`、`branch=deliveryBranch`、`commitHash=deliveryCommitHash`、`targetBranch=deliveryTargetBranch`，`;status` 取最新 `MR_CREATED/UPDATED/FAILED` 评论或 requirement `WAITING_HUMAN && mrFailed` 推 `FAILED`，否则 `NONE`。
- `stopAutomation()`: 收集 `codingTasks.filter(t=>t.status==='RUNNING')` → 并行 `cancelCodingTask`，刷新。代码首行注释 `// TODO: replace with POST /requirement/{id}/stop once backend exposes it`。

- [ ] **Step 1: 写 Hook**（`useState`×7、`useEffect` 取数、`useRef` 定时器、`useCallback` actions、`useMemo` delivery/activeLoopId/planVersion）。

- [ ] **Step 2: 单测**（纯函数 `deriveDelivery(requirement, comments)` 抽出 → 测 4 种状态推导：无 MR/有 CREATED/有 FAILED/WAITING_HUMAN+MR_FAILED）。

- [ ] **Step 3: 校验** `pnpm -C frontend test useRequirementWorkspace` 通过。

- [ ] **Step 4: Commit** `git commit -m "feat(workspace): central hook useRequirementWorkspace"`

---

## Task 5: PrdSection.jsx — PRD 展示区

**Files:**
- Create: `components/RequirementWorkspace/PrdSection.jsx`

**Interfaces:** Props `PrdSectionProps`（Task 1）。复用 `MarkdownEditor`、`Tag`、`Button`。行为同旧 `PrdPanel` 但去掉 `DesignContextStatusBar`（依赖已删）、去 Card 外壳（左列已约束），保留编辑/确认/重生成。

- [ ] Step 1: 写组件（含 `STATUS_META` for `RequirementStatus`、编辑态切换、确认 `confirmPrd`、重生成弹 `prompt`、保存 `editPrd`）。
- [ ] Step 2: 校验 import 不报未定义。
- [ ] Step 3: `pnpm -C frontend tsc --noEmit`。
- [ ] Step 4: Commit `feat(workspace): add PrdSection`.

---

## Task 6: CommentItem.jsx + LoopGroup.jsx — 评论渲染

**Files:**
- Create: `CommentItem.jsx`、`LoopGroup.jsx`

**Interfaces:** Props `CommentItemProps`/`LoopGroupProps`。CommentItem 按 `commentType` 渲染（spec §5.2 表），人类评论用户头像/名称、agent 评论用机器人图标 + agentName；MR URL/文件路径自动链接（最小：用正则替换）。metadataJson `JSON.parse` 容错。LoopGroup 用 `Collapse`，active 默认展开，历史默认收起，header 显示 loopId 短码/时间/plan 版本/最终状态（由该 loop 评论中最新 `ACCEPTANCE/FAILURE/INTERRUPTION` 推断）。

- [ ] Step 1: 写 CommentItem（一张映射表 `COMMENT_META` 决定颜色/图标/标题模板）。
- [ ] Step 2: 写 LoopGroup（用 `@ead/suid` `Collapse`）。
- [ ] Step 3: tsc。
- [ ] Step 4: Commit `feat(workspace): add CommentItem and LoopGroup`.

---

## Task 7: CommentStream.jsx + CommentComposer.jsx — 评论流与输入

**Files:**
- Create: `CommentStream.jsx`、`CommentComposer.jsx`

**Interfaces:**
- `CommentStream`: 接 `comments` → 按 `loopId` 分组（`null` 归一为 `'(no-loop)'`）→ 按 `createdDate` 正序 → 当前 `activeLoopId` 展开，其余收起 → 渲染 `LoopGroup[]`，底部 `CommentComposer`。评论数 >100 用虚拟列表（最小：`React.memo` + 注释 TODO 虚拟化，避免提前优化）。
- `CommentComposer`: `MarkdownEditor`；当 `requirement.automationStatus ∈ {PLANNING,DEVELOPING,VALIDATING,ACCEPTING}` 或 status=`COMPLETED` → 顶部 ` Alert` 警告条（文案随 status 变：活跃=「发送将中断当前自动化并重规划」、COMPLETED=「将基于现有 MR 创建变更请求」），发送 `Button danger`，点击 `Modal.confirm` 二次确认；提交成功清空 + 滚到底部（回调 `onSend`）。

- [ ] Step 1: 写 CommentComposer（含警告文案映射表 `INTERRUPT_WARNING`）。
- [ ] Step 2: 写 CommentStream（分组+排序+虚拟化占位注释）。
- [ ] Step 3: tsc。
- [ ] Step 4: Commit `feat(workspace): add CommentStream and CommentComposer`.

---

## Task 8: AutomationStatusBar.jsx + RightTabs.jsx

**Files:**
- Create: `AutomationStatusBar.jsx`、`RightTabs.jsx`

**Interfaces:**
- `AutomationStatusBar`: Tag 显示 `automationStatus`（颜色映射 `AUTOMATION_STATUS_META`）、当前 loop 短码、最新 plan version。
- `RightTabs`: `Tabs` 4 个 key(`plan|task|run|delivery`)，把 4 个 Tab 组件接入 props（含 `onRunLog/onRun/onRerun/onStop/onRetryMr/highlight...`）；管理 `activeTab` 内部 + 暴露 `onJumpPlan`/`onHighlightTask`（由父容器传 ref 或 props 回调切换 Tab）。

- [ ] Step 1: 写 AutomationStatusBar。
- [ ] Step 2: 写 RightTabs（forwardRef 暴露 `switchTo(tab, taskKey?)`）。
- [ ] Step 3: tsc。
- [ ] Step 4: Commit `feat(workspace): add AutomationStatusBar and RightTabs`.

---

## Task 9: ExecutionPlanTab.jsx + TaskTab.jsx

**Files:**
- Create: `ExecutionPlanTab.jsx`、`TaskTab.jsx`

**Interfaces:**
- `ExecutionPlanTab`: 解析 `plan.planJson` → `{goal,tasks,risks,validation}`（容错）。顶部 `Card`：`goal`、`planType` Tag、`status` Tag、`risks` 列表、`validation` 折叠。下方 `ExtTable`：列 `taskKey/title/agent/area/status`(status 来自 `tasks[]` 中按 `planTaskKey` 匹配 `CodingTaskDto`)；行展开 `dependsOn/fileScope/acceptanceCriteria`。点击行 `onJumpTask(taskKey)`。
- `TaskTab`: 顶部工具栏：任务统计 Tag + 停止自动化 `Button danger`（`stopEnabled = tasks.some(RUNNING)`，禁用时 Tooltip「无可停止的运行任务」+ 注 `// TODO`）。`ExtTable` 列：`taskKey/title/agent/area/status`，失败显示 `failureSummary`，阻塞显示依赖失败原因；行展开最新 `VALIDATION_RESULT`；操作列 `运行/重跑(必填 prompt)/查看运行/查看代码片段`。`highlightTaskKey` 命中时高亮该行（`rowClassName`）+ `onHighlightTaskConsumed` 清空。

- [ ] Step 1: 写 ExecutionPlanTab（含 `parsePlanJson` 纯函数 + 单测解析容错）。
- [ ] Step 2: 写 TaskTab。
- [ ] Step 3: `pnpm -C frontend test ExecutionPlanTab`（parsePlanJson 用例）。
- [ ] Step 4: Commit `feat(workspace): add ExecutionPlanTab and TaskTab`.

---

## Task 10: RunTab.jsx + RunLogDrawer.jsx

**Files:**
- Create: `RunTab.jsx`、`RunLogDrawer.jsx`

**Interfaces:**
- `RunTab`: 按 `runType` 过滤 `Segmented`/`FilterView`；`ExtTable` 列 `runNo/runType/state/triggerSource/持续时间/startedDate`；点行 `onOpenLog(run)`。
- `RunLogDrawer`: `Drawer` 右出 720px，顶部 Run 元信息；主体等宽日志区（`Scrollbar` 或 `<pre>`）；`useEffect` 在 open 时 `subscribeRunLog({iterationId: run.iterationId, runId: run.id, ...})`，onLine 追加行、onTerminal 标记结束；close 时 `socket.close()`。run.iterationId 缺失时显示静态提示「无运行日志通道」。

- [ ] Step 1: 写 RunTab。
- [ ] Step 2: 写 RunLogDrawer（WS 增量追加 + 终态处理 + 清理）。
- [ ] Step 3: tsc。
- [ ] Step 4: Commit `feat(workspace): add RunTab and RunLogDrawer`.

---

## Task 11: DeliveryTab.jsx

**Files:**
- Create: `DeliveryTab.jsx`

**Interfaces:** Props `DeliveryTabProps`。顶部 `Card`：MR `status` Tag、`branch`、`commitHash`、`deliveryMrUrl`(外链 `a`)、`重试 MR` `Button`（`POST /requirement/{id}/mr/retry` via `onRetryMr`）。下方折叠事件列表：从 `comments` 过滤 `MR_CREATED/MR_UPDATED/MR_FAILED/MEMORY_UPDATED/MEMORY_UPDATE_FAILED` 倒序展示。

- [ ] Step 1..4 同上模板。Commit `feat(workspace): add DeliveryTab`.

---

## Task 12: index.tsx 容器重写

**Files:**
- Modify: `components/RequirementWorkspace/index.tsx`

**Interfaces:** 接 `useRequirementWorkspace`；布局 `PageHeader` + 两列 flex（左 75% `PrdSection`+`CommentStream`、右 25% `AutomationStatusBar`+`RightTabs`）；删 Tabs/overview/detailed/codingTasks-loadAll/findRunsByCodingTask 老逻辑。`RightTabs ref` 透传 `onJumpPlan`/`onHighlightTask`（由 CommentStream 回调度）。响应式：≥1440 双列；<1280 单列 + 悬浮按钮打开右抽屉（用 `Drawer`，最小实现）。

- [ ] Step 1: 重写 `index.tsx`。
- [ ] Step 2: `pnpm -C frontend tsc --noEmit` + `pnpm -C frontend build`（核心验收）。
- [ ] Step 3: Commit `refactor(workspace): rewrite container to PRD collaboration layout`.

---

## Task 13: 深度清理 overview/detailed/featureDesign/mocks

**Files:**
- Delete: task「删除」清单全部文件。
- Modify: `pages/OnlineCode/CodingTaskTab.jsx:97`（删 `detailedDesignVersion` 列）。
- Modify: `services/onlineCode.ts`（删除 `findOneDetailedDesign/confirmDetailedDesign/regenerateDetailedDesign/findDetailedDesignsByProject` 及被 `Spec.tsx` 使用的导出）—— 删前列 grep 确认无其他引用。
- Modify: `services/onlineCodeTypes.ts`（删 `OverviewDesignDto/DetailedDesignDto/SpecDto/PlanDto` 等老类型——按 grep 引用确认）。
- Modify: 凡 import `mocks` 的入口（grep `from '@/mocks'`/`setupMock`）移除并清理 `app.ts`/`global.ts` 中的 mock boot。

- [ ] Step 1: `grep -rln` 确认 `mocks`、`onlineCode.ts 老 export`、老 types 的引用方都已不在 → 逐个删除文件/导出。
- [ ] Step 2: 改 `CodingTaskTab.jsx` 列定义。
- [ ] Step 3: `pnpm -C frontend tsc --noEmit` + `pnpm -C frontend build` 全绿。
- [ ] Step 4: Commit `chore(workspace): remove legacy overview/detailed/featureDesign mocks`.

---

## Task 14: 验收与可访问性收尾

- [ ] `pnpm -C frontend build` 通过。
- [ ] `pnpm -C frontend tsc --noEmit` 通过。
- [ ] 路由 `/online-code/requirement?id=...` 加载：PRD/评论/执行计划/任务/运行/交付可见。
- [ ] 活跃自动化下人类评论触发 `Alert` + `Button danger` + `Modal.confirm`。
- [ ] 轮询在 `automationStatus` 变化时刷新（手测断点或日志）。
- [ ] 图标按钮 `aria-label`、颜色不唯一信号（图标+文字）、Markdown XSS 净化（`MarkdownEditor` 已实现，确认未退化）。
- [ ] Commit `test(workspace): acceptance checklist for PRD collaboration UI`.

---

## Self-Review（计划自检）

**1. Spec 覆盖：** spec §2 决策逐条 → Task 1(类型)/4(Hook 轮询 WS)/5+7(评论输入)/6+7(分组折叠)/8+9(右侧 Tab)/10(Drawer)/9(停止降级)/12(布局) 覆盖；§5 评论渲染 → Task 6/7；§6 右侧面板 → Task 9/10/11；§7 中断/变更请求 → Task 4(sendComment 后刷新)+7(COMPLETED 文案)；§8 A11y/响应式 → Task 12+14；§11 待清理 → Task 13。
**2. 占位符扫描：** 编码步骤都给了精确文件/接口/验证命令；纯函数（`deriveDelivery`/`parsePlanJson`）给了测试方向。组件内部 JSX 视觉细节按 spec §5.2/§6 表落地，不在计划重复 spec 全文。
**3. 类型一致：** `RightTab`、`RequirementAutomationStatus`、`RequirementCommentType`、`ExecutionPlanJson` 等在 Task 1 定义，后续 Task 按名引用。`stopAutomation`(Task 4) 与 `RightTabs.onStop`(Task 1) 签名一致。
**4. 已知后端不齐：** `stopAutomation` 降级 + TODO（Task 4/9）。WS 改造（Task 3）。两者已在全局约束与对应 Task 注释。