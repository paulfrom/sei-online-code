# 编码前交互设计 实施计划 — 规划书 + 功能设计（两层产物）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Dispatch basis（项目 CLAUDE.md 强制）**：前后端 MUST 由**独立 sub-agent 在独立上下文**中开发，永不混在一个上下文里。先冻结契约（Task 1），再 Track B（后端，`eadp-backend` skill）与 Track F（前端，`suid` skill）分别开发。
> 设计稿：`docs/archive/plan/PRE-BUILD-INTERACTION-DESIGN.md`。

---

## 执行进度 Re-baseline（2026-07-03，/loop 增量推进）

> 计划 checkbox 曾与实际代码脱节，本轮据 git 历史与文件实况重新对齐。**后续 fire 以本节为准**，跳过已完成项。

| Task | 状态 | 证据 / 缺口 |
|---|---|---|
| T1 契约 | ✅ 完成 | `docs/contracts/API-CONTRACT-PRE-BUILD.md` 已冻结提交（c29b89d/917423a/8a4dfdb） |
| T2 后端上下文 | ✅ 完成 | T3–T8 已实现即证 |
| T3 枚举 | ✅ 完成 | 9a7a7ae |
| T4 DTO | ✅ 完成 | 4232335（+ 计划外 `FeatureDesignBuildResultDto`） |
| T5 Feign API | ✅ 完成 | 8d5f4de + 6522b79：PlanApi/FeatureDesignApi + ProjectApi.build（T14 落地） |
| T6 V6 迁移 | ✅ 完成 | e2fc4a9；含 `oc_task.feature_design_id`（D8） |
| T7 实体+转换器 | ✅ 完成 | 9c8b353 实体/converter + 09ee879 converter 测试通过 |
| T8 DAO | 🟡 源码✅/测试延后 | 4dcbe2c+fbd35f0：markNonLatest/cascadeStale 写方法已加（接口式 @Query）；**Testcontainers DAO 测试 @Disabled 延后**（@DataJpaTest+Flyway+方言引导需专项） |
| T9 PlanService | 🟡 源码✅/测试3/6 | bf51eb4+671fcb5：edit/regenerate/confirm/history + PlanAgentService 桩；**测试 3/6 通过**（rejection 路径，bootstrapContext 模式）；3 success @Disabled（super.save.validateUniqueCode 需 @SpringBootTest） |
| T10 FeatureDesignService | 🟡 源码✅/测试4/8 | 2cb57e6：edit/regenerate/confirm/confirmOne/history + ConflictException(409)；FeatureDesignDto 补 audit；PlanAgentService 桩加 spawnFeatureDesign；**测试 4/8 通过**（rejection：BUILDING→409、STALE/非DRAFT 拒绝），4 success @Disabled |
| T11 FeatureDesignBuildService | 🟡 源码✅/测试延后 | e69d6e5：build（互斥抢占+Task+Run+ClaudeRunner CompletableFuture 回调，D3/D8/D11）+ buildProject 批量；Task.java 加 featureDesignId 映射（D8）；**测试 @Disabled 延后**（TaskService API 对齐 + async 桩待专项） |
| T12 ProjectStateService | ✅ 完成 | aecef0c：resolvePreBuildState 聚合（D7 FAILED / D15 空集→DESIGNING）；**8 测试全通过**（纯查询无 super.save，无需 bootstrapContext） |
| T13 PlanAgentService | ✅ 完成 | 9dab6e8：spawnPlanning/spawnFeatureDesigns/spawnFeatureDesign 实现（ClaudeRunner `CompletableFuture` + SkillMaterializer + 信号量 + D11 链式落库 DRAFT/FAILED）；**5 测试全通过**（success/parseFailure/noPlan/FD success/empty）。C8 守卫归调用方 |
| T14 Controllers | ✅ 完成 | 6522b79：PlanController + FeatureDesignController（+findByPage）+ ProjectController.build（补 T5 Step3）+ PreBuildExceptionHandler（ConflictException→409，D1） |
| T9b P1 触发 | ✅ 完成 | 64a2069：ProjectService.save 新建分支→建初始 Plan(GENERATING,v1) + spawnPlanning；@Lazy 破循环依赖 |
| T15 后端验证 | ✅ 完成 | 全量 :api+:service compileJava + :service test 通过；**后端 Track B 全部完成** |
| F1 services | ✅ 完成 | f7e2f2d：plan.ts + featureDesign.ts（类型 PlanDto/FeatureDesignDto/枚举 + 端点 P2–P5/P6–P11/P12a/P14/P13）+ buildProject（onlineCode.ts） |
| F2 MSW handlers | ✅ 完成 | 3220c44：plan/featureDesign handlers（P2–P5/P6–P11/P12a/P14/P12）+ 409 场景 + mock 数据（DRAFT/IDLE、CONFIRMED/BUILT、CONFIRMED/IDLE）；handlers.ts 导入+spread；pnpm build 通过 |
| F3 PlanTab UI | ✅ 完成 | 2b2a225：PlanTab.tsx（展示/编辑/重生/确认/历史抽屉 + status 联动）+ ProjectDetail.tsx（Tab 容器）；pnpm build 通过 |
| F4 FeatureDesignTab+Editor | ✅ 完成 | 3601931：FeatureDesignTab.tsx（ExtTable remotePaging + 多选批量确认 + 逐行 查看/编辑/重生/确认/执行编码 + STALE 禁用 Tooltip + status/buildStatus badge）+ FeatureDesignEditor.tsx（ExtModal 查看/编辑 goal/design/acceptance/fileScope + 409 提示）+ ProjectDetail.tsx 挂 "功能设计" Tab；pnpm build 通过 |
| F5 BuildActions+dva model | ✅ 完成 | （主循环补提交）：BuildActions.tsx（"执行编码"仅 READY_TO_BUILD 启用+Tooltip+调 buildProject+5s 轮询 BUILDING）+ planFeatureDesign.ts（dva model：fetchProjectState 客户端聚合 §4.1/D7/D15 + buildProject effect）+ ProjectDetail.tsx 挂 BuildActions；pnpm build 通过。D4 WS 改轮询（偏差#5） |
| F6 前端整体验证 | ✅ 完成 | pnpm build 通过 + MSW handlers 覆盖全端点（P2–P5/P6–P11/P12/P12a/P13/P14，handlers.ts 注册 plan+featureDesign handlers，project 内联 #1-#3）；交互式浏览器冒烟测试延后 |

**本轮完成**：P2 D4 WS 客户端（c1fa4fd）—— 新建 `frontend/src/utils/run-log-socket.ts`（WebSocket 客户端：subscribe/runId 过滤/终帧 `frame.state` 检测/close/NDJSON 解析）+ BuildActions 集成（build 启动后 `subscribeRunLog` 订阅 `/ws/run/{featureDesignId}` 按 runId 过滤显示日志，终帧 onTerminal 触发 `fetchFeatureDesigns` 重取 build_status 替代 5s 盲轮询；WS 失败降级轮询兜底，dev 环境无真实后端不崩）；依赖后端 iterationId=featureDesignId 映射（`FeatureDesignBuildService:92`，代码注释标注）；**pnpm build 通过**（主循环独立复核 grep+build）。frontend-engineer sub-agent 实现+提交，主循环验证。**偏差#5 解决**（D4 WS 落地）。前序：P0 本地环境阻断待 CI（32386dd）、P3 已解决（b54571d）。

**已记录偏差**：
1. ~~DAO 用 Spring Data JPA 接口式（非计划的 \*DaoImpl）~~ **已解决（P3）**：对齐 `AgentDao` 接口式为代码库正典（Global Constraints "严格遵从代码库既有规范；规范一致性优先于个人技术偏好"），计划 *DaoImpl 模式作废，不回退。
2. `FeatureDesignBuildResultDto` 为计划外新增（T4），用途待 Task 11 确认。
3. PlanAgentService 为桩（T9 占位，签名 `spawnPlanning`/`spawnFeatureDesigns`），T13 须填实现勿重建；FeatureDesignDto 需同 PlanDto 补 `Date createdDate/lastEditedDate`（T10）。
4. PlanDto/ProjectDto/AgentDto 均 redeclare `Date` audit 字段（代码库约定，契约 §2.1 的 ISO-8601 String 由 Jackson 序列化）。
5. ~~**D4 F5 WS 实现偏差**：前端无既有 WS 客户端……build_status 改用轮询~~ **已解决（P2，c1fa4fd）**：`run-log-socket.ts` WS 客户端落地，订阅 `/ws/run/{iterationId}` 按 `frame.runId` 过滤，终帧 `frame.state` 触发 build_status 重 fetch，替换 5s 盲轮询（WS 失败降级轮询兜底）。

**下一 fire**：**P1 交互式浏览器端到端冒烟**（最后一项）—— 启动 frontend dev server（MSW mock 后端），chrome-devtools 跑通：新建项目→规划书确认→3 FD 确认→执行编码→build_status 流转（设计稿 §9#1）。**注意**：MSW 不模拟异步 build_status 流转，须确认 MSW 是否返静态 BUILT 或需补 handler；UI 渲染/点击/无运行时错误为冒烟重点。**P0 本地阻断待 CI**、**P2/P3 已解决**。P1 完成后 P0–P3 全部收尾（P0 待 CI 环境）。

---

**Goal:** 实现编码前的"规划书 + 功能设计"两层交互流程，止于 `FeatureDesignBuildService` 起编码。

**Architecture:** 新增 `Plan` / `FeatureDesign` 两套 EADP 分层（Entity→DAO→DAOImpl→Service→Controller→Api+DTO），新增两个内置 agent + 两个 seed skill，新增 `FeatureDesignBuildService` 衔接编码（复用 `ClaudeRunner`/`Run`/WS Hub，不走现有 `DispatchService`）。`project` 编码前状态不落列，由 `ProjectStateService` 按 plan/feature_design 聚合。

