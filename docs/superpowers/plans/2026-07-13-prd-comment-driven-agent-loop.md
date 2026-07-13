# PRD Comment Driven Agent Loop

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. This change crosses backend orchestration, persistence, agent execution, cancellation semantics, and frontend review UX.

**Goal:** Shorten the current requirement-driven chain from `PRD -> OverviewDesign -> DetailedDesign -> CodingTask -> Run` to a PRD-centered execution loop:

`Requirement(PRD) -> PM ExecutionPlan -> frontend/backend agent development -> development result comments -> PM acceptance -> remediation loop until accepted`

Human comments on the PRD are authoritative interrupts: when a human comment is added during active automation, all active agent runs for that requirement must be stopped or invalidated, and the PM agent must re-plan from the latest PRD plus the full comment history.

This plan directly replaces the current overview/detailed-design chain. PRD confirmation must no longer create `OverviewDesign` or `DetailedDesign` rows for the new flow.

---

## Why Change

The current chain is correct for traceability but too long for agentic delivery. `OverviewDesign` and `DetailedDesign` create review stages before code exists, while the user now wants PRD to be the single collaboration surface. Agent output should return to the PRD as comments, and PM acceptance should close the loop there.

The target design keeps the useful parts of the current implementation:

- `Requirement` remains the PRD owner.
- `CodingTask`, `Run`, and workspace execution remain the implementation substrate.
- `Agent`, `CliRunner`, `RunLogWebSocketHub`, and failure metadata are reused.
- `generationToken` style invalidation is reused for stale agent results.

It removes or bypasses the long user-visible design chain for the new flow:

- no user-visible overview design gate
- no user-visible detailed design gate
- no manual detailed-design confirmation before coding

---

## Target Agents

Seed or rename built-in agents to these stable names:

- `prd-agent`: creates or regenerates PRD Markdown.
- `pm-agent`: creates execution plans, assigns tasks, validates agent results, writes acceptance or remediation comments.
- `frontend-dev-agent`: implements frontend tasks, must bind `builtin:suid`.
- `backend-dev-agent`: implements backend tasks, must bind `builtin:eadp-backend`.
- `test-agent`: interprets validation command results and writes task-level or plan-level validation reports.

Keep legacy agents temporarily for compatibility:

- `overview-design-agent`
- `detailed-design-agent`
- `dev-agent`

New orchestration must not depend on legacy overview/detailed design agents.

---

## Target Domain Model

### Requirement

Add fields:

- `automationStatus`: `IDLE | PLANNING | DEVELOPING | ACCEPTING | INTERRUPTED | COMPLETED | FAILED`
- replace with final set: `IDLE | PLANNING | DEVELOPING | VALIDATING | ACCEPTING | DELIVERING | INTERRUPTED | WAITING_HUMAN | COMPLETED | FAILED`
- `activeLoopId`: latest automation loop id for stale-result invalidation
- `acceptedAt`
- `acceptedByAgent`

Keep existing fields:

- `status`: `PRD_GENERATING | PRD_REVIEW | PRD_CONFIRMED | FAILED`
- `prdContent`
- `generationToken`

### RequirementComment

New entity/table `oc_requirement_comment`.

Fields:

- `requirementId`
- `loopId`
- `authorType`: `HUMAN | PM_AGENT | FRONTEND_AGENT | BACKEND_AGENT | SYSTEM`
- `authorName`
- `commentType`: `HUMAN_FEEDBACK | EXECUTION_PLAN | DEV_RESULT | ACCEPTANCE | REMEDIATION | INTERRUPTION | FAILURE`
- extend with: `VALIDATION_RESULT | MR_CREATED | MR_UPDATED | MR_FAILED | MEMORY_UPDATED | MEMORY_UPDATE_FAILED | CONTEXT_SUMMARY_FAILED`
- `content`
- `metadataJson`
- `createdDate`

Rules:

- Human comments are never overwritten.
- Agent comments are append-only.
- PM execution plan, development result, acceptance result, and remediation instructions are all comments.

### ExecutionPlan

New entity/table `oc_execution_plan`.

Fields:

