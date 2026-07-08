# Phase 4 API Contract Extension — Full Build Loop

> Extends Phase 1/2/3 contracts. Same EADP envelope rules (`ResultData<T>` /
> `PageResult<T>` / `Search`) apply verbatim — see Phase 1 §1. Phase 4 closes the
> loop: **PREVIEW feedback → Requirement Agent incremental Spec update →
> re-confirm → re-dispatch**, makes the **Iteration** a first-class timeline
> record (Spec version + deployment + run history), and adds **FAILED/CANCELLED
> handling + retry** and **workspace GC**.
> Refs (multica, blueprint only): `daemon/gc.go`, `daemon/diskusage.go`.

## 0. Scope

| In scope (Phase 4) | Out of scope (Phase 5) |
|---|---|
| Feedback re-entry: `PREVIEW` → `SPEC_REFINING` via incremental Spec update | Template GitLab repo productization |
| Spec versioning: new Spec version per loop round (immutable prior versions) | Scaffold generator productization |
| Iteration timeline: list a project's iterations with their spec version + runs | Multi-CLI, multi-tenancy/auth |
| Acceptance: `PREVIEW` → `ACCEPTED` (terminates the round) | Dedicated merge agent |
| FAILED/CANCELLED transitions + retry (re-dispatch a failed iteration) | Real git spawn / live deploy host (compile-only carried) |
| Workspace GC: TTL-based reclaim of terminal-iteration workspaces | — |

## 1. Domain payload changes

### 1.1 `IterationDto` — now carries loop-round provenance

```jsonc
{
  "id": "ITER0002",
  "projectId": "PRJ0001",
  "specId": "SPEC0002",
  "specVersion": 2,               // which Spec version this round built
  "round": 2,                     // NEW — 1-based loop-round ordinal within the project
  "state": "PREVIEW",             // LifecycleState (project-level mirror on the active iteration)
  "previewUrl": "http://localhost:34002/",  // set at DEPLOYING→PREVIEW
  "parentIterationId": "ITER0001",// NEW — the iteration this round refined from (null for round 1)
  "feedback": "把库存列表加上导出按钮",   // NEW — user optimization prose that seeded this round (null for round 1)
  "createdDate": "2026-07-01T11:00:00",
  "finishedDate": null            // NEW — set on ACCEPTED/FAILED/CANCELLED
}
```

- **Spec versioning**: each loop round produces a NEW `Spec` row with
  `version = prior + 1`; prior versions are immutable (diffable history). The
  active iteration points at its own `specId`/`specVersion`.
- `round` + `parentIterationId` give the timeline its chain; `feedback` records
  what the user asked to change entering this round.

## 2. New / changed endpoints — all under `/api`

| # | Method | Path | Request | Response `data` | Purpose |
|---|--------|------|---------|-----------------|---------|
| 25 | POST | `/api/iteration/accept` | `{ iterationId }` | `IterationDto` | User sign-off: `PREVIEW` → `ACCEPTED` (sets `finishedDate`) |
| 26 | POST | `/api/project/optimize` | `{ projectId, feedback }` | `IterationDto` | Feedback re-entry: from `PREVIEW`, Requirement Agent incrementally updates Spec → new version, opens a new iteration (`round+1`), state → `SPEC_REVIEW` |
| 27 | POST | `/api/iteration/findByPage` | `Search` | `PageResult<IterationDto>` | Timeline: list a project's iterations (filter by `projectId`, order by `round`) |
| 28 | POST | `/api/iteration/cancel` | `{ iterationId }` | `IterationDto` | Abort active iteration → `CANCELLED` (cascade: RUNNING tasks/runs → `CANCELLED`) |
| 29 | POST | `/api/iteration/retry` | `{ iterationId }` | `IterationDto` | From `FAILED`: re-dispatch the same Spec version (new tasks/runs, state → `DISPATCHING`) |
| 30 | GET  | `/api/spec/findByProject?projectId=` | — | `SpecDto[]` | Spec version history for a project (ordered by `version`) |

> Phase 1 `/api/spec/confirm` (#6) unchanged; re-confirm after `optimize` reuses
> it. Phase 2 dispatch/merge (#10/#15) and deploy (#7) unchanged on the wire.

## 3. Lifecycle state machine (Phase 4 — loop closed)

```
DRAFTING → SPEC_REFINING → SPEC_REVIEW → DISPATCHING → DEVELOPING → MERGING
         → DEPLOYING → PREVIEW ─┬─ accept  → ACCEPTED        (terminal)
                                └─ optimize → SPEC_REFINING   (round+1, new Spec version)
any active state ─ cancel → CANCELLED (terminal)
any active state ─ error  → FAILED    ─ retry → DISPATCHING
```

- **Only `PREVIEW`** accepts `accept`/`optimize`. `optimize` requires non-empty
  `feedback`; it creates the next Spec version + next iteration, NOT edits the
  prior. **Spec is the single source of truth** — feedback never bypasses it.
- `cancel`/`retry` guarded: `cancel` only from a non-terminal state; `retry`
  only from `FAILED`. Illegal transitions → `OperateResult.operationFailure`.

## 4. Workspace GC (backend, ref `daemon/gc.go` + `diskusage.go`)

- A scheduled sweep reclaims workspace dirs of iterations in a **terminal state**
  (`ACCEPTED`/`FAILED`/`CANCELLED`) older than a configurable TTL.
- Config from environment with fallback (backend rule #11):
  `oc.gc.ttl-hours` (default 72), `oc.gc.enabled` (default true).
  `// TODO(oma-deferred)`: real fs delete + disk-usage accounting behind the
  Phase-2 runtime seam; this phase wires the TTL selection + a
  unit-testable "which iterations are reclaimable" predicate.

## 5. Sub-agent obligations (dispatch basis — separate contexts)

- **Backend (`eadp-backend`)**: extend `Iteration` (+`round`,
  `parentIterationId`, `feedback`, `finishedDate`) + SQL migration script `V4__build_loop.sql`
  (alter table + indexes); DTO fields; `IterationApi`/`SpecApi` eps #25–30;
  `BuildLoopService` — accept/optimize(new Spec version + iteration)/cancel
  (cascade)/retry; Spec version history (`findByProject`); `WorkspaceGcService`
  (TTL predicate, unit-tested, real delete deferred). Compile-only bar +
  unit test for the GC-reclaimable predicate + optimize versioning.
- **Frontend (`suid`)**: MSW handlers eps #25–30; an **Iteration timeline** view
  on the project (list rounds with spec version, state, previewUrl, feedback);
  PREVIEW actions: **Accept** button (#25) + **Optimize** form (#26, feedback
  prose → re-confirm flow); **Cancel**/**Retry** buttons gated by state; Spec
  version history viewer (#30).

> Neither sub-agent may diverge from §1–§4 without updating THIS file first.
> Both build ONLY against this frozen contract, in separate contexts (project
> CLAUDE.md rule: front/back never in one context).