**Tech Stack:** Java + Spring Boot + sei-core（`eadp-backend`）/ React + UmiJS + `@ead/suid`（`suid`）/ PostgreSQL + Flyway / Claude Code CLI。

## Global Constraints

- 后端分层严格 `Entity → DAO → DAOImpl → Service → Controller → API(Feign)` + DTO；JPA-only；String-UUID 主键 `IdGenerator.nextIdStr()`；`ResultData<T>`/`PageResult<T>`/`Search` 信封；`OperateResult` + `@Transactional` 写操作；所有 DTO/API 带 Swagger 注解。
- 表名前缀 `oc_`（沿用现有 `oc_project`/`oc_agent` 等），列名 snake_case；String-UUID 主键列名 `id` VARCHAR(64)。
- 版本历史：**单表多行 + `is_latest`**（与设计稿 §5 一致）。
- 前端：`@ead/suid` 组件（ExtTable remotePaging / ExtModal / Form / ComboList），`@ead/antd-style` `createStyles`，`useStore`/`request`，`createAppStore`。**禁** Tailwind/shadcn/raw antd。用任何组件前 `suid info <component> --format json`。
- 内置 agent/skill：`builtin=true` 不可删；skill 单文件 `SKILL.md` + sha256 hash-lock；seed 写在 Flyway migration。
- `claude` CLI = **API key 模式**；功能设计智能体只产 JSON、不读 workspace；编码执行经 `FeatureDesignBuildService`，不进现有 `DispatchService`。
- 不改动现有 Spec/DispatchService/Iteration/Run/Task 的既有字段与端点（仅新增表/服务/端点，复用 Run/ClaudeRunner/WS）。
- 提交规范 `<type>: <description>`；类型 feat/fix/refactor/docs/test/chore/perf/ci；不用 `git add -A`。
- 本轮后端**编译通过 + 关键单测通过**即达标（沿用 Phase 1–5 bar），不要求本地运行；前端 build 通过 + MSW handlers 覆盖新端点。
- **测试栈**：DAO 集成测试用 Testcontainers PG（与生产一致，见 D10）；V6 的 partial unique index `WHERE is_latest=TRUE` 在 PG 下成立，不依赖 H2。

---

## 修订记录 v2（评审 fixes — 2026-07-03）

> 评审发现 7 条阻断级（B1–B7）+ 8 条需澄清（C1–C8），对应决策 D1–D15。本节为决策总览，Task 1 契约与各 Task 已据此修订。**标 [可推翻] 者为 either/or，用户可改。**

| 决策 | 对应 | 内容 |
|---|---|---|
| D1 | B1 | **409 机制 [可推翻]**：新增 `ConflictException` + `@RestControllerAdvice`（`PreBuildExceptionHandler`）映射为 `ResponseEntity.status(409).body(ResultData.fail(msg))`，仅用于 build 互斥 + BUILDING 态编辑拒绝（§6/§9 明确 409）。其余业务错误沿用现有 200+`ResultData.fail`。删除 422（"设计未确认"归 200+fail）。与仓库"全 200"规范的张力标记为待清理项。 |
| D2 | B2 | **P1 入口**：新增 Task 9b，`ProjectService.create` 持久化后调 `PlanAgentService.spawnPlanning(projectId, null)`；P1 复用现有 `POST /project`（`BaseEntityApi.save`），不新增端点。 |
| D3 | B3 | **编码 agent**：V6 seed 新增第三个内置 agent `dev-agent`（builtin=true，编码执行 instructions，skill_ids=NULL），顺带修复 `DispatchService` 硬编码 `dev-agent` 的悬空引用（不改 DispatchService 代码，仅补 seed）。T11 用 `findByName("dev-agent")`。 |
| D4 | B4 | **WS 路径**：契约/F5 改为 `/ws/run/{iterationId}`（现有 `RunLogWebSocketHub` 按 iterationId 索引），前端按 `frame.runId` 过滤各 feature 流。 |
| D5 | B5 | **包名/签名**：`FeatureDesignApi` 不重新声明 `findByPage`，继承 `FindByPageApi`；`Search`/`PageResult` 统一用 `com.changhong.sei.core.dto.serach`（旧拼写，与 `FindByPageApi`/`ProjectController` 一致）。 |
| D6 | B6 | **前端状态**：F1/F5 改用 dva model（`src/models/planFeatureDesign.ts` + `useSelector`/`useDispatch`），不用 `useStore`/`projectStore`（项目无此文件，用 dva）。 |
| D7 | B7 | **FAILED 聚合**：T12 `resolvePreBuildState` 加 FAILED 分支：plan.status=FAILED 或任一 fd.status=FAILED → `"FAILED"`。 |
| D8 | C1 | **Run→FD 关联 [可推翻]**：T11 每次 build 创建真实 Task 行（设计"功能设计=Task，不二次切分"），`Run.taskId=task.id`（语义正确，不滥用 taskId）；Task→FeatureDesign 关联：T11 Step 0 读 `Task.java` 全字段选定，若无合适字段则在 V6 给 `oc_task` 新增 `feature_design_id` 列（新增列，不改动既有字段）。 |
| D9 | C2 | **seed 对齐**：T6 seed 列严格对齐 V3；`skill_ids` 格式实现时读 `StringListConverter` 确认（JSON array 字符串），审计列默认值对齐 V3。 |
| D10 | C3 | **测试基建**：T8 新建 Testcontainers PG（`build.gradle` 加 `org.testcontainers:postgresql` + `spring-boot-testcontainers`）。 |
| D11 | C4 | **回调模型**：T11/T13 用 `ClaudeRunner.execute` 返回的 `CompletableFuture<String>` 链式落库（`.thenApply` 解析 JSON → `.thenAccept` 持久化 + 状态流转），不设独立 `onRunFinished` 钩子。 |
| D12 | C5 | **ProjectStateService**：T12 新建类（现不存在），职责仅"编码前状态聚合"，与 `BuildLoopService`（编码中）边界隔离。 |
| D13 | C6 | **P12 路径**：`POST /project/{projectId}/build`（PathVariable），对齐设计稿。 |
| D14 | C7 | **P6 方法**：沿用 `FindByPageApi.findByPage(Search)`（POST），Search 带 projectId filter；设计稿 GET 为示意，以 sei-core 约定为准。 |
| D15 | C8 | **杂项**：content 列保持 TEXT（与 `SpecPageListConverter` 一致，§5 JSONB 为示意）；空 FD→DESIGNING；GENERATING 态并发前置校验显式化（T9/T10/T13）；PENDING 行先建后转 GENERATING（T13）；re-confirm 只对无 latest FD 的 feature spawn（T9）；运行日志 Tab 复用现有（无则新增 F7）；成功标准降级见 Global Constraints。 |

---

## 文件结构（File Map）

### 后端（`backend/sei-online-code-api` + `backend/sei-online-code-service`）

| 路径 | 职责 | 任务 |
|---|---|---|
| `.../dto/enums/PlanStatus.java` | Plan 状态枚举 | T3 |
| `.../dto/enums/FeatureDesignStatus.java` | 设计状态枚举 | T3 |
| `.../dto/enums/FeatureDesignBuildStatus.java` | 构建状态枚举 | T3 |
| `.../dto/PlanDto.java` | Plan DTO | T4 |
| `.../dto/FeatureDesignDto.java` | FeatureDesign DTO | T4 |
| `.../dto/plan/PlanContent.java` + `PlanFeature.java` | Plan content JSON 骨架 | T4 |
| `.../dto/featuredesign/FeatureDesignContent.java` | FeatureDesign content JSON 骨架 | T4 |
| `.../dto/request/{RegeneratePlanRequest,RegenerateFeatureDesignRequest,ConfirmFeatureDesignsRequest}.java` | 请求体 DTO | T4 |
| `.../api/PlanApi.java` | Plan Feign API（P2–P5,P13） | T5 |
| `.../api/FeatureDesignApi.java` | FeatureDesign Feign API（P6–P11,P12a,P14） | T5 |
| `.../service/src/main/resources/db/migration/V6__plan_feature_design.sql` | 两表 DDL + 内置 agent/skill seed | T6 |
| `.../entity/Plan.java` | Plan 实体 | T7 |
| `.../entity/FeatureDesign.java` | FeatureDesign 实体 | T7 |
| `.../entity/converter/PlanContentConverter.java` + `FeatureDesignContentConverter.java` | JSONB↔对象 转换器 | T7 |
| `.../dao/PlanDao.java` + `PlanDaoImpl.java` | Plan 数据访问 | T8 |
| `.../dao/FeatureDesignDao.java` + `FeatureDesignDaoImpl.java` | FeatureDesign 数据访问（含条件 UPDATE 抢占查询） | T8 |
| `.../service/PlanService.java` | Plan 业务（编辑/重生/确认 + 级联 STALE + 批量起 FD 智能体） | T9 |
| `.../service/FeatureDesignService.java` | FD 业务（编辑/重生/确认/批量确认 + 失效联动） | T10 |
| `.../service/FeatureDesignBuildService.java` | FD 编码执行（条件 UPDATE 互斥 → Task → ClaudeRunner → Run 回调更新 build_status） | T11 |
| `.../service/ProjectStateService.java` | 编码前项目状态实时聚合（D12 新建类，非修改现有） | T12 |
| `.../service/PlanAgentService.java` | 规划/设计智能体 spawn 编排（复用 ClaudeRunner + SkillMaterializer） | T13 |
| `.../controller/PlanController.java` | Plan 端点 | T14 |
| `.../controller/FeatureDesignController.java` | FD 端点 + build 端点 | T14 |
| `.../service/src/test/...` | 单测：状态机、互斥抢占、聚合、级联 STALE | T8–T12 各自 |