- `requirementId`
- `loopId`
- `version`
- `planType`: `INITIAL | REMEDIATION | CHANGE_REQUEST`
- `status`: `PLANNING | READY | DEVELOPING | ACCEPTING | NEEDS_REMEDIATION | ACCEPTED | INTERRUPTED | FAILED`
- `planJson`
- `summary`
- `createdByAgent`
- `memoryContextId`
- `workspaceMemoryId`
- `createdDate`

`planJson` should be structured:

```json
{
  "goal": "string",
  "tasks": [
    {
      "taskKey": "FE-001",
      "title": "string",
      "description": "string",
      "agent": "frontend-dev-agent",
      "area": "frontend",
      "dependsOn": [],
      "fileScope": ["frontend/src/..."],
      "acceptanceCriteria": ["string"]
    }
  ],
  "risks": ["string"],
  "validation": ["string"]
}
```

### CodingTask

Reuse existing `CodingTask`, but allow creation from `ExecutionPlan` instead of `DetailedDesign`.

Add fields:

- `executionPlanId`
- `planTaskKey`
- `assignedAgent`
- `loopId`

For compatibility, keep `detailedDesignId` nullable.

Status set:

- `PENDING`
- `BLOCKED`
- `RUNNING`
- `VALIDATING`
- `SUCCEEDED`
- `FAILED`
- `VALIDATION_FAILED`
- `CANCELLED`
- `STALE`

`SUCCEEDED` means development succeeded and task-level validation passed. `FAILED` means dev-agent execution failed. `VALIDATION_FAILED` means development completed but task-level validation failed. `BLOCKED` means a dependency failed or failed validation.

### Run

Reuse existing `Run`, but add cancellation/invalidation support:

- `loopId`
- `cancelRequested`
- `invalidatedByCommentId`
- `runType`: `DEVELOPMENT | VALIDATION_COMMAND | TEST_REVIEW | PM_PLANNING | PM_ACCEPTANCE | DELIVERY`
- `memoryContextId`
- `workspaceMemoryId`

All development, validation command execution, test-agent review, PM planning, PM acceptance, and GitLab delivery logs must be visible through the same Run history and filterable by `runType`.

### Requirement Context and Memory

The new flow must integrate with the existing memory implementation instead of redesigning memory semantics.

- Planning, development, test review, and PM acceptance all read the current requirement context and project memory.
- Use or extend existing `RequirementDesignContextService`, `DesignContextPromptAssembler`, `WorkspaceMemoryService`, `MemoryJobService`, and `CodingTaskChangeCollector`.
- Human comments update the current requirement context snapshot, not long-term project memory.
- Long-term project memory is updated only after GitLab MR creation/update succeeds and the requirement reaches `COMPLETED`.
- Add a requirement-level memory job type: `MEMORY_UPDATE_AFTER_REQUIREMENT_DELIVERY`.
- Disable task-level long-term memory update for tasks created by this PRD agent loop.
- Memory update failure does not revert `COMPLETED`; it writes `MEMORY_UPDATE_FAILED` and remains retryable.
- `ExecutionPlan` and `Run` must record the context/memory ids they used for traceability.

---

## Target State Machine

### Normal Flow

1. `prd-agent` writes PRD and sets requirement to `PRD_REVIEW`.
2. User confirms PRD.
3. `pm-agent` creates `ExecutionPlan(READY)` and writes an `EXECUTION_PLAN` comment under the PRD.
4. System creates `CodingTask(PENDING)` rows from `ExecutionPlan.tasks[]`.
5. System starts development tasks using `assignedAgent`, respecting dependencies and lane limits.
6. Each dev agent writes a `DEV_RESULT` comment after completion or failure.
7. After each task succeeds, system runs validation commands and `test-agent` writes a task-level `VALIDATION_RESULT`.
8. When all runnable tasks and task-level validations settle, system runs plan-level validation and `test-agent` writes a plan-level `VALIDATION_RESULT`.
9. `pm-agent` reviews PRD, execution plan, task results, validation reports, run failures, comments, current workspace diff, and memory context.
10. PM writes either:
   - `ACCEPTANCE` comment and marks plan `ACCEPTED`, then requirement `DELIVERING`
   - `REMEDIATION` comment and marks plan `NEEDS_REMEDIATION`
