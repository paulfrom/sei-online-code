# Requirement-Driven Flow Review Remediation

**Source Plan:** `docs/superpowers/plans/2026-07-07-requirement-driven-flow-refactor.md`

**Purpose:** Record the code review findings for the requirement-driven flow implementation and convert them into an executable remediation checklist.

---

## Review Summary

The requirement-driven flow has been implemented in broad structure, but several contract and behavior gaps remain in backend execution, project metadata initialization, and frontend requirement/task interaction. The highest-risk issues are in the `Run` persistence model and the `CodingTask` run/rerun state machine because they can break the execution chain or bypass prompt requirements defined in the contract.

---

## Findings

### 1. `Run` new-flow persistence conflicts with legacy non-null fields

- **Severity:** High
- **Area:** Backend / execution persistence
- **Files:**
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/entity/Run.java`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/CodingTaskExecutionService.java`
  - `backend/sei-online-code-service/src/main/resources/db/migration/V17__project_metadata_and_requirement_flow.sql`

**Problem**

The new coding-task execution flow writes `codingTaskId`, `runNo`, `triggerSource`, `userPrompt`, and related fields, but the `Run` entity still keeps legacy `taskId` and `iterationId` as `nullable = false`. The new execution path does not populate those fields. The migration adds new columns but does not relax the old nullability.

**Impact**

- `Run` creation may fail when coding execution starts.
- Entity constraints and database schema can diverge.
- The most important runtime path in the new flow becomes unreliable.

**Remediation**

- Make legacy `taskId` and `iterationId` optional for the requirement-driven flow.
- Add a migration to drop `NOT NULL` on the corresponding database columns if still enforced.
- Update any display or logging logic that assumes `taskId` always exists.
- Clearly separate legacy-flow fields from new-flow fields in code comments and service usage.

**Verification**

```bash
./gradlew :sei-online-code-service:test --tests '*CodingTaskExecutionServiceTest'
./gradlew :sei-online-code-service:test --tests '*RunServiceTest'
```

### 2. Default `workspacePath` generation uses `entity.getId()` too early

- **Severity:** High
- **Area:** Backend / project creation
- **Files:**
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/ProjectService.java`
  - `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/service/ProjectServiceTest.java`

**Problem**

When `workspacePath` is omitted, `ProjectService.save` constructs the default path before the new project has a persisted id. This can produce a path ending with `null`.

**Impact**

- New projects may get an invalid workspace path.
- Downstream workspace resolution and coding execution can target the wrong directory.

**Remediation**

- Generate the project id before composing the default path, or persist first and backfill the path in the same transaction.
- Convert the current disabled unit tests into executable coverage for:
  - omitted `workspacePath`
  - provided `workspacePath`
  - default `autoRunCodingTask`

**Verification**

```bash
./gradlew :sei-online-code-service:test --tests '*ProjectServiceTest'
./gradlew :sei-online-code-service:compileJava
```

### 3. `/run` can bypass the `rerunPrompt` requirement

- **Severity:** High
- **Area:** Backend / coding task state machine
- **Files:**
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/CodingTaskService.java`
  - `backend/sei-online-code-api/src/main/java/com/changhong/onlinecode/dto/request/RerunCodingTaskRequest.java`

**Problem**

The contract says:

- first run may use an empty prompt
- rerun must provide non-blank `rerunPrompt`

But `CodingTaskService.run` currently allows execution for statuses beyond `PENDING`, so callers can use `/run` on a previously finished task and bypass the rerun prompt requirement entirely.

**Impact**

- The API contract is not enforced.
- Client behavior can diverge from intended UX and review flow.
- Failure-context-driven reruns become optional in practice.

**Remediation**

- Restrict `/run` to `PENDING` only.
- Restrict `/rerun` to `FAILED | SUCCEEDED | CANCELLED`.
- Reject `RUNNING | STALE` in both paths.
- Add tests that validate both state checks and prompt checks.

**Verification**

