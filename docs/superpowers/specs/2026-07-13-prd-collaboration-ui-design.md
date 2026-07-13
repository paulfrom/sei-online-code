# PRD Comment-Driven Agent Loop — Frontend UI Design

> 设计日期：2026-07-13  
> 关联 PRD：`docs/superpowers/plans/2026-07-13-prd-comment-driven-agent-loop.md`  
> 范围：需求详情页（RequirementDetail / RequirementWorkspace）的前端界面与交互设计。

---

## 1. 设计目标

将需求详情页从“Tab 切换的线性流程（PRD → 概览设计 → 详细设计 → 编码任务 → 运行历史）”改造成 **PRD 单页协作面**：

- PRD 内容与人类/Agent 评论在同一视线内。
- 执行计划、任务状态、运行历史、MR 交付状态集中展示在右侧。
- 人类评论成为权威中断信号，UI 必须明确提示其后果。
- 变更请求（completed 后的人类评论）与正常中断流程提示区分开。

---

## 2. 核心决策

| 决策项 | 选择 | 理由 |
|---|---|---|
| 整体布局 | 左侧 75% PRD + 评论流，右侧 25% 执行计划/任务/运行/交付 | PRD 与评论是协作核心，其他信息作为辅助侧栏 |
| 左侧内部 | PRD 在上，评论流在下，上下堆叠 | 评论作为 PRD 的上下文延伸，阅读顺序自然 |
| 评论分组 | 按 `loopId` 折叠分组 | loop 是中断/重跑/过期的核心标识 |
| 评论排序 | loop 内正序，当前 loop 展开，历史 loop 收起 | 叙事顺序清晰，避免历史干扰 |
| 实时更新 | WebSocket 仅用于 Run 日志流，评论/状态用 5s 轮询 | 复用现有 `RunLogWebSocketHub`，降低后端改动 |
| 打断提示 | 警告条 + 危险发送按钮 + 二次确认弹窗 | “权威中断”必须强提示，防止误操作 |
| 评论输入 | 复用现有 `MarkdownEditor` | 与 PRD 编辑体验一致，支持代码片段 |
| 右侧组织 | Tab 切换：执行计划 / 任务 / 运行 / 交付 | 右侧宽度有限，Tab 保证每个视图横向空间 |
| 运行日志 | 右侧 Drawer 展开 | 临时深入查看，不离开主协作面 |
| 停止自动化 | 放在“任务”Tab 工具栏，非活跃时禁用 + Tooltip | 符合“控制开发流程”的上下文 |
| 组件/状态组织 | 容器 + 纯展示子组件 + `useRequirementWorkspace` Hook | 边界清晰、易测试，避免单文件过大 |

---

## 3. 布局与组件拆分

```
RequirementDetail (URL wrapper)
└── RequirementWorkspace (容器)
    ├── PageHeader（标题 + 返回 + 状态 Tag）
    └── WorkspaceLayout（flex 主区域）
        ├── LeftColumn（75%）
        │   ├── PrdSection
        │   │   ├── PrdHeader（状态、版本、编辑/确认/重生成）
        │   │   └── MarkdownEditor（PRD 内容）
        │   └── CommentStream
        │       ├── LoopGroup（按 loopId 折叠）
        │       │   ├── LoopHeader（loop 版本、时间、最终状态）
        │       │   └── CommentItem（不同 authorType/commentType）
        │       └── CommentComposer（Markdown 编辑器 + 打断警告）
        └── RightColumn（25%）
            ├── AutomationStatusBar（automationStatus、当前 loop、plan version）
            └── RightTabs
                ├── ExecutionPlanTab
                ├── TaskTab
                ├── RunTab
                └── DeliveryTab
```

### 3.1 新增/修改文件

按前端项目规范，新增文件统一使用 `.js/.jsx`。

