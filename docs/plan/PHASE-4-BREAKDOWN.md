# Phase 4 Breakdown — Full Build Loop

> Derived from `docs/init/PLAN.md` Phase 4 + `docs/contracts/API-CONTRACT-PHASE4.md`.
> Dispatch basis: front/back built by **separate sub-agents in separate contexts**
> against the frozen Phase 4 contract. Backend → `eadp-backend`; frontend → `suid`.

## Goal (Phase 4 success contract)

- **Input**: an iteration in `PREVIEW` (Phase 1–3 output).
- **Output**: user can **accept** (→ `ACCEPTED`, terminal) or **optimize**
  (feedback → new Spec version + new iteration `round+1` → `SPEC_REVIEW`);
  the project shows an **iteration timeline** (rounds, spec versions, states,
  feedback); failed iterations **retry**, active ones **cancel** (cascade to
  tasks/runs); a **workspace GC** predicate selects terminal iterations past TTL.
- **Verified by**: compile (backend) / build (frontend) green; unit tests prove
  (a) optimize creates a new Spec version + child iteration with `round+1`,
  (b) the GC predicate selects only terminal iterations older than TTL; UI loop
  round-trips PREVIEW→optimize→SPEC_REVIEW and PREVIEW→accept→ACCEPTED.

## Locked decisions (carried + new)

| Item | Decision | Impact |
|---|---|---|
| Backend run | **Compile-only** (carried) | GC real fs-delete + spawn stay `TODO(oma-deferred)`; predicate/versioning are real+unit-tested. |
| Spec history | **New immutable version per round** | `optimize` never edits prior Spec; `version = prior+1`. |
| Loop chain | **`round` + `parentIterationId`** on Iteration | Timeline is a parent chain; round 1 has null parent. |
| Cancel cascade | **RUNNING tasks/runs → CANCELLED** in same tx | One unit of work (backend rule #9). |
| GC config | **env with fallback** `oc.gc.ttl-hours=72`, `oc.gc.enabled=true` | Backend rule #11; no hardcode. |

## Track B — Backend (`eadp-backend`)

| Task | Deliverable | Contract ref |
|---|---|---|
| B23 | Extend `Iteration` entity/DTO: `round`, `parentIterationId`, `feedback`, `finishedDate` | §1 |
| B24 | Flyway `V4__build_loop.sql`: alter `oc_iteration` (+4 cols) + index on `(project_id, round)` | §1 |
| B25 | `IterationApi`/`SpecApi` eps #25–30 (Swagger) + request DTOs (`Accept/Optimize/Cancel/Retry`) | §2 |
| B26 | `BuildLoopService.accept`: PREVIEW→ACCEPTED, set finishedDate; guard state (#25) | §2,§3 |
| B27 | `BuildLoopService.optimize`: PREVIEW→ new Spec version (via Requirement Agent seam) + new Iteration(round+1, parent, feedback)→SPEC_REVIEW; **unit test versioning** (#26) | §2,§3 |
| B28 | `BuildLoopService.cancel` (cascade RUNNING task/run→CANCELLED) + `retry` (FAILED→DISPATCHING, re-dispatch) (#28,#29) | §2,§3 |
| B29 | `SpecService.findByProject` version history (#30) | §2 |
| B30 | `WorkspaceGcService`: env TTL config + `reclaimable(iteration, now)` predicate (terminal & age>TTL); **unit test**; real fs-delete `TODO(oma-deferred)` | §4 |

## Track F — Frontend (`suid`)

| Task | Deliverable | Contract ref |
|---|---|---|
| F18 | MSW handlers eps #25–30 (accept/optimize creates versioned child iteration; cancel/retry; timeline + spec history) | §2 |
| F19 | Iteration timeline view: `ExtTable`/list of rounds (round, specVersion, state, previewUrl, feedback) filtered by projectId (#27) | ep #27 |
| F20 | PREVIEW actions: **Accept** button (#25) + **Optimize** ExtModal (feedback prose → #26 → re-confirm via #6) | ep #25,#26 |
| F21 | **Cancel**/**Retry** buttons gated by iteration state (#28,#29) | ep #28,#29 |
| F22 | Spec version history viewer (#30) + router/menu/services for eps #25–30 | ep #30,§2 |

## Dependency order

```
Phase 4 contract (frozen) ──┬── Track B (compile-only + versioning/GC unit tests)  ← independent
                            └── Track F (MSW = live data)                            ← independent
```

Both depend ONLY on the frozen Phase 4 contract, never on each other.

## Out of scope (Phase 5, do NOT build now)

- Template GitLab repo + scaffold generator productization.
- Multi-CLI, multi-tenancy/auth, dedicated merge agent.
- Real git spawn / live deploy host / real GC fs-delete.