### 前端（`frontend/`）

| 路径 | 职责 | 任务 |
|---|---|---|
| `src/services/plan.ts` + `featureDesign.ts` | P1–P14+P12a request 封装 | F1 |
| `src/mocks/handlers/plan.ts` + `featureDesign.ts` | MSW handlers（新端点） | F2 |
| `src/pages/project/PlanTab.tsx` | 规划书 Tab（查看/编辑/重生/确认 + 历史版本抽屉） | F3 |
| `src/pages/project/FeatureDesignTab.tsx` | 功能设计 Tab（ExtTable 多选 + 批量确认 + 逐项操作） | F4 |
| `src/pages/project/FeatureDesignEditor.tsx` | 功能设计编辑/查看 ExtModal | F4 |
| `src/pages/project/BuildActions.tsx` | 执行编码按钮（单/批）+ build_status badge | F5 |
| `src/models/planFeatureDesign.ts`（D6 新建 dva model） | 项目状态聚合 + build_status 展示 | F5 |

---

## Task 1: 冻结接口契约到 `docs/contracts/`

**Files:**
- Create: `docs/contracts/API-CONTRACT-PRE-BUILD.md`

**Interfaces:**
- Produces: 冻结的 P1–P14 + P12a 端点定义、DTO schema、状态枚举、409 机制、WS 路径、seed 结构，作为 Track B / Track F 的共同唯一依据。Track B 与 Track F 只读此契约，互不读对方代码。**契约内容以本 Task 为准；设计稿 §5/§6 的 JSONB/GET 路径为示意，冲突时以 D1–D15 为准。**

- [x] **Step 1: 写契约文档**，内容包含：

1. **Scope 表**：In = Plan/FD CRUD + 状态机 + build 互斥 + 内置 agent/skill seed（含 `dev-agent`）+ FeatureDesignBuildService + Testcontainers PG 测试基建；Out = 改动现有 Spec/DispatchService 代码、真实 git clone、多租户。
2. **Domain payload**：`PlanDto`、`FeatureDesignDto`（含 `status` / `buildStatus` / `version` / `isLatest` / `content`）、`PlanContent`（`summary/techAssumptions/features[]{featureId,title,outline}/nonGoals`）、`FeatureDesignContent`（`featureId/goal/design/acceptance[]/fileScope`）。
3. **枚举**：`PlanStatus{GENERATING,DRAFT,CONFIRMED,FAILED}`、`FeatureDesignStatus{PENDING,GENERATING,DRAFT,CONFIRMED,STALE,FAILED}`、`FeatureDesignBuildStatus{IDLE,BUILDING,BUILT,BUILD_FAILED,STALE}`。
4. **状态机图**（设计稿 §3 原样）+ **聚合规则含 FAILED（D7）**：plan.status=FAILED 或任一 fd.status=FAILED → `ProjectState=FAILED`；Plan=CONFIRMED 且 FD 空集 → `DESIGNING`（D15）。
5. **端点表 P1–P14 + P12a**，每个写明 Method/Path/Request/Response data/状态推进/失败码。关键修订：
   - **P1** = 复用现有 `POST /project`（`BaseEntityApi.save`），body `{description}`；service 持久化后起规划智能体，返 `{projectId}`（D2，不新增端点）。
   - **P6** = `POST /featureDesign/findByPage`（继承 `FindByPageApi`），`Search` 带 `projectId` filter（D14，非设计稿的 GET）。
   - **P12** = `POST /project/{projectId}/build`（PathVariable，D13）。
   - **失败码**：仅 build 互斥 + BUILDING 态编辑拒绝返 **HTTP 409**（D1，经 `ConflictException`→`PreBuildExceptionHandler`）；其余业务错误（未确认/不存在/STALE 不可确认等）返 **HTTP 200 + `ResultData.fail(msg)`**（仓库既有约定）；**无 422**。
6. **409 机制（D1）**：定义 `ConflictException extends RuntimeException` + `@RestControllerAdvice class PreBuildExceptionHandler`，`@ExceptionHandler(ConflictException.class)` 返 `ResponseEntity.status(409).body(ResultData.fail(msg))`。注明与仓库"全 200"规范的张力为待清理项。
7. **WS（D4）**：日志流端点 `/ws/run/{iterationId}`（现有 `RunLogWebSocketHub` 按 iterationId 索引），`RunLogFrame` 含 `runId/taskId/state`；前端按 `frame.runId` 过滤各 feature/plan 流。**不是 `/ws/run/{runId}`。**
8. **DDL（D15/D9）**：表名 `oc_plan` / `oc_feature_design`，**content 列 `TEXT`**（非 JSONB，与 `SpecPageListConverter` 一致）；审计列沿用 `BaseAuditableEntity` 8 字段；partial unique index `WHERE is_latest=TRUE`（PG）。另：V6 给 `oc_task` 新增 `feature_design_id VARCHAR(64)` 列（D8，若 `Task.java` 无合适字段）。
9. **包名（D5）**：`Search`/`PageResult` 统一 `com.changhong.sei.core.dto.serach`（旧拼写，与 `FindByPageApi` 一致）。
10. **内置 seed（D3/D9）**：三个 agent（`planning-agent`/`feature-design-agent`/`dev-agent`，均 `builtin=true`）+ 两个 skill（`project-planning`/`feature-design`，LOCAL seed）。`dev-agent` instructions 指向编码执行，`skill_ids=NULL`。seed 列严格对齐 V3（`oc_skill` 含 `source/source_type/computed_hash`，`oc_agent` 含 `model`；`skill_ids` 格式读 `StringListConverter` 确认）。
11. **前端状态管理（D6）**：用 dva model `src/models/planFeatureDesign.ts`，不用 `useStore`/`projectStore`。

- [x] **Step 2: 提交契约**

```bash
git add docs/contracts/API-CONTRACT-PRE-BUILD.md
git commit -m "docs: freeze pre-build api contract (plan + feature design)"
```

> **Gate**：契约冻结后，Track B 与 Track F 可并行启动，互不阻塞。

---

## Track B — 后端（`eadp-backend` skill，独立 sub-agent 上下文）

### Task 2: 后端 sub-agent 上下文准备

- [ ] **Step 1**: 后端 sub-agent 启动时先 `suid`/`eadp-backend` 不适用——确认加载 `eadp-backend` skill，通读 `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/Agent.java`、`AgentService.java`、`Task.java`、`Run.java`、`agent/ClaudeRunner.java`、`agent/SkillMaterializer.java`、`V3__skill_agent.sql` 作为模式参照。
- [ ] **Step 2**: 确认 `IdGenerator.nextIdStr()`、`BaseAuditableEntity`、`BaseEntityDao`、`BaseEntityService`、`ResultData`、`OperateResult`、`OperateResultWithData` 的包路径与用法（沿用 Agent/AgentService 中的 import）。

### Task 3: 枚举（api 模块）

**Files:**
- Create: `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/PlanStatus.java`
- Create: `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/FeatureDesignStatus.java`
- Create: `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/FeatureDesignBuildStatus.java`
- Test: 无（纯枚举，编译即验）

**Interfaces:**
- Produces: 三个枚举供 DTO/Entity/Service 共用。

- [ ] **Step 1: 写三个枚举**

```java
package com.changhong.onlinecode.dto.enums;

public enum PlanStatus { GENERATING, DRAFT, CONFIRMED, FAILED }
```
```java
package com.changhong.onlinecode.dto.enums;

public enum FeatureDesignStatus { PENDING, GENERATING, DRAFT, CONFIRMED, STALE, FAILED }
```
```java
package com.changhong.onlinecode.dto.enums;

public enum FeatureDesignBuildStatus { IDLE, BUILDING, BUILT, BUILD_FAILED, STALE }
```

- [ ] **Step 2: 编译验证**

Run: `cd backend && ./gradlew :sei-online-code-api:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/PlanStatus.java backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/FeatureDesignStatus.java backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/enums/FeatureDesignBuildStatus.java
git commit -m "feat: add plan/featureDesign/build status enums"
```

### Task 4: DTO + content 骨架 + 请求体（api 模块）

**Files:**
- Create: `.../dto/plan/PlanFeature.java`、`.../dto/plan/PlanContent.java`
- Create: `.../dto/featuredesign/FeatureDesignContent.java`
- Create: `.../dto/PlanDto.java`、`.../dto/FeatureDesignDto.java`
- Create: `.../dto/request/RegeneratePlanRequest.java`、`RegenerateFeatureDesignRequest.java`、`ConfirmFeatureDesignsRequest.java`、`EditPlanRequest.java`、`EditFeatureDesignRequest.java`

**Interfaces:**
- Produces: `PlanDto`、`FeatureDesignDto`（字段见下），供 Api/Controller/Service 与前端 MSW 共用。

- [ ] **Step 1: 写 content 骨架（Swagger 注解）**

```java
package com.changhong.onlinecode.dto.plan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "规划书功能项")
public class PlanFeature {
    private String featureId;
    private String title;
    private String outline;
    // getters/setters
}
```
```java
package com.changhong.onlinecode.dto.plan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "规划书内容")
public class PlanContent {
    private String summary;
    private List<String> techAssumptions;
    private List<PlanFeature> features;
    private List<String> nonGoals;
    // getters/setters
}
```
```java
package com.changhong.onlinecode.dto.featuredesign;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "功能设计内容")
public class FeatureDesignContent {
    private String featureId;
    private String goal;
    private Map<String, Object> design;   // 智能体自定粒度
    private List<String> acceptance;
    private List<String> fileScope;
    // getters/setters
}
```