| 文件 | 说明 |
|---|---|
| `components/RequirementWorkspace/index.tsx` | 保留扩展名，重写为容器，负责数据聚合与副作用 |
| `components/RequirementWorkspace/useRequirementWorkspace.js` | 数据获取、轮询、WebSocket、action 封装 |
| `components/RequirementWorkspace/types.ts` | 扩展本地类型（现有文件） |
| `components/RequirementWorkspace/PrdSection.jsx` | PRD 展示区 |
| `components/RequirementWorkspace/CommentStream.jsx` | 评论流容器 |
| `components/RequirementWorkspace/LoopGroup.jsx` | loop 折叠组 |
| `components/RequirementWorkspace/CommentItem.jsx` | 单条评论渲染 |
| `components/RequirementWorkspace/CommentComposer.jsx` | 评论输入与打断提示 |
| `components/RequirementWorkspace/RightTabs.jsx` | 右侧 Tab 容器 |
| `components/RequirementWorkspace/ExecutionPlanTab.jsx` | 执行计划 |
| `components/RequirementWorkspace/TaskTab.jsx` | 任务列表 |
| `components/RequirementWorkspace/RunTab.jsx` | 运行历史 |
| `components/RequirementWorkspace/DeliveryTab.jsx` | 交付状态 |
| `components/RequirementWorkspace/RunLogDrawer.jsx` | 运行日志详情 Drawer |
| `services/requirementComment.js` | 评论相关 API |
| `services/executionPlan.js` | 执行计划相关 API |

### 3.2 布局 CSS 要点（@ead/antd-style）

- 主容器：`display: flex; height: 100%; gap: token.marginMD`。
- 左列：`flex: 3; min-width: 0; display: flex; flex-direction: column; gap: token.marginMD`。
- 右列：`flex: 1; min-width: 280px; max-width: 360px; display: flex; flex-direction: column`。
- 两列均 `overflow: auto`，各自独立滚动。

---

## 4. 数据流与状态管理

### 4.1 集中式 Hook：`useRequirementWorkspace`

```js
const workspace = useRequirementWorkspace(requirementId);
```

返回字段：

| 字段 | 说明 |
|---|---|
| `requirement` | 需求主体，含 `automationStatus`、`activeLoopId`、`status` |
| `comments` | 全部评论，已按 `loopId` 分组 |
| `executionPlan` | 当前最新执行计划 |
| `codingTasks` | 当前计划的任务列表 |
| `runs` | 全部 runs |
| `delivery` | MR/内存更新状态聚合 |
| `loading / error` | 页面级加载与错误 |
| `actions` | `sendComment`、`stopAutomation`、`retryMr`、`rerunTask`、`refresh` 等 |

### 4.2 数据获取策略

- **初始加载**：并行请求需求、评论、最新执行计划、任务、runs。
- **轮询**：`automationStatus` 活跃时 5s 刷新，非活跃时 30s。
- **WebSocket**：复用现有 Run 日志通道，仅增量刷新 runs 和任务状态。
- **评论提交**：乐观更新本地评论，随后刷新确认。

### 4.3 状态更新规则

- `activeLoopId` 变化后，所有子组件以最新 loop 为准；旧 loop 评论/任务标记为历史。
- 人类评论触发中断时，前端提交成功后立即刷新，等待后端生成 `INTERRUPTION` 系统评论和新 loop。
- 操作类 API（停止、重试 MR、重跑任务）成功后统一调用 `refresh()`。

### 4.4 子组件契约

子组件只接收 props，不直接调用 service：

```jsx
<PrdSection requirement={...} onConfirm={actions.confirmPrd} />
<CommentStream comments={...} activeLoopId={...} automationStatus={...} onSend={actions.sendComment} />
<RightTabs plan={...} tasks={...} runs={...} delivery={...} onRunLogOpen={...} />
```

---

## 5. 评论流与评论输入

### 5.1 评论分组