```bash
./gradlew :sei-online-code-service:test --tests '*CodingTaskServiceTest'
rg "rerunPrompt|PENDING|SUCCEEDED|FAILED|CANCELLED|STALE" backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/CodingTaskService.java
```

### 4. Requirement detail page is missing required overview, detailed-design, and rerun actions

- **Severity:** Medium
- **Area:** Frontend / requirement detail
- **Files:**
  - `frontend/src/pages/OnlineCode/RequirementDetail.jsx`
  - `frontend/src/services/overviewDesign.js`
  - `frontend/src/services/detailedDesign.js`
  - `frontend/src/services/codingTask.js`

**Problem**

The page does not fully implement the documented flow:

- overview design lacks edit/regenerate actions
- the single detailed-design confirm button is rendered but not wired
- coding tasks support first run only, not rerun
- task failure information is not presented as required

**Impact**

- The page suggests the flow is complete while key operations are unavailable.
- Users cannot complete the full review and execution loop from the requirement detail page.

**Remediation**

- Add overview edit/regenerate UI and connect existing service methods.
- Wire single detailed-design confirm to the corresponding API.
- Add coding-task rerun entry with required prompt input.
- Surface task failure summary/detail and run failure reason in the page.

**Verification**

```bash
pnpm -C frontend build
rg "confirmDetailedDesign|regenerateOverviewDesign|editOverviewDesign|rerunCodingTask|failureReason" frontend/src/pages/OnlineCode frontend/src/services
```

### 5. Project-level coding-task run history is not actually usable

- **Severity:** Medium
- **Area:** Frontend / project coding tasks
- **Files:**
  - `frontend/src/pages/OnlineCode/CodingTaskTab.jsx`

**Problem**

The project coding-task tab exposes a "run history" action, but it only logs the result to the browser console and shows a count message.

**Impact**

- Run history exists nominally but is not inspectable by users.
- Failure analysis and execution traceability remain incomplete at the project level.

**Remediation**

- Replace console logging with a drawer, modal, or route-based history view.
- Show `runNo`, `state`, `triggerSource`, `failureReason`, `startedDate`, and `finishedDate`.
- Optionally provide a jump to the owning requirement detail page.

**Verification**

```bash
pnpm -C frontend build
rg "console.log\\('runs'|failureReason|runNo|triggerSource" frontend/src/pages/OnlineCode/CodingTaskTab.jsx
```

---

## Execution Order

1. Fix `Run` entity and migration compatibility first.
2. Fix `workspacePath` generation timing.
3. Tighten `CodingTask` run/rerun state transitions and add backend tests.
4. Complete the requirement detail page interactions.
5. Complete the project-level run history UI.

---

## Remediation Checklist

- [ ] Relax legacy `Run.taskId` / `Run.iterationId` requirements for the new flow and align DB migration.
- [ ] Add execution-path tests covering coding-task run creation and run history persistence.
- [ ] Fix default `workspacePath` generation for new projects.
- [ ] Enable or replace disabled `ProjectServiceTest` coverage for workspace initialization.
- [ ] Restrict `/run` to first-run semantics and `/rerun` to rerun semantics.
- [ ] Add `CodingTaskService` tests for prompt and state validation.
- [ ] Implement overview edit/regenerate actions in requirement detail.
- [ ] Wire single detailed-design confirm in requirement detail.
- [ ] Implement rerun and failure display in requirement detail coding-task step.
- [ ] Replace project coding-task run-history console output with real UI.

---

## Validation Notes

- `backend/gradlew :sei-online-code-service:compileJava` completed successfully during review.
- `backend/gradlew :sei-online-code-service:test --tests '*ProjectServiceTest'` completed successfully during review.
- `backend/gradlew :sei-online-code-service:test --tests '*RequirementServiceAfterCommitTest'` failed in review due to Gradle test-result file write conflict while tests were being executed in parallel, not due to an observed assertion failure in the reviewed code path.