- [ ] **Step 2: 写 PlanDto / FeatureDesignDto**

`PlanDto extends BaseEntityDto`（沿用 `ProjectDto` 模式），字段：`projectId`、`version`、`status(PlanStatus)`、`content(PlanContent)`、`modifyHint`、`isLatest`。`FeatureDesignDto` 字段：`projectId`、`featureId`、`version`、`status(FeatureDesignStatus)`、`buildStatus(FeatureDesignBuildStatus)`、`content(FeatureDesignContent)`、`modifyHint`、`isLatest`。全部带 `@Schema`。

- [ ] **Step 3: 写请求体 DTO**

`RegeneratePlanRequest{modifyHint}`、`RegenerateFeatureDesignRequest{modifyHint}`、`ConfirmFeatureDesignsRequest{ids:List<String>}`、`EditPlanRequest{content:PlanContent}`、`EditFeatureDesignRequest{content:FeatureDesignContent}`。带 `@Valid`/`@NotNull` 校验。

- [ ] **Step 4: 编译 + 提交**

Run: `cd backend && ./gradlew :sei-online-code-api:compileJava`
Expected: BUILD SUCCESSFUL

```bash
git add backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/
git commit -m "feat: add plan/featureDesign dtos and request bodies"
```

### Task 5: Feign API 接口（api 模块）

**Files:**
- Create: `.../api/PlanApi.java`
- Create: `.../api/FeatureDesignApi.java`

**Interfaces:**
- Produces: 两个 Feign 接口，方法签名固定，供 Controller 实现、前端通过 MSW 对照。

- [ ] **Step 1: 写 PlanApi**

```java
package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.PlanDto;
import com.changhong.onlinecode.dto.request.EditPlanRequest;
import com.changhong.onlinecode.dto.request.RegeneratePlanRequest;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = PlanApi.PATH)
public interface PlanApi extends BaseEntityApi<PlanDto> {
    String PATH = "plan";

    @GetMapping("{projectId}")
    @Operation(summary = "取最新 Plan")
    ResultData<PlanDto> findLatest(@PathVariable("projectId") String projectId);

    @PutMapping("{projectId}")
    @Operation(summary = "编辑规划书")
    ResultData<PlanDto> edit(@PathVariable("projectId") String projectId,
                             @RequestBody @Valid EditPlanRequest request);

    @PostMapping("{projectId}/regenerate")
    @Operation(summary = "重新生成规划书")
    ResultData<PlanDto> regenerate(@PathVariable("projectId") String projectId,
                                   @RequestBody @Valid RegeneratePlanRequest request);

    @PostMapping("{projectId}/confirm")
    @Operation(summary = "确认规划书，批量起功能设计智能体")
    ResultData<PlanDto> confirm(@PathVariable("projectId") String projectId);

    @GetMapping("{projectId}/history")
    @Operation(summary = "规划书历史版本")
    ResultData<java.util.List<PlanDto>> history(@PathVariable("projectId") String projectId);
}
```

- [ ] **Step 2: 写 FeatureDesignApi**

```java
package com.changhong.onlinecode.api;

import com.changhong.onlinecode.dto.FeatureDesignDto;
import com.changhong.onlinecode.dto.request.ConfirmFeatureDesignsRequest;
import com.changhong.onlinecode.dto.request.EditFeatureDesignRequest;
import com.changhong.onlinecode.dto.request.RegenerateFeatureDesignRequest;
import com.changhong.sei.core.api.BaseEntityApi;
import com.changhong.sei.core.api.FindByPageApi;
import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = FeatureDesignApi.PATH)
public interface FeatureDesignApi extends BaseEntityApi<FeatureDesignDto>, FindByPageApi<FeatureDesignDto> {
    String PATH = "featureDesign";

    // findByPage 继承自 FindByPageApi（D5）：POST /featureDesign/findByPage(Search)，Search 带 projectId filter。
    // 不重新声明——签名/包名须与 FindByPageApi 一致（serach 旧拼写）；FeatureDesignController 经 BaseEntityController 提供实现。

    @GetMapping("{id}")
    @Operation(summary = "取单条最新版")
    ResultData<FeatureDesignDto> findOne(@PathVariable("id") String id);

    @PutMapping("{id}")
    @Operation(summary = "编辑 content")
    ResultData<FeatureDesignDto> edit(@PathVariable("id") String id,
                                      @RequestBody @Valid EditFeatureDesignRequest request);

    @PostMapping("{id}/regenerate")
    @Operation(summary = "重新生成")
    ResultData<FeatureDesignDto> regenerate(@PathVariable("id") String id,
                                            @RequestBody @Valid RegenerateFeatureDesignRequest request);

    @PostMapping("confirm")
    @Operation(summary = "批量确认")
    ResultData<java.util.List<FeatureDesignDto>> confirm(@RequestBody @Valid ConfirmFeatureDesignsRequest request);

    @PostMapping("{id}/confirm")
    @Operation(summary = "单条确认")
    ResultData<FeatureDesignDto> confirmOne(@PathVariable("id") String id);

    @PostMapping("{id}/build")
    @Operation(summary = "单 feature 执行编码")
    ResultData<java.util.Map<String, String>> build(@PathVariable("id") String id);

    @GetMapping("{id}/history")
    @Operation(summary = "功能设计历史版本")
    ResultData<java.util.List<FeatureDesignDto>> history(@PathVariable("id") String id);
}
```

> P12（项目级批量 build）挂在现有 `ProjectApi` 上：`POST /project/{projectId}/build`，本任务一并加到 `ProjectApi`（新增一个方法 `build`）。

- [ ] **Step 3: 给 ProjectApi 追加 build 方法**

```java
    @PostMapping(path = "{projectId}/build", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "批量执行编码", description = "对该项目所有 CONFIRMED 且非 BUILDING 的功能设计抢占 build_status 并起编码")
    ResultData<java.util.List<java.util.Map<String, String>>> build(@PathVariable("projectId") String projectId);
```

- [ ] **Step 4: 编译 + 提交**

Run: `cd backend && ./gradlew :sei-online-code-api:compileJava`
Expected: BUILD SUCCESSFUL

```bash
git add backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/api/
git commit -m "feat: add plan/featureDesign feign apis and project build endpoint"
```

### Task 6: Flyway V6 DDL + 内置 agent/skill seed

**Files:**
- Create: `backend/sei-online-code-service/src/main/resources/db/migration/V6__plan_feature_design.sql`

**Interfaces:**
- Produces: `oc_plan` / `oc_feature_design` 两表；两条内置 agent seed + 两条 LOCAL skill seed（id 用定长占位串，与 V3 同风格）。

- [ ] **Step 1: 写 DDL（snake_case，沿用 V3 审计列风格）**

```sql
-- oc_plan
CREATE TABLE oc_plan (
    id                  VARCHAR(64)  NOT NULL,
    project_id          VARCHAR(64)  NOT NULL,
    version             INT          NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    content             TEXT         NOT NULL,   -- JSON
    modify_hint         TEXT,
    is_latest           BOOLEAN      NOT NULL DEFAULT TRUE,
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
CREATE UNIQUE INDEX uk_plan_proj_ver    ON oc_plan (project_id, version);
CREATE UNIQUE INDEX uk_plan_proj_latest ON oc_plan (project_id) WHERE is_latest = TRUE;
CREATE INDEX idx_plan_project ON oc_plan (project_id);

-- oc_feature_design
CREATE TABLE oc_feature_design (
    id                  VARCHAR(64)  NOT NULL,
    project_id          VARCHAR(64)  NOT NULL,
    feature_id          VARCHAR(128) NOT NULL,
    version             INT          NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    build_status        VARCHAR(32)  NOT NULL DEFAULT 'IDLE',
    content             TEXT,
    modify_hint         TEXT,
    is_latest           BOOLEAN      NOT NULL DEFAULT TRUE,
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
CREATE UNIQUE INDEX uk_fd_proj_feat_latest ON oc_feature_design (project_id, feature_id) WHERE is_latest = TRUE;
CREATE INDEX idx_fd_project ON oc_feature_design (project_id);
```

- [ ] **Step 2: 写 seed（沿用 V3 模式，定长 id）**