11. If accepted, system commits, pushes, creates or updates a GitLab MR, writes `MR_CREATED` or `MR_UPDATED`, and marks requirement `COMPLETED`.
12. If remediation is needed, system creates a new `ExecutionPlan` version with the same `loopId`, creates/reruns targeted tasks, and repeats the loop.

PM remediation is limited to 3 rounds by default. If the loop does not converge, stop automation, write the PM explanation, and set requirement `WAITING_HUMAN`.

### Development Scheduling

- Use a single shared workspace for all tasks.
- Do not create per-task worktrees in the first version.
- Run at most one frontend task and one backend task concurrently.
- Cross-area tasks may run in parallel only when `dependsOn` is satisfied and `fileScope` does not conflict.
- A dependency is satisfied only when the upstream task is `SUCCEEDED`, meaning development and task-level validation both passed.
- If task-level validation fails, dependent tasks become or remain `BLOCKED`; independent tasks may continue.
- Human interruption does not roll back workspace changes. Current workspace diff becomes input to PM re-planning.

### Validation Flow

- Use a single `test-agent` for the first version.
- Validation report metadata must include `area: frontend | backend | full-stack`.
- Validation command source priority:
  1. `ExecutionPlan.validation.commands[]`
  2. `Project.validationConfig`
  3. built-in defaults:
     - frontend: `pnpm -C frontend build`
     - backend: `./gradlew :sei-online-code-service:compileJava`
     - full-stack: run both defaults
- System executes validation commands and records command, exit code, duration, stdout/stderr summary, and run id.
- `test-agent` interprets those facts and writes `VALIDATION_RESULT`.
- PM acceptance must primarily rely on plan-level validation result. Agent self-reports are auxiliary.
- Plan-level validation failure must not bulk-update task statuses; PM creates a new remediation plan if needed.

### Human Interrupt Flow

When a human adds a comment while automation is in `PLANNING | DEVELOPING | ACCEPTING`:

1. Mark current `ExecutionPlan` as `INTERRUPTED`.
2. Mark requirement `automationStatus = INTERRUPTED`.
3. Request cancellation for active runs.
4. Invalidate current loop by rotating `Requirement.activeLoopId`.
5. Ignore any stale agent completion whose `loopId` does not match the latest requirement loop.
6. Write a `SYSTEM/INTERRUPTION` comment.
7. Start `pm-agent` re-planning from:
   - current PRD
   - all human comments
   - previous execution plan
   - completed/failed development result comments

This must be implemented as logical cancellation even before process-level kill exists. Stale futures must not mutate current state.

Any human comment triggers interruption while automation is active. Do not try to classify comments as ordinary notes versus change requests. UI must warn that submitting a comment interrupts active automation and triggers PM re-planning.

Completed requirements behave differently: a human comment after MR delivery starts a new `CHANGE_REQUEST` loop based on the delivered MR/branch/commit, not a from-scratch PRD plan. If the MR is still opened, update the same MR. If the MR is merged or closed, create a new MR.

### Delivery Flow

Delivery is part of completion.

- PM acceptance success is not final completion.
- Set `automationStatus = DELIVERING` before SCM operations.
- Commit after PM acceptance, not after every task.
- Each delivery/retry runs `git add` and `git commit`; if there are no changes, skip commit and reuse `HEAD`.
- Push source branch to GitLab.
- Source branch format: `feature/req-{requirementId-short}-{loopId-short}`.
- Create or update a GitLab MR.
- If an opened MR exists for the source branch, reuse it and update title/description.
- If only closed/merged MRs exist for that branch, first version should fail delivery and write `MR_FAILED`.
- MR success writes `MR_CREATED` or `MR_UPDATED` with branch, commit hash, target branch, MR URL, and validation summary.
- Requirement reaches `COMPLETED` only after MR creation/update succeeds.
- MR failure writes `MR_FAILED`, records partial delivery facts, and sets `WAITING_HUMAN`.
- Add `POST /requirement/{id}/mr/retry`; after GitLab config is fixed it reruns delivery without rerunning development or PM acceptance.

---

## Backend Implementation Tasks

### Task 1: Contract First

