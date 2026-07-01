# Phase 2 Breakdown — Concurrency (ADR-0001)

> Derived from `docs/init/PLAN.md` Phase 2 + `docs/contracts/API-CONTRACT-PHASE2.md`.
> Dispatch basis: front/back built by **separate sub-agents in separate contexts**
> against the frozen Phase 2 contract. Backend → `eadp-backend`; frontend → `suid`.

## Goal (Phase 2 success contract)

- **Input**: a confirmed Spec (Phase 1 output) whose `apiContract[]`/`pages[]`
  imply more than one file-scope.
- **Output**: Dispatch Agent cuts the Spec into ≥2 non-overlapping `Task`s; each
  runs in its own git worktree via a parallel `ClaudeRunner`; results merge back
  (fast-forward primary, serial re-resolve on conflict); the UI shows live
  per-agent log panels keyed by `runId`.
- **Verified by**: compile (backend) / build (frontend) still green; `task`/`run`
  rows exist with correct states; WS/poll frames carry `taskId`/`runId`.

## Locked decisions (carried from Phase 1 + ADR-0001)

| Item | Decision | Impact |
|---|---|---|
| Backend run | **Compile-only this round** (unless runtime asked) | WorktreeManager git calls stub-safe; no live spawn required to compile. |
| Concurrency model | **Parallel worktrees, merged back** | `git worktree add -b` per task; merge fast-forward; conflict → serial re-resolve. |
| Conflict strategy | **A-primary + B-fallback**, no merge agent | Dispatch cuts disjoint `fileScope`; on conflict the responsible task re-runs serially. |
| Dev agent | **Single built-in `dev-agent`** | Custom user agents are Phase 3. |
| WS mock | **Polling fallback allowed** | Frontend need not stand up a real WS server. |

## Track B — Backend (`eadp-backend`)

| Task | Deliverable | Contract ref |
|---|---|---|
| B9 | Entities `Task`, `Run` (+ state enums `TaskState`/`RunState`, String-UUID pk, audit base) | §1 |
| B10 | Flyway `V2__task_run.sql` (2 tables + indexes on iterationId/taskId) | §1 |
| B11 | DTOs `TaskDto`/`RunDto` (Swagger) + Feign `TaskApi`/`RunApi` (eps #11–14) | §1,§2 |
| B12 | `DispatchService`: confirmed Spec → disjoint `Task[]` by fileScope; ep #10 (`/api/iteration/dispatch`) | §2 |
| B13 | `WorktreeManager`: `worktree add -b` / `remove --force` / `branch -D` / ff-merge; ref multica `execenv/git.go` | §5 |
| B14 | Wire parallel `ClaudeRunner` per task (compile-only skeleton; `CompletableFuture` fan-out); ep #15 merge | §4 |
| B15 | Extend WS `RunLogFrame` + hub with `taskId`/`runId` | §3 |

> B13/B14 real git+spawn wiring may stay `TODO(oma-deferred)` skeletons this round
> (backend not run). Keep local-profile seam; do not hardcode Nacos into run path.

## Track F — Frontend (`suid`)

| Task | Deliverable | Contract ref |
|---|---|---|
| F8 | MSW handlers eps #10–15 (dispatch cuts ≥2 mock tasks; runs progress PENDING→RUNNING→SUCCEEDED→MERGED) | §2,§5 |
| F9 | Task list (`ExtTable`) filtered by `iterationId` on `/api/task/findByPage` | ep #11 |
| F10 | Multi-agent live log panel: grid/tabs keyed by `runId`, each tailing WS/poll frames filtered by `runId` | §3 |
| F11 | Dispatch view: trigger `/api/iteration/dispatch`, show tasks + their runs; merge button → `/api/iteration/merge` | ep #10,#15 |
| F12 | Extend `LifecycleBadge` + poll to show DISPATCHING/DEVELOPING/MERGING distinctly | §4 |

## Dependency order

```
Phase 2 contract (frozen) ──┬── Track B (compile-only)   ← independent, parallel
                            └── Track F (MSW = live data) ← independent, parallel
```

Both depend ONLY on the frozen Phase 2 contract, never on each other.

## Out of scope (Phase 3+, do NOT build now)

- `SkillMaterializer` / skills-lock / custom user agents (Phase 3).
- Full Build-Loop feedback re-entry, iteration timeline, workspace GC (Phase 4).
- Template GitLab repo + scaffold generator productization (Phase 5).
- Backend runtime (Nacos/PG wiring, real deploy host, real git spawn).