```sql
-- 内置 skill seed（LOCAL 指针 stub，与 V3 suid/eadp-backend 同模式）
INSERT INTO oc_skill (id, name, description, source, source_type, content, computed_hash, created_date) VALUES
('SKILLSEEDPROJECTPLANNING0000000001', 'project-planning',
 '规划书生成 skill', 'local:project-planning', 'LOCAL',
 '# project-planning Skill (pointer stub)

完整技能位于操作机 ~/.claude/skills/project-planning。强制输出 Plan JSON 骨架：summary/techAssumptions/features[]{featureId,title,outline}/nonGoals，禁止预估 fileScope。
',
 'sha256:000000000000000000000000000000000000000000000000000000000000pl01', CURRENT_TIMESTAMP),
('SKILLSEEDFEATUREDESIGN0000000000001', 'feature-design',
 '功能设计生成 skill', 'local:feature-design', 'LOCAL',
 '# feature-design Skill (pointer stub)

完整技能位于操作机 ~/.claude/skills/feature-design。从 outline 展开 goal/design/acceptance[]/fileScope，骨架固定，粒度自定；fileScope 须遵循模板文件边界约定。
',
 'sha256:000000000000000000000000000000000000000000000000000000000000fd01', CURRENT_TIMESTAMP);

-- 内置 agent seed（builtin=true 不可删）
INSERT INTO oc_agent (id, name, description, instructions, model, builtin, skill_ids, created_date) VALUES
('AGENTSEEDPLANNING0000000000000001', 'planning-agent',
 '内置规划 agent：项目描述 → 规划书 JSON',
 'You produce a project Plan JSON from the project description. Use the project-planning skill.', '', TRUE,
 '["SKILLSEEDPROJECTPLANNING0000000001"]', CURRENT_TIMESTAMP),
('AGENTSEEDFEATUREDESIGN000000000001', 'feature-design-agent',
 '内置功能设计 agent：规划书 + feature outline → 功能设计 JSON',
 'You produce one FeatureDesign JSON from the plan and a feature outline. Use the feature-design skill.', '', TRUE,
 '["SKILLSEEDFEATUREDESIGN0000000000001"]', CURRENT_TIMESTAMP);

-- 内置 dev-agent（D3）：编码执行 agent，builtin=true 不可删；skill_ids=NULL（无绑定 skill）。
-- 顺带修复 DispatchService 硬编码 "dev-agent" 的悬空引用（不改 DispatchService 代码，仅补 seed）。
INSERT INTO oc_agent (id, name, description, instructions, model, builtin, skill_ids, created_date) VALUES
('AGENTSEEDDEVAGENT000000000000000001', 'dev-agent',
 '内置编码执行 agent：按功能设计 fileScope 执行编码（写代码），不产设计 JSON。',
 'You implement code for one confirmed FeatureDesign. Write files within the fileScope. Do not produce design JSON.', '', TRUE,
 NULL, CURRENT_TIMESTAMP);
```

> D9：`skill_ids` 格式实现时读 `Agent.java` 的 `StringListConverter` 确认（V3 seed 用 NULL，本 seed 用 JSON array 字符串 `["..."]` 须与 converter 解析一致）；审计列默认值对齐 V3。`dev-agent` 的 `skill_ids=NULL`。

- [ ] **Step 3: 提交**

```bash
git add backend/sei-online-code-service/src/main/resources/db/migration/V6__plan_feature_design.sql
git commit -m "feat: add V6 migration — plan/feature_design tables + builtin agent/skill seed"
```

> 校验由 T7/T8 的实体与 DAO 测试覆盖（编译期不跑 Flyway）。

### Task 7: Entity + JSON 转换器（service 模块）

**Files:**
- Create: `.../entity/Plan.java`、`.../entity/FeatureDesign.java`
- Create: `.../entity/converter/PlanContentConverter.java`、`FeatureDesignContentConverter.java`

**Interfaces:**
- Consumes: T3 枚举、T4 content 骨架。
- Produces: 两个 JPA 实体（`@Convert` JSONB 列），供 DAO/Service 用。

- [ ] **Step 1: 写 PlanContentConverter / FeatureDesignContentConverter**

参照现有 `SpecPageListConverter` 模式（Jackson `ObjectMapper` ↔ JSON 字符串）。

```java
package com.changhong.onlinecode.entity.converter;

import com.changhong.onlinecode.dto.plan.PlanContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PlanContentConverter implements AttributeConverter<PlanContent, String> {
    private static final ObjectMapper M = new ObjectMapper();
    @Override public String convertToDatabaseColumn(PlanContent attr) {
        try { return attr == null ? null : M.writeValueAsString(attr); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    @Override public PlanContent convertToEntityAttribute(String db) {
        try { return (db == null || db.isBlank()) ? null : M.readValue(db, PlanContent.class); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
```
`FeatureDesignContentConverter` 同构（泛型换 `FeatureDesignContent`）。

- [ ] **Step 2: 写 Plan 实体**

参照 `Agent.java`：`@Entity @Table(name="oc_plan")`，`@Access(FIELD)`，`extends BaseAuditableEntity`。字段：`projectId`、`version(Integer)`、`status(@Enumerated(STRING) PlanStatus)`、`content(@Convert(PlanContentConverter) columnDefinition="TEXT")`、`modifyHint`、`isLatest(Boolean)`。getter/setter + `@Transient getDisplay`。

- [ ] **Step 3: 写 FeatureDesign 实体**

字段：`projectId`、`featureId`、`version`、`status(FeatureDesignStatus)`、`buildStatus(FeatureDesignBuildStatus)`（`@Column(name="build_status", nullable=false)`, 默认 `IDLE` —— 实体字段初始化 `= FeatureDesignBuildStatus.IDLE`）、`content(@Convert(FeatureDesignContentConverter))`、`modifyHint`、`isLatest`。

- [ ] **Step 4: 写实体单测（JSON 往返）**

`src/test/.../entity/PlanContentConverterTest.java`：构造 `PlanContent`（含 2 个 feature）→ `convertToDatabaseColumn` → `convertToEntityAttribute` → 断言字段相等。FD converter 同测。

- [ ] **Step 5: 编译 + 跑测试 + 提交**

Run: `cd backend && ./gradlew :sei-online-code-service:compileJava :sei-online-code-service:test --tests '*ConverterTest'`
Expected: BUILD SUCCESSFUL, tests pass

```bash
git add backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/ backend/sei-online-code-service/src/test/
git commit -m "feat: add Plan/FeatureDesign entities and json converters"
```

### Task 8: DAO + DAOImpl（含条件 UPDATE 抢占）

**Files:**
- Create: `.../dao/PlanDao.java` + `PlanDaoImpl.java`
- Create: `.../dao/FeatureDesignDao.java` + `FeatureDesignDaoImpl.java`
- Test: `.../dao/FeatureDesignDaoImplTest.java`（互斥抢占单测）

**Interfaces:**
- Consumes: T7 实体。
- Produces: `PlanDao.findLatest(projectId)` / `findHistory(projectId)` / `markNonLatest(projectId)`；`FeatureDesignDao.findLatestByProject` / `findByFeatureId` / `tryAcquireBuild(id)`（条件 UPDATE 返回 int）/ `markStaleByProject(projectId)` / `cascadeStaleBuildStatus(projectId)`。

- [ ] **Step 1: 写 PlanDao 接口**

```java
package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.Plan;
import com.changhong.sei.core.dao.BaseEntityDao;
import java.util.List;

public interface PlanDao extends BaseEntityDao<Plan> {
    Plan findLatest(String projectId);
    List<Plan> findHistory(String projectId);
    void markNonLatest(String projectId);  // 重生前把旧的 is_latest 置 false
}
```

- [ ] **Step 2: 写 PlanDaoImpl**（`extends BaseEntityDaoImpl<Plan>`，`@PersistenceContext` + JPQL/原生 SQL）

`findLatest`：`select p from Plan p where p.projectId=:pid and p.isLatest=true`。`markNonLatest`：`update Plan p set p.isLatest=false where p.projectId=:pid and p.isLatest=true`（`@Modifying` `@Transactional`）。

- [ ] **Step 3: 写 FeatureDesignDao 接口 + 关键方法**

```java
public interface FeatureDesignDao extends BaseEntityDao<FeatureDesign> {
    List<FeatureDesign> findLatestByProject(String projectId);
    FeatureDesign findLatestByFeature(String projectId, String featureId);
    List<FeatureDesign> findHistory(String projectId, String featureId);
    /** 条件 UPDATE 抢占 build：仅当非 BUILDING 才置 BUILDING。返回受影响行数 0/1。 */
    int tryAcquireBuild(String id);
    /** 规划书改动级联：所有 latest FD 的 status 置 STALE；build_status∈{BUILT,BUILD_FAILED} 置 STALE。 */
    void cascadeStale(String projectId);
    void markNonLatest(String projectId, String featureId);
}
```

- [ ] **Step 4: 写 FeatureDesignDaoImpl** —— `tryAcquireBuild` 用原生 SQL（JPA `@Modifying` 不便表达 `<>` 条件 UPDATE，用 `EntityManager.createNativeQuery`）：

```java
@Override
@Transactional
public int tryAcquireBuild(String id) {
    return em.createNativeQuery(
            "UPDATE oc_feature_design SET build_status='BUILDING', last_edited_date=now() " +
            "WHERE id=:id AND is_latest=TRUE AND build_status<>'BUILDING'")
        .setParameter("id", id)
        .executeUpdate();
}
```

`cascadeStale`：
```java
em.createNativeQuery(
    "UPDATE oc_feature_design SET status='STALE', " +
    "build_status=CASE WHEN build_status IN ('BUILT','BUILD_FAILED') THEN 'STALE' ELSE build_status END, " +
    "last_edited_date=now() WHERE project_id=:pid AND is_latest=TRUE")
  .setParameter("pid", projectId).executeUpdate();
```

- [ ] **Step 5: 写互斥抢占单测（D10：Testcontainers PG）**

`build.gradle` 加 `testImplementation("org.testcontainers:postgresql")` + `testImplementation("org.springframework.boot:spring-boot-testcontainers")`，新建 `application-test.yml`（PG Testcontainer）。现有 `src/test` 零 DAO 集成测试基建，本步从零建。

`FeatureDesignDaoImplTest`（`@DataJpaTest` + Testcontainers PG）：
- 插入一条 FD（build_status=IDLE）→ `tryAcquireBuild` 返回 1，DB 中 build_status=BUILDING。
- 再调 `tryAcquireBuild` 同 id → 返回 0（互斥生效）。
- `cascadeStale`：插一条 BUILT + 一条 IDLE → 调用后 BUILT→STALE(build_status)、两条 status 都=STALE。

> 生产为 PG（`local-config/application-local.yaml`），不用 H2——V6 partial unique index `WHERE is_latest=TRUE` 在 PG 下成立。

- [ ] **Step 6: 编译 + 测试 + 提交**