- [ ] Create or update API contract for PRD comment driven loop.
- [ ] Define `RequirementCommentDto`, `ExecutionPlanDto`, request DTOs, enums, and transition rules.
- [ ] Document interrupt semantics and stale-result invalidation.
- [ ] Document direct replacement of overview/detailed-design flow after PRD confirmation.
- [ ] Document GitLab delivery, MR retry, CHANGE_REQUEST loop, validation, memory integration, and max remediation rounds.

Verification:

```bash
rg "RequirementComment|ExecutionPlan|INTERRUPTED|frontend-dev-agent|backend-dev-agent|test-agent|DELIVERING|CHANGE_REQUEST|GitLab|MEMORY_UPDATE_AFTER_REQUIREMENT_DELIVERY" docs/contracts
```

### Task 2: Persistence Model

- [ ] Add `RequirementComment` entity, DAO, DTO, API, controller, and service.
- [ ] Add `ExecutionPlan` entity, DAO, DTO, API, controller, and service.
- [ ] Add migrations for new tables and new nullable columns on `Requirement`, `CodingTask`, and `Run`.
- [ ] Add `automationStatus`, `activeLoopId`, delivery metadata, and completion metadata to `Requirement`.
- [ ] Add `executionPlanId`, `planTaskKey`, `assignedAgent`, and `loopId` to `CodingTask`.
- [ ] Add `runType`, `loopId`, cancellation fields, and memory context references to `Run`.
- [ ] Keep tenant isolation out of the model.

Verification:

```bash
./gradlew :sei-online-code-service:compileJava
rg "oc_requirement_comment|oc_execution_plan|active_loop_id|assigned_agent|run_type|memory_context_id|workspace_memory_id" backend/sei-online-code-service/src/main/resources/db/migration
```

### Task 3: Agent Seeds

- [ ] Seed `pm-agent`, `frontend-dev-agent`, `backend-dev-agent`, and `test-agent`.
- [ ] Bind `frontend-dev-agent` to `builtin:suid`.
- [ ] Bind `backend-dev-agent` to `builtin:eadp-backend`.
- [ ] Keep existing seed ids stable and add new migrations instead of editing old migration files.

Verification:

```bash
rg "pm-agent|frontend-dev-agent|backend-dev-agent|test-agent|builtin:suid|builtin:eadp-backend" backend/sei-online-code-service/src/main/resources/db/migration
```

### Task 4: PM Orchestrator

- [ ] Add `RequirementAutomationService`.
- [ ] Change PRD confirmation so it starts the PM automation loop instead of creating overview design.
- [ ] On PRD confirmation, create a new loop id, prepare requirement context from existing memory services, and call `pm-agent` to produce `ExecutionPlan`.
- [ ] Parse PM JSON strictly and fail with `RequirementComment(FAILURE)` when invalid.
- [ ] Persist PM execution plan and write an `EXECUTION_PLAN` comment.
- [ ] Create coding tasks from plan tasks.
- [ ] Start tasks after transaction commit.
- [ ] Enforce DAG dependencies, fileScope conflict checks, and lane concurrency of frontend=1/backend=1.
- [ ] Generate new `ExecutionPlan` versions for PM remediation while retaining the same `loopId`.
- [ ] Start a `CHANGE_REQUEST` loop when a completed requirement receives a human comment.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*RequirementAutomationServiceTest'
rg "createGeneratingOverview|RequirementAutomationService|CHANGE_REQUEST|dependsOn|fileScope" backend/sei-online-code-service/src/main/java
```

### Task 5: Agent-Specific Development Execution

- [ ] Change coding execution to resolve `task.assignedAgent` instead of always `dev-agent`.
- [ ] Build prompts from PRD + execution plan task + previous comments + failure context + requirement memory context + workspace diff summary.
- [ ] Write `DEV_RESULT` comments on success and failure.
- [ ] Include changed files, run state, failure reason, and remediation hints in comment metadata.
- [ ] Do not submit long-term memory jobs for coding tasks created from this PRD agent loop.
- [ ] Mark dependent tasks `BLOCKED` when upstream tasks fail or fail validation.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*CodingTaskExecutionServiceTest'
rg "assignedAgent|DEV_RESULT|frontend-dev-agent|backend-dev-agent|VALIDATION_FAILED|BLOCKED|MEMORY_UPDATE_AFTER_CODING_TASK" backend/sei-online-code-service/src/main/java
```

### Task 6: Validation Loop

