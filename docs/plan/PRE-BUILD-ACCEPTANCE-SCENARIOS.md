# 编码前交互设计 — 产品验收场景

> **用途**：产品验收标准。代码实现（Track B/F）须通过本文档全部场景，末轮据此做产品验收。
>
> **方法论**：pm-execution `test-scenarios` 技能（Test Objective / Starting Conditions / User Role / Test Steps / Expected Outcomes）。
>
> **依据**：`docs/contracts/API-CONTRACT-PRE-BUILD.md`（§3 枚举 / §4 状态机 / §5 端点 P1–P14+P12a）+ `PRE-BUILD-IMPLEMENTATION-PLAN.md` D1–D15。
>
> **角色**：PM（产品经理，验收人）、Dev（开发者，操作端点）、Agent（内置智能体，异步产 JSON）。

---

## 用户故事

**US-1**：作为 PM，我新建项目后系统自动生成规划书，我编辑确认后自动展开各功能设计，逐条确认后即可一键执行编码——全程无需手写设计文档。

**US-2**：作为 Dev，我对同一功能设计执行编码时，系统互斥拒绝并发构建，避免产物冲突。

**US-3**：作为 PM，规划书改动后所有已生成功能设计自动失效，强制重新生成，保证设计与规划一致。

---

## 场景组 A — 正向主流程

### A1 新建项目触发规划（P1, D2）

**Test Objective**：验证新建项目后系统自动起规划智能体，Plan 进入 GENERATING。

**Starting Conditions**：
- 后端服务可用，`planning-agent` + `project-planning` skill 已 seed（V6）。
- `ProjectService.create` 持久化项目逻辑可用。

**User Role**：Dev

**Test Steps**：
1. `POST /project` body `{description:"在线编程平台预构建设计"}` → 返 `{projectId}`
2. DB 查 `oc_plan` where project_id=projectId → 存在 1 行，version=1，is_latest=TRUE，status=GENERATING
3. 等待智能体回调落库 → status=DRAFT，content 非空且符合 PlanContent 骨架
4. `GET /plan/{projectId}` → 返最新 PlanDto，status=DRAFT

**Expected Outcomes**：
- P1 复用现有 `POST /project`，不新增端点（D2）
- Plan 初始 version=1、is_latest=TRUE、status=GENERATING
- 智能体完成后 status=DRAFT + content 落库
- 失败时 Plan=FAILED（D7，见 E3）

### A2 规划书确认批量起功能设计（P5, D15 re-confirm 幂等）

**Test Objective**：验证确认规划书后，对每个 feature 起功能设计智能体；已有 latest FD 的 feature 跳过。

**Starting Conditions**：
- A1 完成，Plan.status=DRAFT，content.features 含 3 项（f1/f2/f3）

**User Role**：Dev

**Test Steps**：
1. `POST /plan/{projectId}/confirm` → 返 PlanDto，status=CONFIRMED
2. DB 查 `oc_feature_design` where project_id 且 is_latest=TRUE → 3 行（f1/f2/f3），均 status=PENDING→GENERATING
3. 等待回调 → 3 行均 status=DRAFT，content 符合 FeatureDesignContent 骨架
4. 再次 `POST /plan/{projectId}/confirm` → 不为已有 latest FD 的 feature 重复 spawn（D15 幂等）

**Expected Outcomes**：
- Plan=CONFIRMED 后批量 spawn FD
- 每个 feature 一条 latest FD，version=1
- re-confirm 幂等：已有 latest FD 的 feature 跳过；STALE 的不自动重生（需用户单独 regenerate）

### A3 全部确认后执行编码（P12, P12a, D13）

**Test Objective**：验证全部 FD 确认后项目进入 READY_TO_BUILD，单条/批量执行编码抢占 BUILDING。

**Starting Conditions**：
- A2 完成，3 条 FD 均 status=CONFIRMED、build_status=IDLE

**User Role**：Dev

**Test Steps**：
1. `resolvePreBuildState` → READY_TO_BUILD
2. `POST /project/{projectId}/build` → 返数组，3 条各含 runId
3. DB 查 3 条 FD → build_status=BUILDING
4. `POST /featureDesign/{f1id}/build`（同一条再触发）→ HTTP 409（D1）
5. 等待 Run 回调 → build_status=BUILT

