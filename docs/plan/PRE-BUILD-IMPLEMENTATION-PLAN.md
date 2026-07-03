# 编码前交互设计 实施计划 — 规划书 + 功能设计（两层产物）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Dispatch basis（项目 CLAUDE.md 强制）**：前后端 MUST 由**独立 sub-agent 在独立上下文**中开发，永不混在一个上下文里。先冻结契约（Task 1），再 Track B（后端，`eadp-backend` skill）与 Track F（前端，`suid` skill）分别开发。
> 设计稿：`docs/plan/PRE-BUILD-INTERACTION-DESIGN.md`。

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
- 本轮后端**编译通过即达标**（沿用 Phase 1–5 bar），不要求本地运行；前端 build 通过 + MSW handlers 覆盖新端点。

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
| `.../service/ProjectStateService.java` | 编码前项目状态实时聚合（修改现有类，新增方法） | T12 |
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
| `src/store/projectStore.ts`（扩展现有） | 项目状态聚合 + build_status 展示 | F5 |

---

## Task 1: 冻结接口契约到 `docs/contracts/`

**Files:**
- Create: `docs/contracts/API-CONTRACT-PRE-BUILD.md`

**Interfaces:**
- Produces: 冻结的 P1–P14 + P12a 端点定义、DTO schema、状态枚举，作为 Track B / Track F 的共同唯一依据。Track B 与 Track F 只读此契约，互不读对方代码。

- [ ] **Step 1: 写契约文档**

内容包含（逐字取自设计稿 §2/§3/§5/§6，不发明新字段）：

1. **Scope 表**：In = Plan/FD CRUD + 状态机 + build 互斥 + 内置 agent/skill seed + FeatureDesignBuildService；Out = 改动现有 Spec/DispatchService、真实 git clone、多租户。
2. **Domain payload**：`PlanDto`、`FeatureDesignDto`（含 `status` / `buildStatus` / `version` / `isLatest` / `content`）、`PlanContent`（`summary/techAssumptions/features[]{featureId,title,outline}/nonGoals`）、`FeatureDesignContent`（`featureId/goal/design/acceptance[]/fileScope`）。
3. **枚举**：`PlanStatus{GENERATING,DRAFT,CONFIRMED,FAILED}`、`FeatureDesignStatus{PENDING,GENERATING,DRAFT,CONFIRMED,STALE,FAILED}`、`FeatureDesignBuildStatus{IDLE,BUILDING,BUILT,BUILD_FAILED,STALE}`。
4. **状态机图**（设计稿 §3 原样）。
5. **端点表 P1–P14 + P12a**（设计稿 §6 原样），每个端点写明 Method/Path/Request/Response data/状态推进/失败码（409 用于 build 互斥与 BUILDING 态编辑拒绝）。
6. **DDL**（设计稿 §5 原样，表名 `oc_plan` / `oc_feature_design`）。
7. **内置 seed**：两个 agent（`planning-agent`/`feature-design-agent`，`builtin=true`）+ 两个 skill（`project-planning`/`feature-design`，`LOCAL` seed）的 seed 行结构与 V3 同模式。

- [ ] **Step 2: 提交契约**

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
import com.changhong.sei.core.dto.PageResult;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.search.Search;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Valid
@FeignClient(name = "${sei.feign.client.sei-online-code:sei-online-code}", path = FeatureDesignApi.PATH)
public interface FeatureDesignApi extends BaseEntityApi<FeatureDesignDto>, FindByPageApi<FeatureDesignDto> {
    String PATH = "featureDesign";

