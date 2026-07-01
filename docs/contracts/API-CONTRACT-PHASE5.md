# Phase 5 API Contract Extension — Template Repo + Scaffold Generator + Config Surface

> Extends Phase 1–4 contracts. Same EADP envelope rules (`ResultData<T>` /
> `PageResult<T>` / `Search`) apply verbatim. Phase 5 adds the **WorkspaceManager**
> (clone-once + reuse under a configurable Workspace Root), the **no-template
> scaffold generator** (canonical SUID stack emitted when no GitLab URL is
> configured), and the platform **config surface** (Workspace Root + Template
> GitLab URL). This is the FINAL initial-version phase.
> Refs (multica, blueprint only): `daemon/local_directory.go`,
> `daemon/repocache/cache.go`.

## 0. Scope

| In scope (Phase 5) | Out of scope (deferred) |
|---|---|
| `WorkspaceManager`: resolve project workspace dir under Workspace Root; clone-once + reuse | Multi-CLI support |
| `ScaffoldGenerator`: emit canonical SUID scaffold when no GitLab URL set | Multi-tenancy / auth |
| Config surface: `workspaceRoot` (default OS temp) + `templateGitlabUrl` (no default) | Dev-server live preview |
| Config read/update endpoints + a single-row config store | Dedicated merge agent |
| Wire dispatch: resolve workspace (clone template OR generate scaffold) before fan-out | Importable templates marketplace |
| — | Real git clone / real fs writes (compile-only bar carried) |

## 1. Domain payload

### 1.1 `PlatformConfigDto` — the single platform config row

```jsonc
{
  "id": "CONF0001",                  // singleton row (fixed id "CONFIG")
  "workspaceRoot": "/tmp/sei-online-code",  // default = OS temp + "/sei-online-code"
  "templateGitlabUrl": "",           // "" = no template → scaffold-generate path
  "createdDate": "2026-07-01T12:00:00"
}
```

- **Singleton**: exactly one config row (fixed id `CONFIG`); `save` upserts it.
- `workspaceRoot` default resolves to `${java.io.tmpdir}/sei-online-code` when
  unset (CONTEXT: "defaults to the OS temp directory"). Env override:
  `oc.workspace.root` (backend rule #11, env-with-fallback).
- `templateGitlabUrl` has **no default** (CONTEXT decision). Empty → the
  scaffold-generate path is the day-one path.

## 2. New endpoints — all under `/api`

| # | Method | Path | Request | Response `data` | Purpose |
|---|--------|------|---------|-----------------|---------|
| 31 | GET  | `/api/config/get` | — | `PlatformConfigDto` | Read platform config (creates default singleton if absent) |
| 32 | POST | `/api/config/save` | `{ workspaceRoot, templateGitlabUrl }` | `PlatformConfigDto` | Upsert platform config |
| 33 | GET  | `/api/workspace/resolve?projectId=` | — | `{ path, provisioned, source }` | Resolve a project's workspace dir; `source` = `CLONE`\|`SCAFFOLD`; `provisioned` = already existed |

- Ep #33 `source`: `CLONE` when `templateGitlabUrl` is set (clone-once from it),
  `SCAFFOLD` when empty (generate canonical SUID stack). `provisioned=true` when
  the workspace dir already existed (clone-once reuse — never re-clone).

## 3. `WorkspaceManager` seam (backend, ref `local_directory.go` + `repocache`)

```
resolve(projectId):
  root = config.workspaceRoot (or ${tmpdir}/sei-online-code)
  dir  = root / projectId
  if exists(dir):  return {dir, provisioned:true, source: prior}   // clone-once REUSE
  if config.templateGitlabUrl != "":
      gitClone(templateGitlabUrl, dir)                              // TODO(oma-deferred): real clone
      source = CLONE
  else:
      scaffoldGenerator.generate(dir)                               // TODO(oma-deferred): real fs write
      source = SCAFFOLD
  return {dir, provisioned:false, source}
```

- **Clone-once + reuse** (CONTEXT): first provisioning clones/generates; later
  Build-Loop rounds edit the existing workspace incrementally, never re-clone.
- **Path safety**: reject workspace roots that are system/drive roots
  (ref `local_directory.go` blacklist); unit-test the safety predicate.
- Compile-only: `gitClone` / real fs writes stay `TODO(oma-deferred)`; this phase
  wires resolution + the clone-vs-scaffold decision + a unit-tested
  `resolvePath` / `isSafeRoot` / `decideSource` logic.

## 4. `ScaffoldGenerator` (no-template path)

- Emits the **canonical SUID stack manifest** (file list + purpose) the platform
  would generate when no GitLab URL is configured: Vite + React + TS +
  `@ead/suid` + `@ead/antd-style` + `@ead/suid-utils-react` + MSW, with
  split-file conventions (per-page route files, per-feature mock files,
  glob-aggregated entries) so parallel tasks don't collide (ADR-0001).
- This phase delivers a **manifest** (a deterministic `List<ScaffoldFile{path,
  purpose}>`), unit-tested for the required structural invariants (has
  `package.json`, `src/pages/`, `src/mocks/`, MSW entry, glob-aggregated router).
  Real file emission stays `TODO(oma-deferred)`.

## 5. Sub-agent obligations (dispatch basis — separate contexts)

- **Backend (`eadp-backend`)**: `PlatformConfig` entity (singleton) + Flyway
  `V5__platform_config.sql` (1 table + default-row seed); `PlatformConfigDto` +
  `ConfigApi`/`WorkspaceApi` (eps #31–33); `ConfigService` (upsert singleton,
  env-fallback workspaceRoot); `WorkspaceManager` (§3 resolution + safety
  predicate, unit-tested; clone/fs deferred); `ScaffoldGenerator` (§4 manifest,
  unit-tested); wire `DispatchService` to resolve workspace before fan-out.
  Compile-only bar + unit tests for path-safety + scaffold manifest invariants.
- **Frontend (`suid`)**: MSW handlers eps #31–33; a **Settings/Config** page
  (form: Workspace Root + Template GitLab URL, load #31 / save #32); a
  workspace-resolve indicator on the project/dispatch view (#33, showing
  path + CLONE/SCAFFOLD source badge).

> Neither sub-agent may diverge from §1–§4 without updating THIS file first.
> Both build ONLY against this frozen contract, in separate contexts (project
> CLAUDE.md rule: front/back never in one context).