**Expected Outcomes**：
- 项目级批量 build（P12，PathVariable，D13）对每条 CONFIRMED 且非 BUILDING 的 FD 抢占
- 单条 build（P12a）已在 BUILDING 时返 409
- Run 完成回调更新 build_status=BUILT（D11 链式）

---

## 场景组 B — 状态机推进

### B1 规划书编辑退回 DRAFT 并级联 STALE（P3）

**Test Objective**：验证编辑已确认规划书后退回 DRAFT，关联 FD 全部 STALE。

**Starting Conditions**：
- Plan.status=CONFIRMED，存在 2 条 latest FD（status=CONFIRMED, build_status=BUILT/IDLE）

**User Role**：Dev

**Test Steps**：
1. `PUT /plan/{projectId}` body `EditPlanRequest{content:{...}}` → 返新 PlanDto，version+1，status=DRAFT，is_latest=TRUE
2. 旧 Plan 行 is_latest=FALSE
3. DB 查 FD → 2 条均 status=STALE；原 BUILT 的 build_status→STALE，原 IDLE 的 build_status 保持 IDLE

**Expected Outcomes**：
- 编辑新建 version+1 行，旧行 is_latest=FALSE（单表多行版本历史）
- 关联 latest FD 级联 STALE（cascadeStale）
- build_status∈{BUILT,BUILD_FAILED}→STALE，其余保持（D15）

### B2 规划书重生（P4）

**Test Objective**：验证重生规划书带 modifyHint，version+1，重新起智能体。

**Starting Conditions**：Plan 存在 latest 行（任意非 GENERATING 状态）

**User Role**：Dev

**Test Steps**：
1. `POST /plan/{projectId}/regenerate` body `{modifyHint:"增加权限模块"}` → 返 PlanDto，version+1，status=GENERATING
2. 旧行 is_latest=FALSE
3. 关联 FD 级联 STALE
4. 智能体回调 → status=DRAFT，content 反映 modifyHint

**Expected Outcomes**：
- 重生 version+1、status=GENERATING、起智能体
- GENERATING 态再次 regenerate 被并发守卫拒绝（C8，见 B5）
- modifyHint 落库

### B3 功能设计编辑/重生与 build_status 联动（P8, P9）

**Test Objective**：验证 FD 编辑/重生时 build_status 按 BUILT/BUILD_FAILED→STALE 规则流转。

**Starting Conditions**：FD latest 行，status=CONFIRMED

**User Role**：Dev

**Test Steps**：
1. build_status=BUILT 时 `PUT /featureDesign/{id}` → 新行 version+1, status=DRAFT, build_status=STALE
2. build_status=IDLE 时 `PUT /featureDesign/{id}` → 新行 build_status=IDLE（保持）
3. `POST /featureDesign/{id}/regenerate` → 新行 status=GENERATING, build_status 同上规则

**Expected Outcomes**：
- 编辑/重生新建 version+1 行
- build_status∈{BUILT,BUILD_FAILED}→STALE，否则保持 IDLE
- BUILDING 态编辑/重生返 409（见 C1）

### B4 功能设计确认与 STALE 拒绝（P10, P11）

**Test Objective**：验证 DRAFT 可确认，STALE 不可确认。

**Starting Conditions**：3 条 FD：f1=DRAFT, f2=STALE, f3=CONFIRMED

**User Role**：Dev

**Test Steps**：
1. `POST /featureDesign/confirm` body `{ids:[f1,f2]}` → f1 确认成功；f2 返业务错误（200+fail）
2. `POST /featureDesign/{f1}/confirm` → 单条确认便捷版
3. f3 已 CONFIRMED，再次确认 → 幂等或业务提示

**Expected Outcomes**：
- 仅 DRAFT 可确认；STALE 返 200+ResultData.fail（非 409）
- 批量确认逐条校验，返回更新后列表
- 全部 CONFIRMED 后 ProjectState=READY_TO_BUILD

### B5 智能体并发守卫（C8）

**Test Objective**：验证 GENERATING 态拒绝二次发起（不靠隐含）。

**Starting Conditions**：Plan.status=GENERATING

**User Role**：Dev

**Test Steps**：
1. `POST /plan/{projectId}/regenerate` → 业务错误拒绝（200+fail）
2. 某 FD status=GENERATING 时，批量 spawn 该 feature → 跳过（不抛）

