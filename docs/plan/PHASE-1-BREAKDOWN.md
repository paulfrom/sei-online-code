# Phase 1 Breakdown — Walking Skeleton

> Derived from `docs/init/PLAN.md` Phase 1 + `docs/contracts/API-CONTRACT.md`.
> This file is the **dispatch basis**: per project CLAUDE.md, front and back MUST
> be built by **separate sub-agents in separate contexts** (never both in one),
> against the frozen contract. Backend follows `eadp-backend` skill; frontend
> follows `suid` skill.

## Goal (Phase 1 success contract — from PLAN.md §Success criteria)

- **Input**: a Project Design string (+ generate-scaffold path, no GitLab URL —
  locked decision).
- **Output**: a running iframe preview of a built frontend whose pages/data match
  the confirmed Spec, all data served by MSW.
- **Verified by**: create a project end-to-end in the platform UI → confirm Spec →
  see deployed preview; PG rows for project/spec/iteration exist with correct states.

## Locked decisions affecting Phase 1 (from /loop interview)

| Item | Decision | Impact |
|---|---|---|
| Backend local run | **Code-only this round; not run** | No local Nacos/PG needed now. Backend sub-agent writes EADP layers + Flyway DDL; compile is the bar, runtime deferred. |
| Template source | **Generate-scaffold path** (no GitLab URL) | `WorkspaceManager` day-one path = emit canonical SUID scaffold, not clone. |
| Concurrency | **Serial, single built-in path** | No `WorktreeManager` in Phase 1 (Phase 2). |
| Toolchain gaps | gradlew wrapper missing; pnpm not installed; global gradle 8.7 vs pinned 8.5 | Tracked in §Preconditions — resolve before build verification, not blocking doc/code authoring. |

## Track split (two sub-agents, two contexts)

### Track B — Backend (`eadp-backend` skill)

Layering: `Entity → DAO → DAOImpl → Service → Controller → API(Feign)` + DTO;
JPA-only; String-UUID ids via `IdGenerator.nextIdStr()`; `ResultData` responses;
`OperateResult` + `@Transactional` for writes. All DTOs/APIs carry Swagger annotations.

| Task | Deliverable | Contract ref |
|---|---|---|
| B1 | Domain entities: `Project`, `Spec`, `Iteration` (+ `state` columns, String-UUID pk, audit base) | §2 |
| B2 | Flyway baseline migration (PostgreSQL DDL) for the 3 tables | PLAN P1.1 |
| B3 | DTOs: `ProjectDto` / `SpecDto` / `IterationDto` + `Search` reuse (Swagger-annotated) | §2 |
| B4 | DAO + DAOImpl for each entity (paged `findByPage`) | §1.2/1.3 |
| B5 | Services extending `BaseEntityService`; state-machine transitions as service methods | §4 |
| B6 | Controllers implementing the API interface → endpoints 1–9 returning `ResultData` | §3 |
| B7 | `ClaudeRunner` skeleton (ProcessBuilder spawn `claude`, stream stdout/stderr) — **compile-only stub this round**, ref multica `pkg/agent/claude.go` | PLAN P1.2 |
| B8 | WebSocket hub for `/ws/run/{iterationId}` (server→browser) — ref multica `daemonws/hub.go` | §3.1 |

> B7/B8 are borrowed-machinery skeletons; full spawn/stream wiring is fine to stub
> to compile this round (backend not run). Do NOT hardcode Nacos-dependent beans
> into the run path; keep a local-profile seam for later.

### Track F — Frontend (`suid` skill)

Stack: existing UmiJS + `@ead/suid` + `@ead/antd-style` + `@ead/suid-utils-react`.
Data via `request` / `useStore`; tables via `ExtTable remotePaging`; global state via
`createAppStore`. **No Tailwind/shadcn/raw antd.** Run `suid info <component> --format json`
before using any component.

| Task | Deliverable | Contract ref |
|---|---|---|
| F1 | MSW setup + handlers for endpoints 1–9 returning the `ResultData` / `PageResult` envelope | §1, §5 |
| F2 | WS mock (or polling fallback on `/api/iteration/findOne`) for run-log streaming | §3.1 |
| F3 | Projects list page — `ExtTable remotePaging` on `/api/project/findByPage` | ep #3 |
| F4 | Create-project flow — form (`name`, `design`) → `/api/project/save` | ep #1 |
| F5 | Spec review page — render `SpecDto` structured view; confirm → `/api/spec/confirm` | ep #4–6 |
| F6 | Preview page — iframe to `iteration.previewUrl` + live log panel from WS/poll | ep #7–8 |
| F7 | Lifecycle-state UI (badge + poll `/api/project/state`) driving page transitions | §4 |

## Dependency order

```
Contract (this) ──┬── Track B (compile-only)   ← independent, parallel
                  └── Track F (MSW = live data) ← independent, parallel
```

Both tracks depend ONLY on the frozen contract, not on each other — that's the
point of MSW-first (ADR-0002). They can run as parallel sub-agents. Integration
(disable MSW, point at real backend) is **out of Phase 1 scope**.

## Preconditions to resolve before build verification (not before authoring)

1. **gradlew wrapper missing** — `backend/` has no `gradlew`/`gradle/wrapper/`.
   Generate with global gradle (`gradle wrapper --gradle-version 8.5`) so the
   pinned 8.5 in `build.gradle` is honored. (Global is 8.7.)
2. **pnpm not installed** — enable via `corepack enable pnpm` (corepack present)
   before `pnpm install` in `frontend/`.
3. **`build_log.txt` is stale** — references `ams-api`/`ams-service` from another
   project; ignore, will be regenerated on next real build.
4. **Nacos unreachable** (`10.199.11.1:8848`) — irrelevant this round (backend not
   run); a `local` Spring profile that disables nacos discovery+config is the
   documented later path when runtime verification is needed.

## Out of scope (Phase 2+, do NOT build now)

- `WorktreeManager` / parallel dev agents / merge-back (Phase 2, ADR-0001).
- `SkillMaterializer` / skills-lock / custom agents (Phase 3).
- Full Build-Loop feedback re-entry, iteration timeline, GC (Phase 4).
- Template GitLab repo + scaffold generator productization (Phase 5).
- Backend runtime (Nacos/PG wiring, real deploy host, port registry).
