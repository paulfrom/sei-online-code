# Implementation Plan

Derived from CONTEXT.md + ADR 0001–0003. Strategy: build the thinnest
end-to-end Build Loop first (walking skeleton), then thicken each layer.

> For each module, **which multica source to study is pinned in
> [MULTICA-REFERENCE-MAP.md](./MULTICA-REFERENCE-MAP.md)** — consult it before
> reimplementing any execution/skill/streaming piece, instead of re-locating.

> **Implementation standards:** the **Java backend** follows the **`eadp-backend`**
> skill; the **platform's React UI** follows the **`suid`** skill (both at
> `D:\project\monorepo\sei-ams-mono\.claude\skills`). These guide how we build
> the platform — they are NOT the runtime designated skills the platform's agents
> use to build the generated product (that subsystem is Multica-referenced).

## Module shape

```
rapid-app-dev/
├── server/                 # Java + Spring Boot
│   ├── domain/             # Project, Spec, Iteration, Agent, Skill, Task, Deployment
│   ├── persistence/        # PostgreSQL (Flyway migrations + JDBC/JPA)
│   ├── execution/          # multica-borrowed machinery (reimplemented)
│   │   ├── WorkspaceManager # clone-once, resolve dir
│   │   ├── WorktreeManager  # git worktree add/merge/cleanup
│   │   ├── ClaudeRunner     # ProcessBuilder spawn + stream stdout/stderr
│   │   └── SkillMaterializer# import+hash-lock → .claude/skills/<name>/SKILL.md
│   ├── agents/             # built-in: Requirement, Dispatch, Deploy
│   ├── loop/               # state machine + orchestrator
│   ├── deploy/             # vite build + per-project static host + port registry
│   └── api/                # REST + WebSocket (log streaming)
├── web/                    # React + @ead/suid + @ead/antd-style (platform UI, per `suid`)
└── (template repo lives in a SEPARATE GitLab repo)
```

## Phase 0 — Bootstrap (blocker: repo location)
- Decide final repo location, init git, Spring Boot + Vite scaffolds, Postgres via docker-compose, Flyway baseline.

## Phase 1 — Walking skeleton (thinnest full loop, ONE built-in path)
Goal: paste a Project Design → get a deployed preview, no parallelism, no custom agents yet.
1. Domain + PG tables: `project`, `spec`, `iteration` (+ state column).
2. `ClaudeRunner`: spawn `claude` with a prompt in a dir, stream output to WebSocket.
   _Ref: `pkg/agent/claude.go`, `pkg/agent/agent.go`, `daemonws/hub.go`._
3. `WorkspaceManager`: resolve/create project dir under the configurable
   **Workspace Root** (default = OS temp); clone template from the configured
   **GitLab URL** once. If no URL configured → generate the canonical scaffold
   (same stack) for user confirmation instead of cloning.
   _Ref: `daemon/local_directory.go`, `daemon/repocache/cache.go`._
4. Requirement Agent (single claude run) → structured Spec JSON; persist; `SPEC_REVIEW`.
5. UI: create project, view Spec, confirm.
6. Deploy: `vite build` → serve `dist/` on a per-project port; iframe preview.
7. State machine wired DRAFTING→…→PREVIEW with serial execution (no worktree yet).

## Phase 2 — Concurrency (ADR-0001)
- Dispatch Agent: Spec → non-overlapping tasks along template file-boundary conventions.
- `WorktreeManager`: per-task worktree, parallel `ClaudeRunner`, merge back (fast-forward primary, serial re-resolve fallback). _Ref: `daemon/execenv/git.go`._
- `task`/`run` tables; live multi-agent log streaming in UI.

## Phase 3 — Skills + custom agents (multica model)
- `SkillMaterializer`: skills-lock + hash + import → `.claude/skills/`. _Ref: `skills-lock.json`, `pkg/skillbundle/hash.go`, `daemon/local_skills.go`, `skill/frontmatter.go`._
- Agent config (instructions + bound skills) persisted; UI to create/edit custom dev agents. _Ref: backend `pkg/agent/models.go`, `handler/agent.go`, builtin_skills/multica-creating-agents; UI `views/agents/components/create-agent-dialog.tsx` (+ instructions-editor / skill-multi-select / model-dropdown), `agent-detail-page.tsx`, `agents-page.tsx`, `core/types/agent.ts`._

## Phase 4 — Full Build Loop
- Deploy Agent as a first-class step; PREVIEW feedback → Requirement Agent incremental Spec update → re-confirm → re-dispatch.
- Iteration entity binds Spec version + deployment + run history; iteration timeline UI.
- FAILED/CANCELLED handling + retry; workspace GC. _Ref: `daemon/gc.go`, `daemon/diskusage.go`._

## Phase 5 — Template repo (separate) + scaffold generator
- **`@ead/suid` starter**: Vite + React + TS + `@ead/suid` + `@ead/antd-style` + `@ead/suid-utils-react`, with MSW scaffold mocking the **EADP contract shape** (`ResultData` + ExtTable paging), and split-file conventions (per-page route files, per-feature mock files, glob-aggregated entries) so parallel tasks don't collide. Designated skill = `suid`.
- **No-template fallback:** a scaffold generator (claude run) that emits the same canonical SUID stack/structure when no GitLab URL is configured, for user confirmation.
- Config surface: Workspace Root (default OS temp) + Template GitLab URL (no default).

## Deferred (flagged, not built now)
- Multi-CLI support, multi-tenancy/auth, dev-server live preview, dedicated merge agent, importable templates.

## Success criteria (per phase 1 — the validation contract)
- Input: a Project Design string + a template GitLab URL.
- Output: a running iframe preview of a built frontend whose pages/data match the confirmed Spec, all data served by MSW.
- Verified by: create a project end-to-end in the UI, confirm Spec, see the deployed preview update; PG rows for project/spec/iteration exist with correct states.
