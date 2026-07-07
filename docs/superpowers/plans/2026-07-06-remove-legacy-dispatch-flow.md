# Remove Legacy Dispatch Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the unused Spec -> Iteration -> Dispatch -> Preview product flow while preserving the current Spec -> Plan -> FeatureDesign -> Build flow.

**Architecture:** Spec confirmation will become a Spec/Plan concern instead of an Iteration concern. FeatureDesign builds keep using Task/Run internally as runtime records, but old public dispatch/preview screens and APIs disappear. Project state and frontend routing will be driven by Plan/FeatureDesign state.

**Tech Stack:** Java Spring Boot + sei-core + Gradle backend; React + UmiJS + @ead/suid frontend; PostgreSQL/Flyway schema.

---

## File Map

Backend API:
- Modify: `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/api/ProjectApi.java`
- Modify: `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/api/SpecApi.java`
- Delete: `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/api/IterationApi.java`
- Delete: old iteration request DTOs under `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/request/`
- Keep: `TaskDto`, `RunDto`, `TaskState`, `RunState` because FeatureDesign build still uses Task/Run runtime records.

Backend service:
- Modify: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/SpecService.java`
- Modify: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/controller/SpecController.java`
- Modify: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/controller/ProjectController.java`
- Delete: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/controller/IterationController.java`
- Delete: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/DispatchService.java`
- Delete: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/BuildLoopService.java`
- Delete: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/IterationService.java` after moving `confirmSpec`.
- Keep: `Task`, `TaskDao`, `TaskService`, `Run`, `RunDao`, `RunService`, `RunLogWebSocketHub`.

Frontend:
- Modify: `frontend/config/router.config.ts`
- Modify: `frontend/src/pages/OnlineCode/List.tsx`
- Modify: `frontend/src/pages/OnlineCode/Spec.tsx`
- Modify: `frontend/src/pages/OnlineCode/components/LifecycleBadge.tsx`
- Modify: `frontend/src/services/onlineCode.ts`
- Delete: `frontend/src/pages/OnlineCode/Dispatch.tsx`
- Delete: `frontend/src/pages/OnlineCode/Preview.tsx`
- Delete or simplify: `frontend/src/pages/OnlineCode/Timeline.tsx`
- Modify mocks: `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/db.ts`

Tests:
- Modify: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/service/SpecServiceTest.java`
- Delete old iteration/dispatch/build-loop tests.
- Keep and update FeatureDesign build tests.

## Task 1: Backend Contract Cleanup

- [ ] Remove Iteration API endpoints from `IterationApi.java` by deleting the file.
- [ ] Remove old iteration request DTO files: `DeployIterationRequest.java`, `DispatchIterationRequest.java`, `MergeIterationRequest.java`, `AcceptIterationRequest.java`, `CancelIterationRequest.java`, `RetryIterationRequest.java`, and `OptimizeProjectRequest.java`.
- [ ] Remove `ProjectApi.optimize(...)` and its `IterationDto` import from `ProjectApi.java`.
- [ ] Keep `ProjectApi.build(projectId)` because it starts FeatureDesign coding.
- [ ] Verify no backend API source imports deleted request DTOs.

Run:

```bash
rg "OptimizeProjectRequest|DispatchIterationRequest|DeployIterationRequest|MergeIterationRequest|AcceptIterationRequest|CancelIterationRequest|RetryIterationRequest|IterationApi" backend/sei-online-code-api/src/main/java
```

Expected: no matches except none.

## Task 2: Move Spec Confirmation Out Of IterationService

- [ ] Add `PlanService` dependency to `SpecService`.
- [ ] Move current `IterationService.confirmSpec(String specId)` behavior into `SpecService.confirmSpec(String specId)`.
- [ ] Keep these exact guards: Spec must exist and state must be `SPEC_REVIEW`.
- [ ] Set Spec state to `CONFIRMED`, save it, then call `planService.regenerate(spec.getProjectId(), null)`.
- [ ] Change `SpecController.confirm(...)` to call `specService.confirmSpec(...)`.
- [ ] Delete `IterationService.java` after no code references it.

Run:

```bash
rg "IterationService|confirmSpec\\(" backend/sei-online-code-service/src/main/java backend/sei-online-code-service/src/test/java
```

Expected: `confirmSpec` references point to `SpecService` and tests only; no production `IterationService`.

## Task 3: Delete Old Dispatch And Build Loop

