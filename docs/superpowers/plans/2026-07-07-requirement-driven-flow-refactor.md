# Requirement-Driven Flow Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current project-level `Plan -> Spec -> FeatureDesign -> Build` flow with a requirement-scoped flow: `Project -> Requirement(PRD) -> OverviewDesign -> DetailedDesign -> CodingTask -> Run`.

**Architecture:** `Project` becomes a passive container with repository/workspace metadata and project-level execution settings. Each `Requirement` owns exactly one PRD and one overview design; the overview design emits feature-scoped detailed designs; each confirmed detailed design creates one coding task. The old `FeatureDesign` stage is removed from the user-visible workflow because it does not bind cleanly to detailed design and breaks the coding traceability chain.

**Tech Stack:** Java + Spring Boot + sei-core + Gradle backend; React + UmiJS + @ead/suid frontend; PostgreSQL with SQL migration scripts; existing CLI runner and agent infrastructure.

---

## Confirmed Decisions

- Project creation saves metadata only; it must not trigger plan/design generation.
- Project metadata includes `gitUrl`, `workspacePath`, and project-level `autoRunCodingTask`.
- Requirements are project-scoped and listed inside the project detail route.
- Creating a requirement automatically starts PRD generation with `prd-agent`.
- One requirement has one PRD, one overview design, many feature-scoped detailed designs, and many coding tasks.
- Overview design may keep `modules[]` for grouping, but detailed design generation is per feature.
- PRD, overview design, and detailed design are strong typed JSON first, with readable text fields where useful.
- Once a stage is confirmed and the next stage exists, the confirmed stage is frozen: no edit and no regenerate.
- The user-visible `FeatureDesign` stage is removed.
- `DetailedDesign.confirm` creates one `CodingTask(PENDING)` per detailed design.
- Project-level `autoRunCodingTask` decides whether new coding tasks execute immediately.
- Coding task reruns are allowed; each run creates a new `Run`.
- Reruns require user `rerunPrompt`; failure reason is system generated and automatically included in the next run prompt.
- Existing old business data is not migrated.

## Target Domain Model

- `Project`
  - Add `gitUrl?: string`
  - Add `workspacePath: string`
  - Add `autoRunCodingTask: boolean`
  - Keep `name`, `design` only as project metadata; do not use `design` as execution input directly.
- `Requirement`
  - `projectId`, `title`, `description`
  - `status`: `PRD_GENERATING | PRD_REVIEW | PRD_CONFIRMED | FAILED`
  - `prdVersion`, `prdContent`, failure metadata, trigger metadata
- `OverviewDesign`
  - `projectId`, `requirementId`
  - `status`: `GENERATING | DRAFT | CONFIRMED | FAILED`
  - `version`, `content`, failure metadata
- `DetailedDesign`
  - `projectId`, `requirementId`, `overviewDesignId`
  - `moduleId`, `moduleTitle`, `featureId`, `featureTitle`
  - `status`: `GENERATING | REVIEW | CONFIRMED | FAILED`
  - `version`, `content`, failure metadata
- `CodingTask`
  - `projectId`, `requirementId`, `detailedDesignId`, `detailedDesignVersion`
  - `status`: `PENDING | RUNNING | SUCCEEDED | FAILED | CANCELLED | STALE`
  - `title`, `description`, `fileScope`, latest failure metadata
- `Run`
  - Add or migrate toward `codingTaskId`
  - Store `runNo`, `triggerSource`, `userPrompt`, `failureSummary`, `failureReason`, `worktreePath`

## Target Routes

- Project list remains `/online-code/list`.
- Project detail remains `/online-code/project?id={projectId}`.
- Project detail tabs become:
  - `requirements`
  - `codingTasks`
  - `settings`
- Requirement detail is a single page with step layout:
  - `/online-code/requirement?id={requirementId}`
- Requirement detail steps:
  - PRD
  - Overview Design
  - Detailed Designs
  - Coding Tasks
  - Runs

## Backend File Map

- Create API contracts:
  - `docs/contracts/API-CONTRACT-REQUIREMENT-FLOW.md`