**Expected Outcomes**：
- 显式并发守卫：GENERATING 拒绝二次发起
- 批量场景对 GENERATING 的 feature 跳过容错

---

## 场景组 C — 互斥与 409

### C1 BUILDING 态编辑/重生拒绝（P8, P9, D1）

**Test Objective**：验证 BUILDING 态 FD 不可编辑/重生，返 HTTP 409。

**Starting Conditions**：FD.status=CONFIRMED, build_status=BUILDING

**User Role**：Dev

**Test Steps**：
1. `PUT /featureDesign/{id}` → HTTP 409
2. `POST /featureDesign/{id}/regenerate` → HTTP 409
3. 响应体 `ResultData.fail(msg)`，状态码 409

**Expected Outcomes**：
- 仅 BUILDING 态编辑/重生 + 编码互斥返 409（D1）
- 经 `ConflictException`→`PreBuildExceptionHandler` 映射
- 其余业务错误仍 200+fail（无 422）

### C2 编码执行互斥（P12a, D1, D11）

**Test Objective**：验证条件 UPDATE 抢占 build 互斥。

**Starting Conditions**：FD.status=CONFIRMED, build_status=IDLE

**User Role**：Dev

**Test Steps**：
1. `POST /featureDesign/{id}/build` → 200, `{runId}`
2. 立即再 `POST /featureDesign/{id}/build` → 409（tryAcquireBuild 返回 0）
3. 非 CONFIRMED 态 build → 200+fail（"设计未确认"）

**Expected Outcomes**：
- `tryAcquireBuild` 条件 UPDATE（build_status<>'BUILDING'）返回受影响行数 0/1
- 抢占成功→BUILDING；失败→409
- 非 CONFIRMED→200+fail（无 422）

### C3 项目级批量 build 跳过 BUILDING（P12）

**Test Objective**：验证批量 build 跳过已在 BUILDING 的条目，整批仍 200。

**Starting Conditions**：3 条 CONFIRMED FD：f1=IDLE, f2=BUILDING, f3=IDLE

**User Role**：Dev

**Test Steps**：
1. `POST /project/{projectId}/build` → 200，返数组
2. f1/f3 各含 runId；f2 skipped=true, reason="已在构建中"
3. 整批 HTTP 200（409 按条返回，非整批）

**Expected Outcomes**：
- 批量 build 过滤 CONFIRMED && build_status≠BUILDING
- BUILDING 条目 skipped，不抛
- 整批 200，按条体现 409 语义

---

## 场景组 D — 级联与 FAILED 聚合

### D1 规划书改动级联 STALE（D15, cascadeStale）

**Test Objective**：验证规划书编辑/重生后所有 latest FD 级联 STALE，build_status 联动。

**Starting Conditions**：Plan=CONFIRMED，3 条 latest FD：build_status 分别 BUILT/BUILD_FAILED/IDLE

**User Role**：Dev

**Test Steps**：
1. `PUT /plan/{projectId}` → Plan=DRAFT
2. DB 查 3 条 FD → status 均STALE；build_status：BUILT→STALE, BUILD_FAILED→STALE, IDLE→IDLE

**Expected Outcomes**：
- cascadeStale：所有 latest FD status→STALE
- build_status∈{BUILT,BUILD_FAILED}→STALE，其余保持
- 单条 native UPDATE 完成（FeatureDesignDaoImpl）

### D2 FAILED 聚合（D7）

**Test Objective**：验证 Plan 或任一 FD FAILED → ProjectState=FAILED。

**Starting Conditions**：—

**User Role**：Dev

**Test Steps**：
1. Plan.status=FAILED → `resolvePreBuildState` 返 FAILED
2. Plan=CONFIRMED，某 FD.status=FAILED → 返 FAILED
3. FD 全 CONFIRMED → READY_TO_BUILD

**Expected Outcomes**：
- D7 FAILED 分支：plan=FAILED 或任一 fd=FAILED → FAILED
- FAILED 态阻断后续（不可确认/不可 build）

### D3 空 FD 视为 DESIGNING（D15）

**Test Objective**：验证 Plan=CONFIRMED 且 FD 空集 → DESIGNING。

**Starting Conditions**：Plan=CONFIRMED，无 latest FD

**User Role**：Dev

**Test Steps**：
1. `resolvePreBuildState` → DESIGNING
2. 此时不应出现 READY_TO_BUILD