- [ ] Delete `DispatchService.java`.
- [ ] Delete `BuildLoopService.java`.
- [ ] Delete `IterationController.java`.
- [ ] Remove `BuildLoopService` injection and `optimize(...)` method from `ProjectController.java`.
- [ ] Remove `convertIterationToDto(...)` and `IterationDto` imports from `ProjectController.java`.
- [ ] Update comments in `TaskService`, `RunService`, `AgentService`, and `AgentBriefWriter` that reference `DispatchService`.

Run:

```bash
rg "DispatchService|BuildLoopService|IterationController|IterationDto|optimize\\(" backend/sei-online-code-service/src/main/java
```

Expected: no matches except archive comments are outside `src/main`.

## Task 4: Backend Tests

- [ ] Delete tests that only cover removed old flow: `IterationServiceTest.java` and `BuildLoopServiceOptimizeTest.java`.
- [ ] Update `SpecServiceTest` to assert confirming a `SPEC_REVIEW` Spec calls `PlanService.regenerate(projectId, null)`.
- [ ] Update any test setup constructors affected by `SpecService` dependency changes.
- [ ] Keep FeatureDesignBuildService tests unchanged unless constructor signatures require updates.

Run:

```bash
./gradlew :sei-online-code-service:test
```

Expected: backend tests pass.

## Task 5: Frontend Route And API Cleanup

- [ ] Remove `/online-code/dispatch` and `/online-code/preview` routes from `frontend/config/router.config.ts`.
- [ ] Remove imports and functions for old iteration actions from `frontend/src/services/onlineCode.ts`: `deployIteration`, `dispatchIteration`, `mergeIteration`, `acceptIteration`, `cancelIteration`, `retryIteration`, `optimizeProject`, `findOneIteration`, `findRunsByPage`, and `findOneRun`.
- [ ] Keep `buildProject(projectId)` because FeatureDesign build uses it.
- [ ] Keep `ResultData`, `PageResult`, `Search`, `ProjectDto`, and `SpecDto`.

Run:

```bash
rg "dispatchIteration|deployIteration|mergeIteration|acceptIteration|cancelIteration|retryIteration|optimizeProject|findOneIteration|findRunsByPage|findOneRun" frontend/src
```

Expected: no matches.

## Task 6: Frontend Page Cleanup

- [ ] Delete `frontend/src/pages/OnlineCode/Dispatch.tsx`.
- [ ] Delete `frontend/src/pages/OnlineCode/Preview.tsx`.
- [ ] Delete `frontend/src/pages/OnlineCode/Timeline.tsx` if it only represents old iteration history.
- [ ] Update `List.tsx` so row action always enters `/online-code/project?id=${record.id}` after initial requirement parsing.
- [ ] Update `Spec.tsx` copy: "确认 Spec 并启动规划" and success message "Spec 已确认，规划生成已启动".
- [ ] Update `LifecycleBadge.tsx` to show only states still used by current UI.

Run:

```bash
rg "/online-code/dispatch|/online-code/preview|Dispatch|Preview|Timeline" frontend/src frontend/config
```

Expected: no product-route matches for deleted pages.

## Task 7: Frontend Mock Cleanup

- [ ] Remove old iteration dispatch/deploy/merge/accept/cancel/retry handlers from `frontend/src/mocks/handlers.ts`.
- [ ] Keep plan and featureDesign mock handlers.
- [ ] Remove old iteration/run state progression helpers from `frontend/src/mocks/db.ts` if no current mock handler uses them.
- [ ] Keep run-log socket utilities if BuildActions still uses live logs.

Run:

```bash
rg "dispatchIteration|deployIteration|mergeIteration|acceptIteration|cancelIteration|retryIteration|iterationsOf|runsOf" frontend/src/mocks frontend/src
```

Expected: no matches unless a retained helper is used by current FeatureDesign build mock code.

## Task 8: Verification

- [ ] Run backend tests:

```bash
./gradlew :sei-online-code-service:test
```

- [ ] Run frontend type/lint/build command available in `frontend/package.json`.
- [ ] Run a final dead-reference scan:

```bash
rg "DispatchService|BuildLoopService|IterationController|IterationApi|/online-code/dispatch|/online-code/preview" backend frontend docs/contracts
```

Expected: no matches in live code or active contracts.

## Self-Review

- The plan preserves Task/Run because FeatureDesignBuildService still depends on them.
- The plan removes user-facing Iteration/Dispatch/Preview flow.
- The plan moves Spec confirmation before deleting IterationService.
- The plan avoids rewriting old Flyway migrations; historical migrations remain needed for schema construction.