- Modify:
  - `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/ProjectDto.java`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/Project.java`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/ProjectService.java`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/WorkspaceManager.java`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/Run.java`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/RunService.java`
- Create:
  - `RequirementApi`, `OverviewDesignApi`, `DetailedDesignApi`, `CodingTaskApi`
  - DTOs, request DTOs, enums, entities, DAOs, services, controllers for the four new concepts
  - `RequirementAgentService`, `OverviewDesignAgentService`, `DetailedDesignAgentService`
  - `CodingTaskExecutionService`
  - SQL migration scripts for project columns and new tables
- Keep temporarily:
  - `Plan`, `Spec`, `FeatureDesign`, old `Task`, and their APIs, but remove them from new UI entry points.

## Frontend File Map

- Modify:
  - `frontend/src/pages/OnlineCode/List.tsx`
  - `frontend/src/pages/OnlineCode/ProjectDetail.tsx`
  - `frontend/config/router.config.ts`
  - `frontend/src/services/onlineCode.ts`
  - `frontend/src/mocks/handlers.ts`
  - `frontend/src/mocks/db.ts`
- Create:
  - `frontend/src/services/requirement.ts`
  - `frontend/src/services/overviewDesign.ts`
  - `frontend/src/services/detailedDesign.ts`
  - `frontend/src/services/codingTask.ts`
  - `frontend/src/pages/OnlineCode/RequirementListTab.tsx`
  - `frontend/src/pages/OnlineCode/CodingTaskTab.tsx`
  - `frontend/src/pages/OnlineCode/ProjectSettingsTab.tsx`
  - `frontend/src/pages/OnlineCode/RequirementDetail.tsx`
  - Step components under `frontend/src/pages/OnlineCode/requirement/`
- De-expose:
  - `PlanTab`
  - `DetailedDesignTab`
  - `FeatureDesignTab`
  - `BuildActions`

## Task 1: Contract First

- [ ] Create `docs/contracts/API-CONTRACT-REQUIREMENT-FLOW.md`.
- [ ] Define DTOs for `ProjectDto`, `RequirementDto`, `OverviewDesignDto`, `DetailedDesignDto`, `CodingTaskDto`, and `RunDto`.
- [ ] Define status enums and legal transitions.
- [ ] Define endpoints for project settings, requirement creation/regeneration/edit/confirm, overview generation/edit/confirm, detailed design generation/edit/confirm/batch-confirm, coding task run/rerun/cancel, and run history.
- [ ] Define freeze rules: confirmed upstream stages cannot be edited or regenerated after downstream creation.
- [ ] Define rerun rules: rerun requires `rerunPrompt`; previous failure reason is system included.
- [ ] Define workspace initialization behavior before coding execution.

Verification:

```bash
test -f docs/contracts/API-CONTRACT-REQUIREMENT-FLOW.md
rg "RequirementDto|OverviewDesignDto|DetailedDesignDto|CodingTaskDto|rerunPrompt|autoRunCodingTask" docs/contracts/API-CONTRACT-REQUIREMENT-FLOW.md
```

## Task 2: Backend Project Metadata

- [ ] Add project fields `gitUrl`, `workspacePath`, `autoRunCodingTask`.
- [ ] Add SQL migration scripts for the new columns.
- [ ] Update `ProjectDto` conversion in `ProjectController`.
- [ ] Change `ProjectService.save` so new project creation no longer creates `Plan` or starts `planning-agent`.
- [ ] Ensure default `workspacePath` is generated when omitted.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*ProjectServiceTest'
./gradlew :sei-online-code-service:compileJava
```

## Task 3: Backend Requirement + PRD Stage

- [ ] Add `Requirement` entity, DAO, DTOs, enum, API, controller, service.
- [ ] On create, persist `PRD_GENERATING`, then call `RequirementAgentService.spawnPrd`.
- [ ] On agent success, write `prdContent`, set `PRD_REVIEW`.
- [ ] Allow edit/regenerate only before `PRD_CONFIRMED`.
- [ ] On confirm, freeze PRD and create `OverviewDesign(GENERATING)`.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*RequirementServiceTest'
rg "PRD_GENERATING|PRD_REVIEW|PRD_CONFIRMED" backend/sei-online-code-api backend/sei-online-code-service
```

## Task 4: Backend Overview Design Stage

- [ ] Add `OverviewDesign` entity, DAO, DTOs, enum, API, controller, service.
- [ ] `OverviewDesignAgentService` generates strong typed content with `modules[].features[]`.
- [ ] Allow edit/regenerate only while not confirmed and before detailed designs exist.
- [ ] On confirm, flatten all module features and create one `DetailedDesign(GENERATING)` per feature.
- [ ] Start `DetailedDesignAgentService` per created detailed design.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*OverviewDesignServiceTest'
rg "modules|features|overviewDesignId" backend/sei-online-code-api backend/sei-online-code-service
```

## Task 5: Backend Detailed Design Stage

- [ ] Add `DetailedDesign` entity, DAO, DTOs, enum, API, controller, service.
- [ ] Generate detailed design content from PRD + overview design + one feature.
- [ ] Support single confirm and batch confirm.
- [ ] On confirm, create exactly one `CodingTask(PENDING)` bound to `detailedDesignId + version`.
- [ ] If `Project.autoRunCodingTask = true`, trigger execution after task creation.
- [ ] Forbid edit/regenerate after confirm.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*DetailedDesignServiceTest'
rg "batchConfirm|DetailedDesignStatus|CodingTask" backend/sei-online-code-api backend/sei-online-code-service
```

## Task 6: Backend Coding Task Execution

- [ ] Add `CodingTask` entity, DAO, DTOs, enum, API, controller, service.
- [ ] Add `CodingTaskExecutionService`.
- [ ] Modify `Run` to support `codingTaskId`, `runNo`, `userPrompt`, `failureSummary`, `failureReason`, `triggerSource`.
- [ ] Execute prompt must include PRD, overview design, detailed design, coding task data, previous failure reason when present, and user rerun prompt when provided.
- [ ] First run allows empty prompt; rerun requires non-blank prompt.
- [ ] Enforce one active run per coding task.
- [ ] Initialize workspace before calling CLI.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*CodingTask*'
./gradlew :sei-online-code-service:test --tests '*RunServiceTest'
rg "codingTaskId|rerunPrompt|failureReason" backend/sei-online-code-api backend/sei-online-code-service
```

