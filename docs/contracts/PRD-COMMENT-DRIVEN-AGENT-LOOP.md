# PRD Comment Driven Agent Loop Contract

## Scope

This contract replaces the user-visible overview/detailed-design confirmation chain after PRD confirmation.

`Requirement(PRD) -> PM ExecutionPlan -> frontend/backend CodingTask -> DEV_RESULT comments -> validation -> PM acceptance -> GitLab MR delivery`

Legacy overview/detailed-design APIs, agents, code, and tables are removed. Historical Flyway migrations remain immutable; the replacement migration drops their runtime schema.

## Agents

Built-in agents:

- `prd-agent`
- `pm-agent`
- `frontend-dev-agent`, bound to `builtin:suid`
- `backend-dev-agent`, bound to `builtin:eadp-backend`
- `test-agent`

`dev-agent` may remain only for unrelated legacy task compatibility; the PRD loop never resolves it.

## Requirement

New automation status:

- `IDLE`
- `PLANNING`
- `DEVELOPING`
- `VALIDATING`
- `ACCEPTING`
- `DELIVERING`
- `INTERRUPTED`
- `WAITING_HUMAN`
- `COMPLETED`
- `FAILED`

Trace fields:

- `activeLoopId`
- `acceptedAt`
- `acceptedByAgent`
- `deliveryBranch`
- `deliveryCommitHash`
- `deliveryMrUrl`
- `deliveryTargetBranch`

## RequirementComment

Entity/table: `oc_requirement_comment`.

Author types:

- `HUMAN`
- `PM_AGENT`
- `FRONTEND_AGENT`
- `BACKEND_AGENT`
- `TEST_AGENT`
- `SYSTEM`

Comment types:

- `HUMAN_FEEDBACK`
- `EXECUTION_PLAN`
- `DEV_RESULT`
- `VALIDATION_RESULT`
- `ACCEPTANCE`
- `REMEDIATION`
- `INTERRUPTION`
- `FAILURE`
- `MR_CREATED`
- `MR_UPDATED`
- `MR_FAILED`
- `MEMORY_UPDATED`
- `MEMORY_UPDATE_FAILED`
- `CONTEXT_SUMMARY_FAILED`

Human comments are append-only and never overwritten. Agent/system comments are append-only.

API:

- `GET /requirementComment/requirement/{requirementId}`
- `POST /requirement/{id}/comments`

Human comments during active automation (`PLANNING | DEVELOPING | VALIDATING | ACCEPTING`) interrupt the current loop, request cancellation for active runs, rotate `activeLoopId`, and start a `CHANGE_REQUEST` loop.

Human comments after `COMPLETED` start a `CHANGE_REQUEST` loop against the delivered branch/MR context.

## ExecutionPlan

Entity/table: `oc_execution_plan`.

Plan types:

- `INITIAL`
- `REMEDIATION`
- `CHANGE_REQUEST`

Plan statuses:

- `PLANNING`
- `READY`
- `DEVELOPING`
- `ACCEPTING`
- `NEEDS_REMEDIATION`
- `ACCEPTED`
- `INTERRUPTED`
- `FAILED`

API:

- `GET /executionPlan/requirement/{requirementId}`
- `GET /executionPlan/requirement/{requirementId}/latest`

`planJson` stores:

- `goal`
- `tasks[]`
- `risks[]`
- `validation`

Task fields include `taskKey`, `title`, `description`, `agent`, `area`, `dependsOn`, `fileScope`, and `acceptanceCriteria`.

## CodingTask

New PRD-loop tasks are created from `ExecutionPlan`, not `DetailedDesign`.

Trace fields:

- `executionPlanId`
- `planTaskKey`
- `assignedAgent`
- `loopId`
- `area`
- `dependsOn`
- `fileScope`

Terminal statuses include `SUCCEEDED`, `FAILED`, `VALIDATION_FAILED`, `CANCELLED`, `STALE`, and `BLOCKED`.

`SUCCEEDED` means development completed and task-level validation passed.

## Run

Run types:

- `DEVELOPMENT`
- `VALIDATION_COMMAND`
- `TEST_REVIEW`
- `PM_PLANNING`
- `PM_ACCEPTANCE`
- `DELIVERY`

Trace/cancellation fields:

- `requirementId`
- `loopId`
- `cancelRequested`
- `invalidatedByCommentId`
- `memoryContextId`
- `workspaceMemoryId`

Logical cancellation is the source of truth. Process-level kill may be best-effort.

## Compensation

The loop compensation scheduler runs every 60 seconds by default. It is a state repairer, not a
second orchestrator: recovery keeps the current `activeLoopId`, reuses the persisted
`ExecutionPlan`, and delegates progression to the normal automation, task scheduler, validation,
acceptance, and delivery services.

Default thresholds:

- active loop stage stale timeout: 30 minutes
- `Run.state = RUNNING` timeout: 30 minutes
- retry policy: at most 3 retries, using the existing 1-minute/10-minute backoff fields

Recovery order and actions:

1. Preserve the pre-loop recovery boundary: retry eligible `Requirement.status = FAILED` PRD
   generations and stale `PRD_GENERATING` records without changing their Requirement id.
2. Close timed-out `RUNNING` runs as `FAILED`; a linked `RUNNING | VALIDATING` CodingTask becomes
   `FAILED` with structured failure/retry information.
3. `PLANNING | FAILED`: when no Run is active, resume PM planning for the same loop. A missing plan
   is generated as `INITIAL` only when no earlier plan exists; otherwise it is a `CHANGE_REQUEST`.
4. `DEVELOPING`: retry eligible `FAILED | VALIDATION_FAILED` tasks by conditionally claiming them
   as `PENDING`; recreate missing tasks idempotently from `ExecutionPlan.planJson`; request the
   normal DAG scheduler.
5. stale `VALIDATING | ACCEPTING`: re-enter the normal plan-settled validation/PM acceptance
   boundary when no Run is active.
6. stale `DELIVERING`: invoke the normal idempotent delivery retry when no delivery Run is active.

Compensation must not:

- create or rotate a loop id
- automatically resume `INTERRUPTED`
- automatically advance `WAITING_HUMAN`
- modify `COMPLETED`
- overwrite human comments or bypass PM remediation/acceptance limits

Every claimed retry, timeout closure, and recovered stage writes `oc_compensation_log` with
`SCHEDULED_COMPENSATION` as its trigger source. Conditional task/Run state updates are the
concurrency guard; a failed claim means another worker already advanced the node.

## Validation

Task-level validation runs after successful development. Default commands:

- frontend: `pnpm -C frontend build`
- backend: `./gradlew :sei-online-code-service:compileJava`

Validation facts are written as `VALIDATION_RESULT` comments. Plan-level validation runs when plan tasks are terminal and drives PM acceptance.

## Delivery

PM acceptance success moves Requirement to `DELIVERING`.

Delivery must:

- commit after acceptance
- push source branch `feature/req-{requirementId-short}-{loopId-short}`
- create or update GitLab MR
- write `MR_CREATED` or `MR_UPDATED`
- set Requirement `COMPLETED` only after MR create/update succeeds
- write `MR_FAILED` and set `WAITING_HUMAN` on failure

Retry API:

- `POST /requirement/{id}/mr/retry`

## Memory

Long-term memory update is submitted only after GitLab MR create/update succeeds.

Job type:

- `MEMORY_UPDATE_AFTER_REQUIREMENT_DELIVERY`

Task-level `MEMORY_UPDATE_AFTER_CODING_TASK` is skipped for tasks created from execution plans.

Memory update failure does not revert `COMPLETED`; it writes `MEMORY_UPDATE_FAILED`.