- [ ] Add validation command execution service using `Run.runType = VALIDATION_COMMAND`.
- [ ] Resolve validation commands from execution plan, project config, then built-in defaults.
- [ ] Trigger task-level validation after each successful development task.
- [ ] Trigger plan-level validation after all runnable tasks and task-level validations settle.
- [ ] Call `test-agent` with validation command facts and write `VALIDATION_RESULT`.
- [ ] Set task status to `SUCCEEDED` only after task-level validation passes.
- [ ] Set task status to `VALIDATION_FAILED` when task-level validation fails.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*Validation*'
rg "VALIDATION_COMMAND|TEST_REVIEW|VALIDATION_RESULT|test-agent|validationConfig" backend/sei-online-code-service/src/main/java backend/sei-online-code-api/src/main/java
```

### Task 7: PM Acceptance Loop

- [ ] Detect when all coding tasks and validations in the current plan version are terminal.
- [ ] Call `pm-agent` for acceptance review.
- [ ] PM output must be structured as `accepted: boolean`, `summary`, `findings[]`, `remediationTasks[]`.
- [ ] PM acceptance prompt must use plan-level `VALIDATION_RESULT` as primary input.
- [ ] If accepted, mark execution plan `ACCEPTED` and move requirement to `DELIVERING`.
- [ ] If not accepted, write `REMEDIATION` comment, create a new `ExecutionPlan` version, and keep the same loop unless a human interrupt occurred.
- [ ] Enforce max 3 remediation rounds, then write explanation and set `WAITING_HUMAN`.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*RequirementAutomationServiceTest'
rg "ACCEPTANCE|REMEDIATION|NEEDS_REMEDIATION|WAITING_HUMAN|DELIVERING|max" backend/sei-online-code-service/src/main/java
```

### Task 8: Human Comment Interrupt and Change Request

- [ ] Add `POST /requirement/{id}/comments`.
- [ ] If `authorType = HUMAN` and automation is active, invoke interrupt flow.
- [ ] Treat every active-flow human comment as a change signal; do not classify comment intent.
- [ ] Mark active runs as cancel requested and/or cancelled.
- [ ] Rotate `activeLoopId` before starting PM re-plan.
- [ ] Ensure old futures cannot update current entities because loop id no longer matches.
- [ ] Rebuild requirement context snapshot from existing memory/context services after human comments.
- [ ] If requirement is already `COMPLETED`, start a `CHANGE_REQUEST` loop instead of interrupting the completed loop.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*RequirementCommentServiceTest'
rg "cancelRequested|invalidatedByCommentId|activeLoopId|INTERRUPTED|CHANGE_REQUEST|CONTEXT_SUMMARY_FAILED" backend/sei-online-code-service/src/main/java
```

### Task 9: Runner Cancellation

- [ ] Extend `CliRunner` with optional cancellation API or introduce `ManagedCliRun`.
- [ ] Track process handles by `runId`.
- [ ] Implement best-effort process kill for Claude and Codex runners.
- [ ] Keep logical cancellation as the source of truth even if process kill fails.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*Runner*'
rg "cancel" backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent
```

### Task 10: GitLab Delivery

