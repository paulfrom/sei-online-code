# Phase 2 API Contract Extension — Concurrency (ADR-0001)

> Extends `docs/contracts/API-CONTRACT.md` (Phase 1). Same EADP envelope rules
> (`ResultData<T>` / `PageResult<T>` / `Search`) apply verbatim — see Phase 1 §1.
> Phase 2 adds **task decomposition + parallel worktree execution + multi-agent
> run-log streaming**. Concurrency = parallel dev agents in isolated git
> worktrees, merged back (ADR-0001). This file is the frozen dispatch basis for
> the two Phase 2 sub-agents (backend `eadp-backend`, frontend `suid`).

## 0. Scope

| In scope (Phase 2) | Out of scope (Phase 3+) |
|---|---|
| Dispatch Agent: confirmed Spec → non-overlapping `Task[]` | Skills materialization / skills-lock (Phase 3) |
| `Task` / `Run` entities + tables | Custom user-defined agents (Phase 3) |
| WorktreeManager: per-task worktree, parallel ClaudeRunner, merge-back | Full feedback re-entry / iteration timeline (Phase 4) |
| Multi-agent live log streaming (per-run WS frames) | Template GitLab repo productization (Phase 5) |
| Lifecycle expansion: DISPATCHING → DEVELOPING → MERGING (no longer collapsed) | — |

## 1. New domain payloads

Ids are String-UUID (`IdGenerator.nextIdStr()`). Audit fields on read responses.

### 1.1 `TaskDto` — one non-overlapping unit of work cut by Dispatch Agent

```jsonc
{
  "id": "TASK0001",
  "iterationId": "ITER0001",
  "title": "库存列表页",
  "description": "实现 /stock/list 页面 + 对应 mock",       // agent prompt seed
  "fileScope": ["src/pages/stock/list.tsx",                  // file-boundary claim
                "src/mocks/stock.ts"],                        // (non-overlapping across tasks)
  "assignedAgent": "dev-agent",                              // Phase 2: single built-in dev agent
  "state": "PENDING",   // PENDING | RUNNING | MERGING | MERGED | FAILED | CANCELLED
  "worktreeBranch": "task/ITER0001-0001",                    // null until RUNNING
  "seq": 1,             // int — dispatch order / merge order
  "createdDate": "2026-07-01T10:12:00"
}
```

- `fileScope` is the **conflict-avoidance contract**: Dispatch Agent MUST cut
  tasks so scopes are disjoint (per-page route files, per-feature mock files,
  glob-aggregated entries). On merge conflict → serial re-resolution fallback
  (ADR-0001 A-primary + B-fallback). No standalone merge agent.

### 1.2 `RunDto` — one ClaudeRunner execution of a Task in its worktree

```jsonc
{
  "id": "RUN0001",
  "taskId": "TASK0001",
  "iterationId": "ITER0001",
  "state": "RUNNING",   // RUNNING | SUCCEEDED | FAILED | CANCELLED
  "worktreePath": "/tmp/rapid-app-dev/PRJ0001/wt-TASK0001",  // absolute, server-side
  "exitCode": null,     // int | null — set on terminal
  "startedDate": "2026-07-01T10:12:05",
  "finishedDate": null  // null until terminal
}
```

## 2. New endpoints (Phase 2) — all under `/api`

| # | Method | Path | Request | Response `data` | Purpose |
|---|--------|------|---------|-----------------|---------|
| 10 | POST | `/api/iteration/dispatch` | `{ iterationId }` | `TaskDto[]` | Dispatch Agent: confirmed Spec → tasks (state → `DISPATCHING`→`DEVELOPING`) |
| 11 | POST | `/api/task/findByPage` | `Search` | `PageResult<TaskDto>` | List tasks (filter by `iterationId`) |
| 12 | GET  | `/api/task/findOne?id=` | — | `TaskDto` | Load one task |
| 13 | POST | `/api/run/findByPage` | `Search` | `PageResult<RunDto>` | List runs (filter by `iterationId` / `taskId`) |
| 14 | GET  | `/api/run/findOne?id=` | — | `RunDto` | Poll one run's state/exitCode |
| 15 | POST | `/api/iteration/merge` | `{ iterationId }` | `IterationDto` | Merge all task worktrees back (state → `MERGING`→`DEPLOYING`) |