- 按 `loopId` 分组折叠。
- loop 内按 `createdDate` 正序。
- 当前 `activeLoopId` 默认展开；历史 loop 默认收起。
- LoopHeader 显示 loop 编号、时间、plan version、最终状态。

### 5.2 评论类型渲染

| commentType | 渲染样式 |
|---|---|
| `HUMAN_FEEDBACK` | 用户头像 + 气泡高亮 |
| `EXECUTION_PLAN` | PM agent 卡片，含目标摘要与任务数 |
| `DEV_RESULT` | 开发结果卡片，含 agent、任务 key、变更文件、成败状态 |
| `VALIDATION_RESULT` | 测试报告卡片，含 `area` 标签、通过/失败摘要 |
| `ACCEPTANCE` | PM 验收结论，醒目状态色 |
| `REMEDIATION` | PM 重跑指令，黄色警示 |
| `INTERRUPTION` | 系统中断消息，居中灰条 |
| `FAILURE` | 系统失败，红色告警 |
| `MR_CREATED / MR_UPDATED / MR_FAILED` | 交付事件卡片，含 MR 链接 |
| `MEMORY_UPDATED / MEMORY_UPDATE_FAILED` | 内存更新事件 |

所有 agent 评论使用机器人图标 + agent 名称；人类评论使用用户头像/名称。

### 5.3 评论输入

- 使用 `MarkdownEditor`（与 PRD 编辑一致）。
- 当 `automationStatus ∈ {PLANNING, DEVELOPING, ACCEPTING}` 时：
  1. 输入框上方显示 `Alert` 警告条。
  2. 发送按钮变为 `danger`。
  3. 点击发送弹出 `Modal.confirm` 二次确认。
- 提交成功后清空编辑器并滚动评论流到底部。

### 5.4 快捷定位

- 点击 `EXECUTION_PLAN` 评论切换到右侧“执行计划”Tab。
- 点击 `DEV_RESULT` 评论在右侧“任务”Tab 高亮对应任务。
- 评论中的文件路径、MR URL 自动渲染为链接。

---

## 6. 右侧面板

### 6.1 执行计划 Tab

- 顶部卡片：`goal`、`planType`、`status` Tag、`risks`、`validation` 命令（折叠）。
- 下方任务表格：列 `taskKey`、标题、`agent`、`area`、状态。
- 行展开：`dependsOn`、`fileScope`、`acceptanceCriteria`。

### 6.2 任务 Tab

- 顶部工具栏：任务统计 + **停止自动化**按钮。
- 任务列表操作：`运行`、`重跑`、`查看运行`、`查看代码片段`。
- 失败任务显示 `failureSummary`；阻塞任务显示依赖失败原因。
- 行展开显示该任务最新 `VALIDATION_RESULT`。

### 6.3 运行 Tab

- 按 `runType` 过滤。
- 表格列：`runNo`、`runType`、状态、触发来源、持续时间、开始时间。
- 点击行打开 `RunLogDrawer`。

### 6.4 交付 Tab

- 顶部卡片：MR 状态、分支、commit hash、MR URL、`重试 MR` 按钮。
- 下方折叠事件列表：`MR_CREATED / MR_UPDATED / MR_FAILED / MEMORY_UPDATED / MEMORY_UPDATE_FAILED`。

### 6.5 运行日志 Drawer

- 右侧滑出，宽度 640–800px。
- 顶部 Run 元信息；主体等宽字体日志区；WebSocket 推送时自动追加。

---

## 7. 操作、中断与变更请求

### 7.1 中断流程

1. 用户输入人类评论。
2. 活跃状态下显示警告条、危险按钮、二次确认。
3. 提交后本地乐观追加，并调用 `refresh()`。
4. 后端标记计划 `INTERRUPTED`、旋转 `activeLoopId`、写系统评论、启动 PM 重规划。
5. 前端轮询到变化后：新 loop 展开、旧 loop 收起、自动化状态变为 `PLANNING`。

