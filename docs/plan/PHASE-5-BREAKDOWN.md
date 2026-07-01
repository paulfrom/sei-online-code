# Phase 5 Breakdown — Template Repo + Scaffold Generator + Config Surface

> Derived from `docs/init/PLAN.md` Phase 5 + `docs/contracts/API-CONTRACT-PHASE5.md`.
> Dispatch basis: front/back built by **separate sub-agents in separate contexts**
> against the frozen Phase 5 contract. Backend → `eadp-backend`; frontend → `suid`.
> FINAL initial-version phase.

## Goal (Phase 5 success contract)

- **Input**: a project ready to dispatch; platform config with EITHER a
  `templateGitlabUrl` set OR empty (scaffold path).
- **Output**: platform config is readable/updatable (Workspace Root + Template
  GitLab URL); `WorkspaceManager.resolve` decides clone-vs-scaffold, resolves a
  safe per-project dir, and reuses on re-run (clone-once); `ScaffoldGenerator`
  yields the canonical SUID manifest when no URL; config UI + workspace-source
  indicator operational.
- **Verified by**: compile (backend) / build (frontend) green; unit tests prove
  (a) path-safety predicate rejects system/drive roots, (b) decideSource returns
  CLONE with URL / SCAFFOLD without, (c) scaffold manifest has required
  structural entries; UI loads/saves config and shows the source badge.

## Locked decisions (carried + new)

| Item | Decision | Impact |
|---|---|---|
| Backend run | **Compile-only** (carried) | real git clone / fs writes stay `TODO(oma-deferred)`; resolution/decision/manifest are real+unit-tested. |
| Config store | **Singleton row** (fixed id `CONFIG`) | `save` upserts; `get` creates default if absent. |
| Workspace Root default | **`${java.io.tmpdir}/sei-online-code`** | env override `oc.workspace.root` (rule #11). |
| Template URL default | **none (empty)** | empty → scaffold path is day-one path (CONTEXT). |
| Clone-once | **resolve reuses existing dir** | later loop rounds edit in place, never re-clone. |
| Scaffold shape | **split-file + glob-aggregation** | per-page/per-feature files so parallel tasks don't collide (ADR-0001). |

## Track B — Backend (`eadp-backend`)

| Task | Deliverable | Contract ref |
|---|---|---|
| B31 | `PlatformConfig` entity (singleton, fixed id `CONFIG`) + `PlatformConfigDto` | §1 |
| B32 | Flyway `V5__platform_config.sql` (1 table + default-row seed) | §1 |
| B33 | `ConfigApi`/`WorkspaceApi` (eps #31–33) + `WorkspaceResolveResult` DTO (`path`/`provisioned`/`source`) | §2 |
| B34 | `ConfigService`: get (create default if absent) + save (upsert singleton); env-fallback `workspaceRoot` (`oc.workspace.root`) | §2 |
| B35 | `WorkspaceManager`: `resolve(projectId)` (§3) + `isSafeRoot` blacklist predicate + `decideSource`; **unit tests** (path-safety, source decision); clone/fs deferred | §3 |
| B36 | `ScaffoldGenerator`: canonical SUID `List<ScaffoldFile>` manifest; **unit test** structural invariants (package.json, src/pages/, src/mocks/, MSW entry, glob router) | §4 |
| B37 | Wire `DispatchService`: call `WorkspaceManager.resolve` before fan-out; pass resolved path into the run seam | §3,§5 |

## Track F — Frontend (`suid`)

| Task | Deliverable | Contract ref |
|---|---|---|
| F23 | MSW handlers eps #31–33 (config get/save singleton; workspace resolve → CLONE/SCAFFOLD source) | §2 |
| F24 | Settings/Config page: Form(Workspace Root + Template GitLab URL), load #31 / save #32 | ep #31,#32 |
| F25 | Workspace-source indicator on project/dispatch view: path + CLONE/SCAFFOLD badge (#33) | ep #33 |
| F26 | Router/menu entry (Settings) + service functions for eps #31–33 | §2 |

## Dependency order

```
Phase 5 contract (frozen) ──┬── Track B (compile-only + safety/scaffold unit tests)  ← independent
                            └── Track F (MSW = live data)                              ← independent
```

Both depend ONLY on the frozen Phase 5 contract, never on each other.

## Out of scope (deferred beyond initial version)

- Multi-CLI, multi-tenancy/auth, dev-server live preview, dedicated merge agent,
  importable templates marketplace.
- Real git clone / real scaffold fs emission / live deploy host.