> Phase 1 `/api/spec/confirm` (ep #6) still transitions to `DISPATCHING`. In
> Phase 2 the flow becomes: confirm → **dispatch** (cut tasks, spawn parallel
> runs) → **merge** → deploy (ep #7, unchanged). The Phase-1 collapse of
> DISPATCHING/DEVELOPING/MERGING into one serial step is REPLACED by these
> explicit steps. Backward compat: a project may still be driven serially, but
> the states are now distinctly transmitted.

## 3. WebSocket — multi-agent run-log streaming (extends Phase 1 §3.1)

Same hub (`daemonws/hub.go` ref), same URL family. Phase 2 frames carry
`taskId` + `runId` so the UI can fan out per-agent log panels.

- URL: `ws://<host>/ws/run/{iterationId}` (unchanged — one socket per iteration,
  multiplexed by `runId`).
- Frame shape (extends Phase 1):
  ```jsonc
  {
    "iterationId": "ITER0001",
    "taskId": "TASK0001",      // NEW — which task this line belongs to
    "runId": "RUN0001",        // NEW — which run
    "stream": "stdout",        // stdout | stderr | system
    "line": "vite building…",
    "ts": "2026-07-01T10:12:07"
  }
  ```
- Per-run terminal frame: `{ "stream": "system", "runId": "RUN0001", "line": "DONE", "state": "SUCCEEDED" }`.
- Iteration terminal frame (all merged): `{ "stream": "system", "line": "MERGED", "state": "DEPLOYING" }`.

## 4. Lifecycle state machine (Phase 2 — expanded)

Project-level (unchanged tokens, now traversed distinctly):
```
… SPEC_REVIEW → DISPATCHING → DEVELOPING → MERGING → DEPLOYING → PREVIEW …
```
- `DISPATCHING`: Dispatch Agent cutting tasks (ep #10).
- `DEVELOPING`: parallel runs executing in worktrees.
- `MERGING`: worktrees merging back (ep #15); on conflict → serial re-resolve.
- `DEPLOYING` → `PREVIEW`: unchanged (ep #7).

Task-level (new): `PENDING → RUNNING → MERGING → MERGED`; any → `FAILED`; abort → `CANCELLED`.
Run-level (new): `RUNNING → SUCCEEDED`; any → `FAILED`; abort → `CANCELLED`.

## 5. Sub-agent obligations

- **Backend (`eadp-backend`)**: `Task`/`Run` entities + SQL migration script `V2__task_run.sql`;
  `TaskDto`/`RunDto` + `TaskApi`/`RunApi`; Dispatch Agent service (Spec → disjoint
  tasks by fileScope); `WorktreeManager` (`git worktree add -b` / `remove --force`
  / `branch -D` / fast-forward merge, ref multica `execenv/git.go`); wire
  `ClaudeRunner` per-task parallel exec; extend WS frames with `taskId`/`runId`.
  Compile-only bar still applies unless runtime verification is explicitly asked.
- **Frontend (`suid`)**: MSW handlers for eps #10–15 (same envelopes); a
  per-agent live log panel (tabs/grid keyed by `runId`) on the Preview/Dispatch
  view; task list (`ExtTable`) filtered by `iterationId`; drive the expanded
  lifecycle badges. WS mock may stay polling-fallback (contract-sanctioned).

> Neither sub-agent may diverge from §1–§4 without updating THIS file first.
> Both build ONLY against this frozen contract, in separate contexts (project
> CLAUDE.md rule: front/back never in one context).
