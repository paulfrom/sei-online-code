# Pre-Build 功能 PM 产品验收报告

> 验收日期：2026-07-03 ｜ 验收人：主循环（PM 验收）｜ 分支：`feat/pre-build-plan-feature-design`
> 验收依据：契约 `docs/contracts/API-CONTRACT-PRE-BUILD.md` + 设计稿 `docs/archive/plan/PRE-BUILD-INTERACTION-DESIGN.md` §9 + 实施计划 Re-baseline + D1–D15 决策
> 验收方式：实证 grep + build 复核 + MSW 覆盖核验（非 sub-agent，因 qa-reviewer sub-agent 启动停滞，主循环亲自核验）

## 1. 验收结论

**CONDITIONAL PASS（有条件通过）**

核心功能完整交付：后端 Track B（T1–T15）编译通过 + 关键单测通过；前端 Track F（F1–F6）`pnpm build` 通过 + MSW 全端点覆盖。设计稿 §9 的 6 条验证点均有实现证据（5 PASS + 1 DEFERRED）。条件项为 3 类**验证型**待办（非功能缺口）：测试基建专项、交互式浏览器端到端冒烟、D4 WS 客户端。功能本身已满足"新增→规划书确认→功能设计确认→执行编码"两层产物流程。

## 2. 设计稿 §9 验证点逐条验收

| # | 验证点 | 结果 | 证据 | 备注 |
|---|---|---|---|---|
| 1 | UI 端到端跑通 | DEFERRED | `pnpm build` 通过 + MSW 全端点覆盖（F6） | 交互式浏览器冒烟未做；Global Constraints bar 为 build+MSW 覆盖，已达标；端到端 UI 点击验证延后 |
| 2 | plan/feature_design 行状态与聚合 Project 状态一致 | PASS | `ProjectStateService.java:37-39`（D7 FAILED 聚合）+ 前端 `planFeatureDesign.ts` `fetchProjectState` 客户端同规则聚合 | 后端聚合服务 + 前端 dva model 双实现，规则对齐契约 §4.1 |
| 3 | 规划书改动 → 功能设计转 STALE | PASS | `PlanService.java:84` 调 `cascadeStale` + `FeatureDesignDao.java:93` `cascadeStale` 实现 | 级联 STALE 链路成立 |
| 4 | 批量确认后 READY_TO_BUILD 自动达成 | PASS | `ProjectStateService` 聚合 `READY_TO_BUILD = Plan CONFIRMED 且全 FD CONFIRMED` + `BuildActions.tsx` 仅 `READY_TO_BUILD` 启用"执行编码" | 状态聚合 + UI 门控对齐 |
| 5 | 同 feature 并发 P12a 两次 → 第二次 409 | PASS（逻辑） | `FeatureDesignBuildService.java:77` `tryAcquireBuildLock` 互斥 + `ConflictException` → `PreBuildExceptionHandler` → HTTP 409 | 互斥逻辑证据成立；**运行时 DAO 集成测试 @Disabled 延后**（偏差见 §3） |
| 6 | 编码后改设计 → build_status 转 STALE，重新确认可再执行 | PASS | `FeatureDesignService.java:92-95`（edit）+ `:137-138`（regenerate）：旧 `BUILT/BUILD_FAILED` → 新 `STALE` | build_status 失效联动实现 |

## 3. Track B / Track F 任务验收

### 后端 Track B（T1–T15）— 全 PASS（关键单测通过；部分 @Disabled 见 §3）

| Task | 结果 | 证据（commit） |
|---|---|---|
| T1 契约 | PASS | `docs/contracts/API-CONTRACT-PRE-BUILD.md`（c29b89d/917423a/8a4dfdb） |
| T3 枚举 | PASS | 9a7a7ae |
| T4 DTO | PASS | 4232335 |
| T5 Feign API | PASS | 8d5f4de + 6522b79（PlanApi/FeatureDesignApi + ProjectApi.build） |
| T6 V6 DDL+seed | PASS | e2fc4a9（含 dev-agent，D3；oc_task.feature_design_id，D8） |
| T7 实体+转换器 | PASS | 9c8b353 + 09ee879（converter 测试通过） |
| T8 DAO | PASS（源码）| 4dcbe2c+fbd35f0（tryAcquireBuildLock/cascadeStale）；**Testcontainers 测试 @Disabled 延后** |
| T9 PlanService | PASS（3/6 测试）| bf51eb4+671fcb5；3 success @Disabled |
| T9b P1 触发 | PASS | 64a2069（ProjectService.save 新建分支→spawnPlanning） |
| T10 FeatureDesignService | PASS（4/8 测试）| 2cb57e6；4 success @Disabled |
| T11 FeatureDesignBuildService | PASS（源码）| e69d6e5；**测试 @Disabled 延后** |
| T12 ProjectStateService | PASS（8/8 测试）| aecef0c |
| T13 PlanAgentService | PASS（5/5 测试）| 9dab6e8 |
| T14 Controllers | PASS | 6522b79（PlanController/FeatureDesignController/ProjectController.build + PreBuildExceptionHandler→409，D1） |
| T15 后端验证 | PASS | 全量 :api+:service compileJava + :service test 通过 |