    @PostMapping("findByPage")
    @Operation(summary = "列表（按 projectId）")
    PageResult<FeatureDesignDto> findByPage(@RequestBody Search search);

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
    @PostMapping(path = "build", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "批量执行编码", description = "对该项目所有 CONFIRMED 且非 BUILDING 的功能设计抢占 build_status 并起编码")
    ResultData<java.util.List<java.util.Map<String, String>>> build(@RequestParam("projectId") String projectId);
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
```

> `skill_ids` JSON 形式与 `Agent.java` 的 `StringListConverter` 一致（`["..."]`）。

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

- [ ] **Step 5: 写互斥抢占单测（H2/原生 SQL 兼容性确认后；若 H2 不支持 partial unique index 则用 `@DataJpaTest` + Testcontainers PG，沿用现有测试约定——查现有 `*DaoImplTest` 选哪种）**

`FeatureDesignDaoImplTest`：
- 插入一条 FD（build_status=IDLE）→ `tryAcquireBuild` 返回 1，DB 中 build_status=BUILDING。
- 再调 `tryAcquireBuild` 同 id → 返回 0（互斥生效）。
- `cascadeStale`：插一条 BUILT + 一条 IDLE → 调用后 BUILT→STALE(build_status)、两条 status 都=STALE。

> 先 `grep -r "Testcontainers\|@DataJpaTest" backend/sei-online-code-service/src/test` 确认现有测试栈，沿用之。

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
- `edit(projectId, content)`：取 latest Plan → 若 `status=GENERATING` 抛业务异常（409）；否则 `markNonLatest` + 新建 version+1 行（`status=DRAFT`、`isLatest=true`、`content`）→ `featureDesignDao.cascadeStale(projectId)`。
- `regenerate(projectId, modifyHint)`：`markNonLatest` + 新行 `status=GENERATING`、version+1 → 调 `planAgentService.spawnPlanning(projectId, modifyHint)` 异步生成 → 回调落库 `status=DRAFT`+`content`。
- `confirm(projectId)`：latest Plan 必须 `status=DRAFT` → 置 `CONFIRMED` → 对 `plan.content.features[]` 每个调 `planAgentService.spawnFeatureDesign(...)`（并发信号量在 T13）。
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
- Produces: `build(featureDesignId)` → `{runId}`；`buildProject(projectId)` → 批量；Run 完成回调 `onRunFinished(runId, success)` → 更新 `build_status`。

- [ ] **Step 1: 写 build 单条**

```java
@Transactional
public Map<String,String> build(String featureDesignId) {
    FeatureDesign fd = featureDesignDao.findOne(featureDesignId); // latest
    if (fd == null) throw new BusinessException("功能设计不存在");
    if (fd.getStatus() != FeatureDesignStatus.CONFIRMED)
        throw new BusinessException("设计未确认，不可执行编码");        // 422
    int acquired = featureDesignDao.tryAcquireBuild(featureDesignId);
    if (acquired == 0) throw new ConflictException("该功能正在构建中"); // 409
    // 抢占成功 → 解析 agent + skill + workspace worktree → 建 Task + Run → ClaudeRunner.execute
    Agent agent = agentService.findByName("feature-design-agent-build"); // 或专用 dev agent；见 Step 2 注
    String runId = IdGenerator.nextIdStr();
    // ... 建 Run 行(RUNNING), spawn ClaudeRunner, 回调更新 build_status
    return Map.of("runId", runId);
}
```

> **注**：编码执行用的是"开发 agent"（写代码），不是 `feature-design-agent`（产设计 JSON）。设计稿 §7 的 `feature-design-agent` 仅产设计稿。编码执行的 agent 选择沿用现有 Task.assignedAgent 解析逻辑——本任务复用现有 dev agent 体系（Phase 3 自定义 agent 或内置 dispatch/dev）。若现无内置 dev agent，本任务在 V6 或代码里不新增，而是要求项目至少有一个自定义 dev agent（沿用 Phase 2 约束）。**在契约里标注此依赖**。

- [ ] **Step 2: 写 onRunFinished 回调**

```java
@Transactional
public void onRunFinished(String runId, boolean success) {
    Run run = runService.findOneByRunId... // 按 run.taskId → feature_design id
    FeatureDesign fd = featureDesignDao.findOne(run.getTaskId()); // taskId 复用为 featureDesignId
    fd.setBuildStatus(success ? BUILT : BUILD_FAILED);
    featureDesignDao.save(fd);
}
```

> Run 与 feature_design 的关联：建 Run 时 `taskId` 存 `featureDesignId`（复用 Run.taskId 字段，不新增列）。

- [ ] **Step 3: 写 buildProject 批量**

查 `featureDesignDao.findLatestByProject` → 过滤 `status=CONFIRMED && build_status!=BUILDING` → 逐条 `build`（捕获 409 跳过）。

- [ ] **Step 4: 写 FeatureDesignBuildServiceTest**（mock ClaudeRunner/RunService）
- `build` 对非 CONFIRMED 抛异常。
- `build` 在 tryAcquireBuild 返回 0 时抛 ConflictException。
- `onRunFinished(true)` → build_status=BUILT；`onRunFinished(false)` → BUILD_FAILED。
- `buildProject` 跳过 BUILDING 的条目。

- [ ] **Step 5: 编译 + 测试 + 提交**

Run: `cd backend && ./gradlew :sei-online-code-service:test --tests 'FeatureDesignBuildServiceTest'`
```bash
git add backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/FeatureDesignBuildService.java backend/sei-online-code-service/src/test/
git commit -m "feat: add FeatureDesignBuildService — mutex build + run callback"
```

### Task 12: ProjectStateService 聚合扩展

**Files:**
- Modify: `.../service/ProjectStateService.java`（现有类，新增 pre-build 聚合方法）
- Test: `.../service/ProjectStateServiceTest.java`

**Interfaces:**
- Consumes: `PlanDao.findLatest`、`FeatureDesignDao.findLatestByProject`。
- Produces: `resolvePreBuildState(projectId)` → `DRAFTING/PLANNING/DESIGNING/READY_TO_BUILD`（设计稿 §3 聚合规则）。

- [ ] **Step 1: 新增方法**（不改动现有 lifecycle 状态方法，新增并列方法供新端点用）

```java
public String resolvePreBuildState(String projectId) {
    Plan plan = planDao.findLatest(projectId);
    if (plan == null) return "DRAFTING";
    if (plan.getStatus() == PlanStatus.GENERATING || plan.getStatus() == PlanStatus.DRAFT) return "PLANNING";
    // CONFIRMED
    List<FeatureDesign> fds = featureDesignDao.findLatestByProject(projectId);
    if (fds.isEmpty()) return "DESIGNING"; // 视为未就绪
    boolean allConfirmed = fds.stream().allMatch(f -> f.getStatus() == FeatureDesignStatus.CONFIRMED);
    return allConfirmed ? "READY_TO_BUILD" : "DESIGNING";
}
```

- [ ] **Step 2: 测试**（4 个分支 + FAILED 透传）
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

- [ ] **Step 1: 写 spawnPlanning**（单进程）
- 解析 `planning-agent` + 绑定 `project-planning` skill → `SkillMaterializer` 写到临时目录 `.claude/skills/` → 拼提示词（项目描述 + modifyHint + "输出 Plan JSON 骨架"）→ `ClaudeRunner.execute` → 回调解析 stdout JSON → 落库 Plan.content + status=DRAFT。
- 并发约束：同项目已有 GENERATING 的 Plan 则拒绝（PlanService.regenerate 已隐含，这里加防御）。

- [ ] **Step 2: 写 spawnFeatureDesign**（批量并发 + 信号量）

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

- 单条失败：catch → 该 FD `status=FAILED`，不影响其他。
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

- [ ] **Step 1**: 用 `suid` skill 确认 `request`/`useStore` 用法。`suid info request --format json`。
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

### Task F5: 执行编码 + build_status badge + store

**Files:**
- Create: `frontend/src/pages/project/BuildActions.tsx`
- Modify: `frontend/src/store/projectStore.ts`

- [ ] **Step 1**: "执行编码"按钮（项目级 P12），仅当 `resolvePreBuildState=READY_TO_BUILD` 亮起；调 `buildProject`。
- [ ] **Step 2**: build_status badge（IDLE/BUILDING/BUILT/BUILD_FAILED/STALE 五色，遵循 design.md 语义色）。
- [ ] **Step 3**: store 扩展：聚合状态 + 各 FD 的 build_status 轮询/WS 订阅（复用现有 WS `/ws/run/{runId}`）。
- [ ] **Step 4**: 提交 `git commit -m "feat: add build actions and build_status badges"`

### Task F6: 前端整体验证

- [ ] **Step 1**: `pnpm build`（或 `mise run` 前端构建任务——先 `mise tasks --all` 确认）。
Expected: build success
- [ ] **Step 2**: 手动跑通 MSW 流程：新增项目 → 规划书确认 → 3 FD 确认 → 执行编码 → build_status 流转。
- [ ] **Step 3**: 提交 `git commit -m "test: verify pre-build flow against msw"`

---

## Self-Review

**1. Spec coverage**（对照设计稿各节）：
- §2 两层产物 → T4（content 骨架）、T7（实体）✓
- §3 状态机（含 build_status 互斥）→ T3（枚举）、T8（tryAcquireBuild）、T9/T10（状态推进）、T11（build 回调）、T12（聚合）✓
- §3 失效规则（Plan 改→FD STALE）→ T9（cascadeStale 调用）、T8（cascadeStale 实现）✓
- §4 页面流 → F3/F4/F5 ✓
- §5 DDL → T6 ✓
- §6 API P1–P14+P12a → T5（Api）、T14（Controller）✓
- §7 智能体执行层 → T13（spawn+信号量）、T6（seed）✓
- §7 内置 agent/skill → T6 seed ✓
- §8 异步与错误处理 → T11（409/FAILED）、T13（单条失败不影响其他）✓
- §9 成功标准 → T15/F6 验证 ✓

**2. Placeholder scan**：T11 Step 1 的"编码执行用 dev agent 而非 feature-design-agent"已在注里显式标注依赖，并在契约 Task 1 要求标注——这是显式依赖声明非占位符。无 TBD/TODO 残留。

**3. Type consistency**：`PlanStatus`/`FeatureDesignStatus`/`FeatureDesignBuildStatus` 在 T3 定义，T4 DTO/T7 Entity/T9–T12 Service 全部复用同名枚举；`tryAcquireBuild` 在 T8 定义、T11 消费；`cascadeStale` 在 T8 定义、T9 消费；`resolvePreBuildState` 在 T12 定义、F5 消费。方法名一致。

---