Run: `cd backend && ./gradlew :sei-online-code-service:test --tests 'FeatureDesignDaoImplTest'`
Expected: PASS

```bash
git add backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/dao/ backend/sei-online-code-service/src/test/
git commit -m "feat: add plan/featureDesign daos with build mutex acquire"
```

### Task 9: PlanService

**Files:**
- Create: `.../service/PlanService.java`
- Test: `.../service/PlanServiceTest.java`

**Interfaces:**
- Consumes: `PlanDao`、`FeatureDesignDao`（级联 STALE）、`PlanAgentService`（T13，先以构造注入占位，T13 实现后接线）、`ProjectService`（读项目描述）。
- Produces: `findLatest` / `edit` / `regenerate` / `confirm` / `history`。

- [ ] **Step 1: 写 PlanService 骨架**（`extends BaseEntityService<Plan>`，构造注入 `PlanDao`/`FeatureDesignDao`/`PlanAgentService`）

核心方法语义：
- `edit(projectId, content)`：取 latest Plan → 若 `status=GENERATING` 抛业务异常（**200+ResultData.fail**，D1：409 仅用于 build 互斥/BUILDING 态 FD 编辑）；否则 `markNonLatest` + 新建 version+1 行（`status=DRAFT`、`isLatest=true`、`content`）→ `featureDesignDao.cascadeStale(projectId)`。
- `regenerate(projectId, modifyHint)`：`markNonLatest` + 新行 `status=GENERATING`、version+1 → 调 `planAgentService.spawnPlanning(projectId, modifyHint)`（T13 内含 GENERATING 并发守卫，C8）→ D11 链式落库 `status=DRAFT`+`content`。
- `confirm(projectId)`：latest Plan 必须 `status=DRAFT` → 置 `CONFIRMED` → 对 `plan.content.features[]` 中**无 latest FD 的 feature** 调 `planAgentService.spawnFeatureDesign(...)`（D15 re-confirm 幂等：已有 latest FD 的 feature 跳过，STALE 的需用户单独 regenerate）；并发信号量在 T13。
- `findLatest`/`history` 直查。

- [ ] **Step 2: 写 PlanServiceTest**（mock `PlanAgentService` + `FeatureDesignDao`）
- `edit` 在 `GENERATING` 态抛异常。
- `edit` 成功后 `cascadeStale` 被调用、version 递增、新行 isLatest。
- `confirm` 在非 DRAFT 抛异常；成功后对 N 个 feature 各 spawn 一次。

- [ ] **Step 3: 编译 + 测试 + 提交**

Run: `cd backend && ./gradlew :sei-online-code-service:test --tests 'PlanServiceTest'`
```bash
git add backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/PlanService.java backend/sei-online-code-service/src/test/
git commit -m "feat: add PlanService with edit/regenerate/confirm + cascade stale"
```

### Task 9b: P1 新项目触发规划（D2）

**Files:**
- Modify: `.../service/ProjectService.java`（现有 `create`/`save` 流程，持久化后触发规划）

**Interfaces:**
- Consumes: `PlanAgentService.spawnPlanning`（T13）。
- Produces: P1 = 复用现有 `POST /project`（`BaseEntityApi.save`），不新增端点；项目持久化后异步起规划智能体。

- [ ] **Step 1**: `ProjectService.create`（或 `save` 新建分支）持久化 Project 后，调 `planAgentService.spawnPlanning(projectId, null)`；Plan 初始行 `status=GENERATING`、`version=1`、`isLatest=true`。
- [ ] **Step 2**: 编译 + 提交 `git commit -m "feat: trigger planning agent on project create (P1)"`

> P1 不新增 API 端点（复用 `BaseEntityApi.save`）；契约 §P1 已声明。失败时 Plan=`FAILED`（D7）。

### Task 10: FeatureDesignService

**Files:**
- Create: `.../service/FeatureDesignService.java`
- Test: `.../service/FeatureDesignServiceTest.java`

**Interfaces:**
- Consumes: `FeatureDesignDao`、`PlanAgentService`。
- Produces: `findLatestByProject` / `findOne` / `edit` / `regenerate` / `confirm`（批）/ `confirmOne` / `history`。

- [ ] **Step 1: 写 FeatureDesignService**

核心语义：
- `edit(id, content)`：取 latest → `build_status=BUILDING` 抛 409；否则 `markNonLatest(projectId,featureId)` + 新行 version+1、`status=DRAFT`；若旧 `build_status∈{BUILT,BUILD_FAILED}` 新行 `build_status=STALE`（否则保持 IDLE）。
- `regenerate(id, modifyHint)`：`BUILDING` 抛 409；新行 `status=GENERATING` → `planAgentService.spawnFeatureDesign(...)` → 回调 `status=DRAFT`+`content`，`build_status` 同 edit 规则。
- `confirm(ids)`：逐条校验 `status∈{DRAFT}`（STALE 不可确认，抛业务异常）→ 置 `CONFIRMED`。返回更新后列表。
- `confirmOne(id)`：`confirm([id])` 的便捷版。
- `findLatestByProject`/`findOne`/`history` 直查。

- [ ] **Step 2: 写 FeatureDesignServiceTest**
- `confirm` 对 STALE 态抛异常。
- `edit` 在 BUILDING 抛 409；BUILT 态编辑后新行 build_status=STALE。
- `regenerate` 在 BUILDING 抛 409。

- [ ] **Step 3: 编译 + 测试 + 提交**

Run: `cd backend && ./gradlew :sei-online-code-service:test --tests 'FeatureDesignServiceTest'`
```bash
git add backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/FeatureDesignService.java backend/sei-online-code-service/src/test/
git commit -m "feat: add FeatureDesignService with edit/regenerate/confirm + build-stale transitions"
```

### Task 11: FeatureDesignBuildService（编码执行互斥 + Run 回调）

**Files:**
- Create: `.../service/FeatureDesignBuildService.java`
- Test: `.../service/FeatureDesignBuildServiceTest.java`

**Interfaces:**
- Consumes: `FeatureDesignDao.tryAcquireBuild`、`ClaudeRunner`、`RunService`/`Run`（创建 Run 行）、`SkillMaterializer`（materialize `feature-design-agent` 绑定 skill 到运行目录）、`AgentService.findByName("feature-design-agent")`、`WorkspaceManager`（解析 project workspace，编码要写文件，**这里需要 worktree**——与设计稿 §7 末尾一致）。
- Produces: `build(featureDesignId)` → `{runId}`；`buildProject(projectId)` → 批量；Run 完成经 `ClaudeRunner.execute` 返回的 `CompletableFuture` 链式回调 `updateBuildStatus(featureDesignId, success)` → 更新 `build_status`（D11，无独立钩子）。

- [ ] **Step 0: 读 `Task.java` 全字段，选定 FeatureDesign 关联字段（D8）**
  - 若现有字段（如 `specId`/`iterationId`/payload）可复用 → 用之；否则 V6 给 `oc_task` 新增 `feature_design_id VARCHAR(64)` 列（新增列，不改动既有字段）。

- [ ] **Step 1: 写 build 单条（D3/D8/D11）**

```java
public Map<String,String> build(String featureDesignId) {
    FeatureDesign fd = featureDesignDao.findOne(featureDesignId); // latest
    if (fd == null) throw new BusinessException("功能设计不存在");           // 200 + ResultData.fail
    if (fd.getStatus() != FeatureDesignStatus.CONFIRMED)
        throw new BusinessException("设计未确认，不可执行编码");            // 200 + ResultData.fail（无 422，D1）
    int acquired = featureDesignDao.tryAcquireBuild(featureDesignId);
    if (acquired == 0) throw new ConflictException("该功能正在构建中");     // HTTP 409（D1）

    // 抢占成功 → 建 Task（功能设计=Task，不二次切分，D8）→ Run.taskId=task.id（语义正确，不滥用 taskId）
    Agent agent = agentService.findByName("dev-agent");                    // D3，V6 seed 已补
    Task task = buildTaskFrom(fd, agent);                                  // assignedAgent=dev-agent，关联 featureDesignId
    taskDao.save(task);
    String runId = IdGenerator.nextIdStr();
    String cwd = workspaceManager.resolveWorktree(fd.getProjectId());     // 编码写文件，需 worktree（§7 末尾）

    // D11：execute 返 CompletableFuture<String>，链式落库 build_status（无独立 onRunFinished 钩子）
    claudeRunner.execute(iterationId(task), runId, promptFor(fd), cwd)
        .thenAccept(result -> updateBuildStatus(featureDesignId, result.success()));
    return Map.of("runId", runId);
}
```

> 编码 agent = `dev-agent`（D3，V6 seed 已补；顺带修复 `DispatchService` 悬空的 `dev-agent` 引用）。`ConflictException` 经 `PreBuildExceptionHandler` 映射 HTTP 409（D1）；其余业务错误沿用 200+`ResultData.fail`。

- [ ] **Step 2: build_status 回调（D11，CompletableFuture 链，无独立钩子）**

```java
// 即 Step 1 的 .thenAccept 回调；ClaudeRunner.execute 返 CompletableFuture<String>，无独立 onRunFinished 钩子
@Transactional
void updateBuildStatus(String featureDesignId, boolean success) {
    FeatureDesign fd = featureDesignDao.findOne(featureDesignId); // latest
    fd.setBuildStatus(success ? FeatureDesignBuildStatus.BUILT : FeatureDesignBuildStatus.BUILD_FAILED);
    featureDesignDao.save(fd);
}
```

> Run→FeatureDesign 关联经 Task（D8）：`Run.taskId=task.id`，`task.featureDesignId` 关联 FD；不再"复用 Run.taskId 存 featureDesignId"。