**Expected Outcomes**：
- 空集→DESIGNING（D15，视为未就绪）
- 仅全部 FD=CONFIRMED 才 READY_TO_BUILD

---

## 场景组 E — 历史版本、WS、边界

### E1 历史版本查询（P13, P14）

**Test Objective**：验证规划书/功能设计历史版本可查。

**Starting Conditions**：某 projectId 下 Plan 有 3 个 version（v1/v2/v3，v3 latest）

**User Role**：PM

**Test Steps**：
1. `GET /plan/{projectId}/history` → 返 3 条 PlanDto，按 version 倒序
2. `GET /featureDesign/{id}/history` → 返该 feature 各版本

**Expected Outcomes**：
- 历史含全部版本（含 is_latest=FALSE 的旧行）
- 按 version 倒序

### E2 WS 日志流按 runId 过滤（D4）

**Test Objective**：验证 WS 端点 `/ws/run/{iterationId}`，前端按 frame.runId 过滤各 feature 流。

**Starting Conditions**：项目有 iterationId，多个 feature 并发 build

**User Role**：Dev

**Test Steps**：
1. 前端连接 `/ws/run/{iterationId}`
2. 收到 RunLogFrame，按 frame.runId 过滤 f1 的日志
3. f2 的 frame 不混入 f1 视图

**Expected Outcomes**：
- WS 路径 `/ws/run/{iterationId}`（非 /ws/run/{runId}，D4）
- 复用现有 RunLogWebSocketHub（按 iterationId 索引）
- 前端按 runId 过滤各 feature 流

### E3 智能体失败落 FAILED（D7, D11）

**Test Objective**：验证智能体回调解析失败 → 对应行 FAILED，不影响其他。

**Starting Conditions**：3 条 FD 并发 spawn，mock 其中 1 条返回非法 JSON

**User Role**：Dev

**Test Steps**：
1. spawnFeatureDesigns 并发 3 条
2. f2 返回非法 JSON → f2.status=FAILED
3. f1/f3 正常 → DRAFT

**Expected Outcomes**：
- 单条失败→该条 FAILED，不影响其他（设计 §8）
- 回调解析失败→FAILED（D11 exceptionally）
- 任一 FAILED→ProjectState=FAILED（D7）

### E4 内置 seed 不可删（D3）

**Test Objective**：验证三个内置 agent + 两个 skill seed 存在且 builtin 不可删。

**Starting Conditions**：V6 迁移执行后

**User Role**：Dev

**Test Steps**：
1. 查 oc_agent where builtin=TRUE → 含 planning-agent / feature-design-agent / dev-agent
2. dev-agent.skill_ids=NULL
3. 查 oc_skill → 含 project-planning / feature-design（LOCAL）
4. 尝试删除 builtin agent → 拒绝

**Expected Outcomes**：
- 三个 builtin agent 存在（D3，含 dev-agent 修复 DispatchService 悬空引用）
- dev-agent.skill_ids=NULL
- 两个 LOCAL skill seed 存在
- builtin 不可删

---

## 验收覆盖矩阵

| 场景 | 覆盖端点 | 覆盖决策 |
|---|---|---|
| A1 | P1 | D2, D7 |
| A2 | P5, P2 | D15 |
| A3 | P12, P12a | D13, D11 |
| B1 | P3 | D15 |
| B2 | P4 | C8 |
| B3 | P8, P9 | D15 |
| B4 | P10, P11 | — |
| B5 | P4, P5 | C8 |
| C1 | P8, P9 | D1 |
| C2 | P12a | D1, D11 |
| C3 | P12 | D13 |
| D1 | P3, P4 | D15 |
| D2 | (聚合) | D7 |
| D3 | (聚合) | D15 |
| E1 | P13, P14 | — |
| E2 | WS | D4 |
| E3 | (异步) | D7, D11 |
| E4 | (seed) | D3, D9 |

---

## 验收通过标准

- 全部场景 Expected Outcomes 可被实现复现（后端单测覆盖状态机/互斥/级联/聚合；前端 MSW 覆盖端点流）。
- 409 仅出现在 C1/C2/C3 的互斥场景；其余业务错误 200+fail；无 422。
- D7 FAILED 聚合、D15 空集→DESIGNING、D4 WS 路径、D3 dev-agent seed 均有场景验证。
- 末轮产品验收：PM 逐场景核对实现行为，未通过项记入返工清单。