- [ ] Add delivery service for commit, push, and GitLab MR create/update.
- [ ] Add GitLab platform config fields for API base URL and token, reusing existing config patterns where possible.
- [ ] On PM acceptance, run delivery with `Run.runType = DELIVERY`.
- [ ] Source branch format: `feature/req-{requirementId-short}-{loopId-short}`.
- [ ] Reuse opened MR for the source branch; create a new MR only when none exists.
- [ ] On closed/merged MR for the branch, fail first version delivery and write `MR_FAILED`.
- [ ] Add `POST /requirement/{id}/mr/retry`.
- [ ] On MR success, write `MR_CREATED` or `MR_UPDATED`, set requirement `COMPLETED`, then submit memory update job.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*GitLab*' --tests '*Delivery*'
rg "MR_CREATED|MR_UPDATED|MR_FAILED|DELIVERY|mr/retry|GitLab" backend/sei-online-code-service/src/main/java backend/sei-online-code-api/src/main/java
```

### Task 11: Requirement Delivery Memory Update

- [ ] Add `MEMORY_UPDATE_AFTER_REQUIREMENT_DELIVERY`.
- [ ] Submit it only after GitLab MR creation/update succeeds.
- [ ] Include requirement id, loop id, execution plan id, MR URL, branch, commit hash, changed files, final validation result, and acceptance comment id.
- [ ] Do not mark requirement incomplete if memory update fails.
- [ ] Write `MEMORY_UPDATED` or `MEMORY_UPDATE_FAILED` comments.
- [ ] Ensure task-level memory update is skipped for tasks created from execution plans.

Verification:

```bash
./gradlew :sei-online-code-service:test --tests '*MemoryJob*'
rg "MEMORY_UPDATE_AFTER_REQUIREMENT_DELIVERY|MEMORY_UPDATED|MEMORY_UPDATE_FAILED" backend/sei-online-code-service/src/main/java backend/sei-online-code-api/src/main/java
```

---

## Frontend Implementation Tasks

### Task 12: PRD Comment Surface

- [ ] Add a comment panel under the PRD view.
- [ ] Render human, PM, frontend agent, backend agent, test agent, delivery, memory, and system comments distinctly.
- [ ] Add comment composer for human feedback.
- [ ] Show interruption/re-planning state immediately after a human comment.
- [ ] Warn that human comments interrupt active automation and trigger PM re-planning.
- [ ] After completion, explain that a human comment creates a CHANGE_REQUEST loop against the delivered MR.

Verification:

```bash
pnpm -C frontend build
rg "RequirementComment|commentType|HUMAN_FEEDBACK|EXECUTION_PLAN|VALIDATION_RESULT|MR_CREATED|CHANGE_REQUEST" frontend/src
```

### Task 13: Execution Plan, Task, Run, and Delivery View

- [ ] Show latest PM execution plan below PRD comments.
- [ ] Show per-task assigned agent, status, run history, and latest result comment.
- [ ] Show task-level and plan-level validation reports.
- [ ] Show Run history grouped/filterable by `runType`.
- [ ] Show MR link, branch, commit hash, and MR retry action when delivery is waiting for human configuration.
- [ ] Keep actions minimal: human comment, rerun failed task, stop automation, retry MR.

Verification:

```bash
pnpm -C frontend build
rg "ExecutionPlan|assignedAgent|frontend-dev-agent|backend-dev-agent|runType|MR_UPDATED|mr/retry" frontend/src/pages/OnlineCode frontend/src/services
```

---

## Migration Strategy

This plan directly replaces the user-visible overview/detailed-design chain.

- `RequirementService.confirmPrd` must start the PM agent loop instead of creating overview design.
- New coding tasks are created from `ExecutionPlan`, not `DetailedDesign`.
- Existing overview/detailed-design classes and tables may remain temporarily for compatibility or data visibility, but new orchestration must not call them.
- `CodingTask.detailedDesignId` remains nullable for compatibility.
- Old cleanup should follow the existing old-flow cleanup plan after this replacement is verified.

---

## Acceptance Criteria

- A PRD can be generated and confirmed.
- Confirmed PRD triggers PM execution plan generation without overview/detailed design gates.
- PM plan creates frontend/backend coding tasks with distinct assigned agents.
- Development scheduling respects dependencies, fileScope conflicts, and frontend/backend lane limits.
- Dev agent results are appended as PRD comments.
- System runs task-level and plan-level validation, and test-agent writes validation reports.
- PM acceptance primarily uses plan-level validation and appends acceptance or remediation comments.
- Failed acceptance creates remediation tasks and loops until accepted or max retry is reached.
- Human comment during active automation interrupts current work and causes PM re-planning.
- Human comment after completed MR starts a CHANGE_REQUEST loop.
- Stale agent completions from interrupted loops cannot overwrite current state.
- PM acceptance success triggers GitLab commit/push/MR delivery.
- Requirement reaches COMPLETED only after MR is created or updated and MR link is written as a comment.
- MR retry works after GitLab configuration is fixed.
- Requirement-level memory update is submitted after MR success; memory update failure does not revert COMPLETED.
- Backend tests cover normal flow, remediation, human interrupt, stale result discard, and assigned-agent execution.
- Frontend exposes PRD comments, execution plan, task status, validation reports, Run history by type, MR link/retry, memory update comments, and interruption/change-request state.