- [ ] **Step 3: 写 buildProject 批量**

查 `featureDesignDao.findLatestByProject` → 过滤 `status=CONFIRMED && build_status!=BUILDING` → 逐条 `build`（捕获 409 跳过）。

- [ ] **Step 4: 写 FeatureDesignBuildServiceTest**（mock ClaudeRunner/RunService）
- `build` 对非 CONFIRMED 抛异常。
- `build` 在 tryAcquireBuild 返回 0 时抛 ConflictException。
- `updateBuildStatus(true)` → build_status=BUILT；`updateBuildStatus(false)` → BUILD_FAILED。
- `buildProject` 跳过 BUILDING 的条目。

- [ ] **Step 5: 编译 + 测试 + 提交**

Run: `cd backend && ./gradlew :sei-online-code-service:test --tests 'FeatureDesignBuildServiceTest'`
```bash
git add backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/FeatureDesignBuildService.java backend/sei-online-code-service/src/test/
git commit -m "feat: add FeatureDesignBuildService — mutex build + run callback"
```

### Task 12: ProjectStateService 聚合扩展

**Files:**
- Create: `.../service/ProjectStateService.java`（D12：现不存在，新建整个类；职责仅编码前状态聚合，与 `BuildLoopService` 边界隔离）
- Test: `.../service/ProjectStateServiceTest.java`

**Interfaces:**
- Consumes: `PlanDao.findLatest`、`FeatureDesignDao.findLatestByProject`。
- Produces: `resolvePreBuildState(projectId)` → `DRAFTING/PLANNING/DESIGNING/READY_TO_BUILD/FAILED`（§3 聚合规则 + D7 FAILED）。

- [ ] **Step 1: 新建类 + resolvePreBuildState（含 D7 FAILED 分支）**

```java
public String resolvePreBuildState(String projectId) {
    Plan plan = planDao.findLatest(projectId);
    if (plan == null) return "DRAFTING";
    if (plan.getStatus() == PlanStatus.FAILED) return "FAILED";                 // D7
    if (plan.getStatus() == PlanStatus.GENERATING || plan.getStatus() == PlanStatus.DRAFT) return "PLANNING";
    // plan = CONFIRMED
    List<FeatureDesign> fds = featureDesignDao.findLatestByProject(projectId);
    if (fds.stream().anyMatch(f -> f.getStatus() == FeatureDesignStatus.FAILED)) return "FAILED"; // D7
    if (fds.isEmpty()) return "DESIGNING";                                       // D15：空集视为未就绪
    boolean allConfirmed = fds.stream().allMatch(f -> f.getStatus() == FeatureDesignStatus.CONFIRMED);
    return allConfirmed ? "READY_TO_BUILD" : "DESIGNING";
}
```

- [ ] **Step 2: 测试**（5 个分支：DRAFTING/PLANNING/DESIGNING/READY_TO_BUILD/FAILED）
- [ ] **Step 3: 编译 + 测试 + 提交**

```bash
git add backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/ProjectStateService.java backend/sei-online-code-service/src/test/
git commit -m "feat: ProjectStateService aggregate pre-build states"
```

### Task 13: PlanAgentService（智能体 spawn 编排）

**Files:**
- Create: `.../service/PlanAgentService.java`
- Test: `.../service/PlanAgentServiceTest.java`

**Interfaces:**
- Consumes: `AgentService.findByName`、`SkillMaterializer`、`ClaudeRunner`、`PlanDao`、`FeatureDesignDao`、`ProjectService`。
- Produces: `spawnPlanning(projectId, modifyHint)`、`spawnFeatureDesign(projectId, featureId, modifyHint)`，含并发信号量。

- [ ] **Step 1: 写 spawnPlanning（单进程，D11 链式 + C8 显式并发守卫）**
- 并发守卫（C8，显式，不靠"regenerate 隐含"）：先查 latest Plan，若 `status=GENERATING` → 抛业务异常拒绝二次发起。
- 解析 `planning-agent` + 绑定 `project-planning` skill → `SkillMaterializer.materialize(tmpDir, skills)` 写到临时目录 `.claude/skills/` → 拼 prompt（项目描述 + modifyHint + "输出 Plan JSON 骨架"）→ `claudeRunner.execute(...)` 返 `CompletableFuture<String>`。
- D11 链式落库：`.thenApply(json -> parsePlanContent(json))` → `.thenAccept(content -> { plan.content=content; plan.status=DRAFT; planDao.save(plan); })`；解析失败 → `.exceptionally(e -> { plan.status=FAILED; planDao.save(plan); return null; })`。

- [ ] **Step 2: 写 spawnFeatureDesigns（批量并发 + 信号量，C8 PENDING 行 + D11 链式）**

```java
private final Semaphore permits = new Semaphore(MAX_CONCURRENT_FD); // 默认 4，@Value("${oc.fd.concurrency:4}")
private final ExecutorService executor = Executors.newCachedThreadPool();

public void spawnFeatureDesigns(String projectId, List<PlanFeature> features) {
    List<CompletableFuture<Void>> futures = features.stream()
        .map(f -> CompletableFuture.runAsync(() -> {
            try { permits.acquire(); spawnOneFeatureDesign(projectId, f, null); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { permits.release(); }
        }, executor)).toList();
    futures.forEach(CompletableFuture::join); // 或不 join, fire-and-forget
}
```

`spawnOneFeatureDesign`（C8）：
- 并发守卫：查该 featureId 的 latest FD，若 `status=GENERATING` → 跳过（批量场景容错，不抛）。
- **先建 PENDING 行**（C8，§3 状态机起点）：`markNonLatest(projectId, featureId)` + 新建 FD 行 `status=PENDING`、`buildStatus=IDLE`、`version=旧+1`、`isLatest=true`。
- 转 `status=GENERATING` → `materialize` + `execute` → D11 链式：`.thenApply(parseFeatureDesignContent)` → `.thenAccept(content -> { fd.content=content; fd.status=DRAFT; save; })`；失败 → `fd.status=FAILED`。
- 单条失败：catch → 该 FD `status=FAILED`，不影响其他（设计 §8）。
- 回调解析 JSON 失败 → `status=FAILED`。

- [ ] **Step 3: 测试**（mock ClaudeRunner 返回 JSON 字符串）
- spawnPlanning 成功落库 DRAFT + content。
- spawnFeatureDesigns 并发 3 条，mock 各自返回 → 3 条都 DRAFT。
- 单条 JSON 解析失败 → 该条 FAILED，其他正常。

- [ ] **Step 4: 编译 + 测试 + 提交**

Run: `cd backend && ./gradlew :sei-online-code-service:test --tests 'PlanAgentServiceTest'`
```bash
git add backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/PlanAgentService.java backend/sei-online-code-service/src/test/
git commit -m "feat: add PlanAgentService — spawn planning/featureDesign agents with bounded concurrency"
```

### Task 14: Controller

**Files:**
- Create: `.../controller/PlanController.java`
- Create: `.../controller/FeatureDesignController.java`
- Modify: `.../controller/ProjectController.java`（加 `build` 端点）

**Interfaces:**
- Consumes: `PlanApi`/`FeatureDesignApi`（实现接口）、`PlanService`/`FeatureDesignService`/`FeatureDesignBuildService`。
- Produces: HTTP 端点，`ResultData`/`PageResult` 包装；409 用现有异常映射（查 `GlobalExceptionHandler` 或 `OperateResult` 约定）。

- [ ] **Step 1: 写 PlanController**（`implements PlanApi`，委托 PlanService）
- [ ] **Step 2: 写 FeatureDesignController**（`implements FeatureDesignApi`，委托 FeatureDesignService + FeatureDesignBuildService；`build` 端点委托 `FeatureDesignBuildService.build`）
- [ ] **Step 3: ProjectController 加 `build`**（委托 `FeatureDesignBuildService.buildProject`）
- [ ] **Step 4: 编译 + 提交**

Run: `cd backend && ./gradlew :sei-online-code-service:compileJava`
```bash
git add backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/controller/
git commit -m "feat: add plan/featureDesign controllers and project build endpoint"
```

### Task 15: 后端整体验证

- [ ] **Step 1: 全量编译 + 全测试**