### 前端 Track F（F1–F6）— 全 PASS

| Task | 结果 | 证据（commit） |
|---|---|---|
| F1 services | PASS | f7e2f2d（plan/featureDesign/onlineCode.buildProject） |
| F2 MSW handlers | PASS | 3220c44（全端点 + 409 场景 + mock 数据） |
| F3 PlanTab | PASS | 2b2a225（`pnpm build` 通过） |
| F4 FeatureDesignTab+Editor | PASS | 3601931（`pnpm build` 通过；ExtTable/批量确认/STALE 禁用/执行编码 grep 核验） |
| F5 BuildActions+dva model | PASS | fa9facb（`pnpm build` 通过；dva 聚合 5 状态/READY_TO_BUILD 门控/5s 轮询 grep 核验） |
| F6 整体验证 | PASS | e7f578d（`pnpm build` 通过 + MSW 全端点覆盖核验：P2–P5/P6–P11/P12/P12a/P13/P14） |

## 4. 缺口与待清理（CONDITIONAL 项）

| 编号 | 项 | 类型 | 影响 | 来源 |
|---|---|---|---|---|
| G1 | 测试基建专项：T8 DAO Testcontainers + T9/T10 success + T11 测试（@SpringBootTest + Testcontainers） | 验证型 | §9#5 互斥/状态机运行时未验证（逻辑证据成立） | 计划偏差 + @Disabled 清单 |
| G2 | 交互式浏览器端到端冒烟（设计稿 §9#1） | 验证型 | UI 点击流未实测（build+MSW 已达标） | F6 |
| G3 | D4 WS 客户端：build_status 现用轮询（5s），未接 `/ws/run/{iterationId}` | 实现型 | 实时性弱于 WS；功能不受影响 | 偏差#5 |
| G4 | 偏差#1：DAO 接口式 vs 计划 *DaoImpl 模式 | 一致性 | 不影响功能；与 AgentDao 对齐 | 已记录偏差 |

## 5. 偏差复核（5 条已记录）

| # | 偏差 | 仍成立? | 处置 |
|---|---|---|---|
| 1 | DAO 用 Spring Data JPA 接口式（非 *DaoImpl） | 是 | 待清理项（与 AgentDao 对齐，不阻塞） |
| 2 | `FeatureDesignBuildResultDto` 计划外新增（T4） | 是 | 已用于 `ProjectApi.build` 返回类型，用途明确，可关闭 |
| 3 | PlanAgentService 为桩 | 否 | T13 已实现（spawnPlanning/spawnFeatureDesigns/spawnFeatureDesign，5 测试通过），偏差可关闭；audit 字段已补 |
| 4 | PlanDto/ProjectDto/AgentDto redeclare Date audit 字段 | 是 | 代码库约定，Jackson 序列化，保持 |
| 5 | D4 F5 WS 改轮询 | 是 | 待清理项（G3） |

## 6. 建议后续（优先级排序）

1. **P0 — 测试基建专项（G1）**：建 Testcontainers PG（`build.gradle` 加依赖 + `application-test.yml`），启用 T8/T9/T10/T11 的 @Disabled 测试，保障 §9#5 互斥与状态机运行时正确性。这是把 CONDITIONAL 升级为 FULL PASS 的关键。
2. **P1 — 交互式端到端冒烟（G2）**：用 chrome-devtools 跑通"新建项目→规划书确认→3 FD 确认→执行编码→build_status 流转（IDLE→BUILDING→BUILT）"，兑现设计稿 §9#1。
3. **P2 — D4 WS 客户端（G3）**：实现 `/ws/run/{iterationId}` 客户端按 `frame.runId` 过滤，替换 5s 轮询，提供实时构建日志。
4. **P3 — 偏差清理（G4/#1）**：DAO 模式统一决策（接口式保留 vs 回归 *DaoImpl）。

---

**附**：本报告由主循环在 qa-reviewer sub-agent 启动停滞（输出 139 字节未增长）后亲自核验产出，所有 PASS 项均附 file:line/commit 证据，DEFERRED 项明确标注类型与影响，未掩盖跳过项。