## Task 7: Frontend Project Shell

- [ ] Update project create/edit form to include `gitUrl`, `workspacePath`, `autoRunCodingTask`.
- [ ] Change project detail tabs to `需求`, `编码任务`, `设置`.
- [ ] Remove user entry points for project-level `PlanTab`, `DetailedDesignTab`, `FeatureDesignTab`, and `BuildActions`.
- [ ] Add project requirement list tab with create requirement action.
- [ ] Add project coding task tab with status filters, execute/rerun, and run history entry.

Verification:

```bash
pnpm -C frontend build
rg "PlanTab|FeatureDesignTab|BuildActions" frontend/src/pages/OnlineCode/ProjectDetail.tsx frontend/src/pages/OnlineCode
```

## Task 8: Frontend Requirement Detail

- [ ] Add `/online-code/requirement?id={requirementId}` route.
- [ ] Build step layout for PRD, overview design, detailed designs, coding tasks, and runs.
- [ ] PRD step supports edit, regenerate with prompt, confirm.
- [ ] Overview step supports edit, regenerate with prompt, confirm.
- [ ] Detailed design step shows feature list; supports drawer detail, edit, regenerate, single confirm, batch confirm.
- [ ] Coding task step supports first run, rerun with required prompt, status display, failure display.
- [ ] Run step shows execution history and failure reason.

Verification:

```bash
pnpm -C frontend build
rg "RequirementDetail|DetailedDesignStep|CodingTaskStep|rerunPrompt" frontend/src
```

## Task 9: Mocks And Local Contract Validation

- [ ] Add MSW handlers for new endpoints.
- [ ] Add mock state transitions for PRD, overview, detailed design, coding task, run.
- [ ] Ensure mocked requirement creation automatically moves to PRD review after generation simulation.
- [ ] Ensure mocked batch detailed design confirm creates coding tasks.
- [ ] Ensure rerun without prompt fails in mocks.

Verification:

```bash
pnpm -C frontend build
rg "requirement|overviewDesign|detailedDesign|codingTask" frontend/src/mocks
```

## Task 10: Old Flow De-Exposure And Cleanup

- [ ] Keep old backend `Plan/Spec/FeatureDesign/Task` classes temporarily.
- [ ] Remove old UI routes and imports from active navigation.
- [ ] Remove old project-list action that calls `generateOverviewDesign`.
- [ ] Confirm `ProjectService.save` no longer references `PlanAgentService`.
- [ ] Add follow-up cleanup doc for deleting old backend flow after new flow stabilizes.

Verification:

```bash
rg "generateOverviewDesign|/online-code/spec|FeatureDesignTab|PlanTab|DetailedDesignTab" frontend/src frontend/config
rg "planAgentService.spawnPlanning" backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/ProjectService.java
```

## End-to-End Acceptance Scenarios

- [ ] Create project with `gitUrl`, default `workspacePath`, `autoRunCodingTask=false`; no requirement, overview, detailed design, or coding task is created.
- [ ] Create requirement; PRD generation starts automatically and reaches `PRD_REVIEW`.
- [ ] Edit PRD, confirm it, and verify overview design generation starts.
- [ ] Confirm overview design and verify multiple feature-scoped detailed designs are created.
- [ ] Batch confirm detailed designs and verify one coding task per detailed design is created.
- [ ] With auto-run off, coding tasks remain `PENDING`.
- [ ] Run one coding task manually without prompt; a run is created.
- [ ] Fail a run; verify system failure reason is stored.
- [ ] Rerun without prompt is rejected.
- [ ] Rerun with prompt includes previous failure reason and creates a new run.
- [ ] Enable project `autoRunCodingTask`; confirm another detailed design and verify task execution starts automatically.

## Final Verification

```bash
./gradlew :sei-online-code-service:test
./gradlew :sei-online-code-service:compileJava
pnpm -C frontend build
git diff --check
```

## Review Notes For Claude

- Pay special attention to whether `Requirement` should store PRD directly or whether a future `PrdVersion` table is needed. Current plan intentionally keeps one requirement as the PRD aggregate to reduce scope.
- Check whether `Run` can be safely extended with `codingTaskId` while old `taskId` users still exist.
- Check whether project-level `autoRunCodingTask` needs queueing/backpressure beyond the existing one-active-run-per-task guard.
- Check whether old `Plan/Spec/FeatureDesign` code should be hidden only at UI level or also guarded at API level.
- Check whether SQL migration scripts should use nullable columns first to avoid breaking existing data.