### 7.2 变更请求流程

- 需求 `COMPLETED` 后，人类评论提示变为：“当前需求已交付，提交评论将基于现有 MR/分支创建变更请求 loop。”
- 后端生成 `CHANGE_REQUEST` loop，前端按正常流程展示新的执行计划与任务。

### 7.3 其他操作

- **重跑失败任务**：任务行“重跑”按钮，必填提示词，成功后刷新。
- **重试 MR**：交付 Tab 按钮，调用 `POST /requirement/{id}/mr/retry`。
- **停止自动化**：任务 Tab 工具栏按钮，调用 stop API 后立即刷新。

---

## 8. 可访问性与响应式

### 8.1 可访问性

- 图标按钮均带 `aria-label`。
- 评论类型用图标 + 文字，不只依赖颜色。
- `Modal.confirm` 支持键盘 `Enter` / `Esc`。
- Markdown 渲染做 XSS 净化。
- 状态变化除颜色外，使用 `message` 或 `Alert` 通知。

### 8.2 响应式

- **≥1440px**：双列 75/25。
- **1280–1439px**：右侧最小 280px，空间不足时变为可折叠抽屉。
- **<1280px**：单栏展示 PRD + 评论；右下角悬浮按钮打开右侧抽屉。
- **移动端**：保证评论输入、状态标签可见；任务/运行表格支持横向滚动。

### 8.3 性能

- 评论数 >100 时使用虚拟列表。
- 日志 Drawer 只渲染当前 Run。
- 页面不可见时轮询降级或暂停。
- 适当使用 `React.memo`，避免过度优化。

---

## 9. 未决事项（待实现时细化）

1. 后端 API 路径与 DTO 字段需与 Task 1/2 对齐后再确定具体 service 参数。
2. WebSocket 通道名/消息格式需与后端 `RunLogWebSocketHub` 改动对齐。
3. 各评论类型的 `metadataJson` 结构需在 DTO 稳定后细化渲染逻辑。

---

## 10. 验证方式

- `pnpm -C frontend build` 通过。
- 页面能正常加载 PRD、评论、执行计划、任务、运行、交付。
- 人工评论在自动化活跃时触发警告 + 二次确认。
- 轮询在自动化状态变化时正确刷新界面。

---

## 11. 待清理项

新流程直接替代原有的 overview / detailed design 链。在新页面稳定运行后，需要移除或下线以下内容，不允许新旧两套入口并存：

| 待清理项 | 说明 | 建议时机 |
|---|---|---|
| `components/RequirementWorkspace/OverviewDesignPanel.jsx` | 原“概览设计”面板 | 新 PRD 流程验证通过后 |
| `components/RequirementWorkspace/DetailedDesignPanel.jsx` | 原“详细设计”面板 | 新 PRD 流程验证通过后 |
| `WorkspaceTab` 中的 `'overview'` / `'detailed'` | Tab 键值，需从 `types.js` 与 `RequirementWorkspace` 中移除 | 与面板同时移除 |
| `RequirementWorkspace` 中对 `findOneOverviewDesign` / `findDetailedDesignsByOverview` 的调用 | 新流程不再依赖 overview/detailed design | 重写容器时一并移除 |
| `frontend/src/pages/OnlineCode/FeatureDesignTab.tsx` / `DetailedDesignTab.tsx` / `PlanTab.tsx` | 如果其他页面已不再使用，可整体删除；若仍有依赖，需评估是否合并 | 确认无引用后 |
| 与 overview/detailed design 相关的 service 文件与路由 | `services/overviewDesign.js`、`services/detailedDesign.js` 等 | 后端确认废弃并下线对应 API 后 |

> 第一版实施时只隐藏/移除 `RequirementWorkspace` 内部的 overview/detailed 相关代码，不主动删除后端 API 与其他页面的引用，避免扩大影响面。