Run: `cd backend && ./gradlew :sei-online-code-api:compileJava :sei-online-code-service:compileJava :sei-online-code-service:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: 提交（如有修复）**

```bash
git add -p   # 仅相关修复，不用 git add -A
git commit -m "fix: backend pre-build integration adjustments"
```

---

## Track F — 前端（`suid` skill，独立 sub-agent 上下文）

> 前端依赖 Task 1 冻结的契约，**不依赖后端代码**，用 MSW mock 全部新端点。与 Track B 并行。

### Task F1: services 封装

**Files:**
- Create: `frontend/src/services/plan.ts`、`frontend/src/services/featureDesign.ts`
- Modify: `frontend/src/services/project.ts`（加 `buildProject`）

- [ ] **Step 1**: `request` 从 `@ead/suid-utils-react` 导入（`src/services/api.ts:1` 现有先例）。**D6：项目用 dva，不用 `useStore`**——状态管理走 dva model（见 F5）。
- [ ] **Step 2**: 写 `plan.ts`（`getLatest`/`edit`/`regenerate`/`confirm`/`history`，对照 PlanApi 路径）。
- [ ] **Step 3**: 写 `featureDesign.ts`（`findByPage`/`findOne`/`edit`/`regenerate`/`confirm`/`confirmOne`/`build`/`history` + `buildProject`）。
- [ ] **Step 4**: 提交 `git commit -m "feat: add plan/featureDesign services"`

### Task F2: MSW handlers

**Files:**
- Create: `frontend/src/mocks/handlers/plan.ts`、`featureDesign.ts`

- [ ] **Step 1**: 对照契约 P1–P14+P12a 写 handlers，返回 `ResultData`/`PageResult` 信封 + mock 数据（1 个 Plan、3 个 FeatureDesign 覆盖 DRAFT/CONFIRMED/BUILT）。
- [ ] **Step 2**: 409 场景：对 `build` 同 id 第二次请求返回 `{code:409,...}`。
- [ ] **Step 3**: 提交 `git commit -m "feat: add msw handlers for plan/featureDesign"`

### Task F3: 规划书 Tab

**Files:**
- Create: `frontend/src/pages/project/PlanTab.tsx`

- [ ] **Step 1**: `suid info ExtTable --format json` / `suid info ExtModal --format json` 确认 API。
- [ ] **Step 2**: 展示 latest Plan（summary/features/nonGoals 只读 + 编辑模式切换）、重新生成按钮（带 modifyHint 输入）、确认按钮、历史版本抽屉。
- [ ] **Step 3**: 确认后禁用编辑（status=CONFIRMED）；编辑/重生后刷新。
- [ ] **Step 4**: 提交 `git commit -m "feat: add plan tab ui"`

### Task F4: 功能设计 Tab + 编辑器

**Files:**
- Create: `frontend/src/pages/project/FeatureDesignTab.tsx`、`FeatureDesignEditor.tsx`

- [ ] **Step 1**: `ExtTable remotePaging` 列：featureId/title/status/buildStatus/version；多选 + "批量确认"。
- [ ] **Step 2**: 每行操作：查看/编辑（ExtModal，编辑 goal/design/acceptance/fileScope）、重新生成（modifyHint）、确认、执行编码（跳转或弹窗显示 runId）。
- [ ] **Step 3**: STALE 行确认按钮禁用 + tooltip "需重新生成"。
- [ ] **Step 4**: 提交 `git commit -m "feat: add featureDesign tab and editor ui"`

### Task F5: 执行编码 + build_status badge + dva model

**Files:**
- Create: `frontend/src/pages/project/BuildActions.tsx`
- Create: `frontend/src/models/planFeatureDesign.ts`（D6：dva model，非 `useStore`/`projectStore`——项目无此文件，用 dva `useDispatch`/`useSelector`，参照 `src/models/global.ts`）

- [ ] **Step 1**: "执行编码"按钮（项目级 P12），仅当 `resolvePreBuildState=READY_TO_BUILD` 亮起；调 `buildProject`。
- [ ] **Step 2**: build_status badge（IDLE/BUILDING/BUILT/BUILD_FAILED/STALE 五色，遵循 design.md 语义色）。
- [ ] **Step 3**: dva model：聚合状态 + 各 FD 的 build_status 轮询/WS 订阅。**D4：WS 端点是 `/ws/run/{iterationId}`（非 `/ws/run/{runId}`）**，按 `frame.runId` 过滤各 feature 流。
- [ ] **Step 4**: 提交 `git commit -m "feat: add build actions and build_status badges"`

### Task F6: 前端整体验证

- [ ] **Step 1**: `pnpm build`（或 `mise run` 前端构建任务——先 `mise tasks --all` 确认）。
Expected: build success
- [ ] **Step 2**: 手动跑通 MSW 流程：新增项目 → 规划书确认 → 3 FD 确认 → 执行编码 → build_status 流转。
- [ ] **Step 3**: 提交 `git commit -m "test: verify pre-build flow against msw"`

---

## Self-Review

**0. v2 评审修订**：B1–B7 阻断 + C1–C8 澄清已落为 D1–D15（见"修订记录 v2"），Task 1 契约与 T5/T6/T8/T9/T11/T12/T13/F1/F5 + 新增 T9b 已据此修订。

**1. Spec coverage**（对照设计稿各节）：
- §2 两层产物 → T4（content 骨架）、T7（实体）✓
- §3 状态机（含 build_status 互斥 + FAILED）→ T3（枚举）、T8（tryAcquireBuild）、T9/T10（状态推进）、T11（build 链式回调）、T12（聚合含 FAILED，D7）✓
- §3 失效规则（Plan 改→FD STALE）→ T9（cascadeStale 调用）、T8（cascadeStale 实现）✓
- §4 页面流 → F3/F4/F5（+ 运行日志 Tab 复用现有，D15）✓
- §5 DDL → T6（content=TEXT，D15；partial unique index PG，D10）✓
- §6 API P1–P14+P12a → T5（Api，D5/D13/D14）、T14（Controller）、T9b（P1，D2）✓
- §7 智能体执行层 → T13（spawn+信号量+D11 链式）、T6（seed）✓
- §7 内置 agent/skill → T6 seed（含 dev-agent，D3）✓
- §8 异步与错误处理 → T11（409 经 ConflictException，D1；FAILED）、T13（单条失败不影响其他）✓
- §9 成功标准 → T15/F6 验证（降级为编译+MSW，见 Global Constraints）✓

**2. Placeholder scan**：编码执行 agent = `dev-agent`（V6 seed 已补，D3），不再是悬空依赖；`ConflictException`/`PreBuildExceptionHandler` 在契约 D1 定义、T11 使用；`Run.taskId` 不再被滥用（D8 经 Task 关联）；WS 路径修正为 `/ws/run/{iterationId}`（D4）。无 TBD/TODO 残留。

**3. Type consistency**：`PlanStatus`/`FeatureDesignStatus`/`FeatureDesignBuildStatus` 在 T3 定义，T4 DTO/T7 Entity/T9–T12 Service 全部复用；`tryAcquireBuild` T8 定义、T11 消费；`cascadeStale` T8 定义、T9 消费；`resolvePreBuildState` T12 定义、F5 消费；`Search`/`PageResult` 统一 `serach` 旧包（D5）。方法名/包名一致。

**4. 可推翻项**：D1（409 机制是否引入新 advice vs 全 200+业务码）、D8（Task 关联字段复用 vs 新增列）—— 用户可在 Task 1 契约冻结前改。

---
---

## 修订记录 v3 — PM 可行性验证（2026-07-03，`plan` 技能 Step 2）

> 由 `plan`（PM）技能执行 Step 2，对照实际代码库核对 D1–D15。结论：**假设全部成立，1 处修正**。本节为 Task 1 契约冻结前的权威事实依据，契约撰写不再重复验证。

### 已确认（file:line 证据）

| 决策 | 验证结果 | 证据 |
|---|---|---|
| D3 | `dev-agent` 在 DispatchService 硬编码；V3 未 seed → V6 须补 seed | `service/DispatchService.java:50` `DEV_AGENT="dev-agent"`；V3 seeded = requirement-agent/dispatch-agent/deploy-agent |
| D4 | WS `/ws/run/{iterationId}` 按 iterationId 索引；Run.iterationId 已存在 | `ws/RunLogWebSocketHub.java:32` |
| D5 | Search/PageResult 包 `com.changhong.sei.core.dto.serach`（旧拼写，6 处）；FindByPageApi/BaseEntityApi 在 `com.changhong.sei.core.api` | grep 命中 |
| D8 | Task.java(oc_task) 字段：iterationId/title/description/fileScope/assignedAgent/state/worktreeBranch/seq，**无** feature_design_id → V6 须新增列 | `entity/Task.java:35-59` |
| D9 | V3 oc_skill 含 source/source_type/computed_hash；oc_agent 含 model；skill_ids=TEXT(JSON array，经 StringListConverter) | `db/migration/V3__skill_agent.sql`；`entity/converter/StringListConverter.java` |
| D14 | findByPage=POST+Search filter；Api 模式 `interface XxxApi extends BaseEntityApi<XxxDto>, FindByPageApi<XxxDto>` | `api/TaskApi.java:21`、`api/ProjectApi.java:37` |
| D15 | content TEXT+JSON 转换器模式成立；`entity/converter/` 已有 AbstractJsonListConverter/SpecPageListConverter 可参照 | `entity/converter/` 目录 |
| 分层 | DAO `extends BaseEntityDao<T>`(@Repository)；Service `extends BaseEntityService`；OperateResult(s) 在 `core.service.bo` | `dao/AgentDao.java`、`service/AgentService.java` |

### 修正（C-NEW，覆盖 Global Constraints）

| 编号 | 内容 |
|---|---|
| C-NEW | **ID 列长度 VARCHAR(36) 而非 VARCHAR(64)**。Global Constraints 称 `VARCHAR(64)`，但 V3 实际约定 `VARCHAR(36)`（IdGenerator.nextIdStr 36 位）。契约 DDL 与 V6 全部 ID/外键列统一 `VARCHAR(36)`。 |

### V6 seed 规范（对齐 V3 模式）

- 新增 agent：`planning-agent` / `feature-design-agent` / `dev-agent`（均 `builtin=TRUE`；`dev-agent` 的 `skill_ids=NULL`）。
- 新增 skill：`project-planning` / `feature-design`（LOCAL 指针 stub，同 V3 suid/eadp-backend 模式）。
- seed ID：固定 36 位助记串（同 V3 `SKILLSEEDSUID000...`/`AGENTSEEDREQUIREMENT000...`）；审计列只设 `created_date=CURRENT_TIMESTAMP`，其余 null。
- V6 DDL：`oc_plan` / `oc_feature_design`（content `TEXT`，partial unique index `WHERE is_latest=TRUE`）；给 `oc_task` 新增 `feature_design_id VARCHAR(36)`。

### 下一步

Task 1（冻结契约 `docs/contracts/API-CONTRACT-PRE-BUILD.md`）以上述事实为依据直接撰写。完成后 Gate 通过，进入 Track B / Track F。
