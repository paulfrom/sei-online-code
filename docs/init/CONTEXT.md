# Rapid App Dev Platform (working title)

A requirement-driven platform where a user describes a project in prose, an agent
refines it into a spec, and a coding CLI (driven by a fixed workflow and a
designated skill) builds, deploys, and iterates the **frontend** with mock data.
Backend is deferred. Architecture references Multica's server + daemon + spawn-CLI model.

## Language

**Project Design**:
The user-supplied prose that describes what they want built. The raw input to the loop.

**Spec** (需求细分/细化):
The agent-refined, structured breakdown of a Project Design that the user reviews
and confirms. Includes an **API Contract** (endpoints + request/response schemas)
that the frontend codes against and that a future backend will implement.
_Avoid_: requirements doc, PRD (until disambiguated)

**Mock strategy**:
MSW (Mock Service Worker) intercepts the real HTTP API Contract in the browser
and returns fake data. Adding a backend later = implement the same contract +
disable MSW; frontend code is unchanged.

**Spec format**:
Structured (JSON/YAML against a fixed schema): `pages[]`, `components[]`,
`entities[]`, `apiContract[]`. Free-form prose lives in each field's
`description`. Machine-readable so Dispatch partitions tasks by file boundary,
the UI renders a structured editable view, and incremental updates are diffable.

**Template Repo**:
A standard starter frontend repository the project is cloned from. Agents modify
this scaffold rather than generating from scratch. Source = a **configurable
GitLab URL with no default**. If unconfigured, the platform **generates the
canonical scaffold** (same stack/conventions, not a free framework choice) for
the user to confirm — clone-path and generate-path converge on the same stack.

**Workspace Root**:
The configurable base directory under which each project gets its own
subfolder (the Workspace). Defaults to the OS temp directory when unset.
Multica analog: `MULTICA_WORKSPACES_ROOT`.

**Skill**:
A definable/importable instruction bundle, handled the same way as Multica
(lock file + importable sources). Bound to agents.

**Agent**:
A defined worker with its own instructions (prompt) + bound Skills. Multiple
agents collaborate in the same Workspace, mirroring Multica's agent model.
Agent configs are user-customizable (like Multica), but three are **built-in**:

- **Requirement Agent** (需求分析): refines Project Design → Spec.
- **Dispatch Agent** (任务分配): decomposes the confirmed Spec into tasks and assigns them to development agents.
- **Deploy Agent** (服务部署): builds and deploys/previews the frontend.

Development agents (the ones that actually write code) are user-defined custom agents.

**Workspace**:
The single shared working directory (the cloned Template Repo) where all agents
for a project do their work. "Same Workspace" means the same project-level repo:
during parallel execution each development agent gets an isolated git **Worktree**
branched off the Workspace, and results merge back. It does NOT mean multiple
agents writing the same physical directory simultaneously.

**Worktree**:
A per-task isolated git worktree branched off the Workspace, given to a
development agent so parallel agents don't collide. Merged back into the
Workspace on completion (Multica's isolation model).

**Build Loop**:
The repeating cycle: refine → user confirm → develop → deploy → user feedback → develop → deploy → user accept.
Optimization feedback re-enters via the Requirement Agent: it incrementally
updates the confirmed Spec, the user confirms the change, then Dispatch runs.
The **Spec is the single source of truth** — feedback never bypasses it.

**Acceptance** (验收):
The user's explicit sign-off that a deployed iteration meets the Spec. Terminates (a round of) the loop.

**Iteration**:
A first-class persisted record of one Build Loop round, bound to a Spec version,
its deployed artifact, and the task/agent run history. Gives the per-round
traceability the Spec-as-source-of-truth rule requires.

**Project lifecycle states**:
`DRAFTING` → `SPEC_REFINING` → `SPEC_REVIEW` → `DISPATCHING` → `DEVELOPING` →
`MERGING` → `DEPLOYING` → `PREVIEW` → `ACCEPTED`; plus `FAILED` (any stage) and
`CANCELLED`. From `PREVIEW`: user optimization → back to `SPEC_REFINING`;
user acceptance → `ACCEPTED`.

## Relationships

- A **Project Design** is refined into one **Spec**
- A **Spec** drives one or more **Build Loop** iterations
- A project clones one **Template Repo** into one **Workspace**
- Multiple **Agents** work in the same **Workspace**; each Agent has its own instructions + bound **Skills**
- Each **Build Loop** iteration produces a deployed frontend (mock data only)
- A **Project** has many **Iterations**; each Iteration binds one Spec version + one deployed artifact + its run history

## Open (still to resolve)

- Template repo concrete structure / file-boundary conventions (router/mock aggregation)
- The concrete Spec JSON schema
- Where the platform's own repo lives (trivial; defaults to current ../rapid-app-dev scaffold)

## Decisions (cont.)

- **Template source = a GitLab clone URL.**
- **Workspace is cloned-once, then reused.** Template is cloned into the
  Workspace on first provisioning only; later Build Loop iterations edit the
  existing Workspace incrementally (never re-clone, never lose prior work).
- Per coding run: resolve Workspace → (clone template if new) → send coding
  instruction to the CLI.
- **Concurrency = parallel dev agents in isolated worktrees, merged back** (see ADR 0001).
- **Conflict strategy = A-primary + B-fallback.** Dispatch Agent cuts
  non-overlapping tasks using the template's file-boundary conventions
  (per-page route files, per-feature mock files, glob-aggregated entry points)
  so merges are mostly fast-forward; on conflict, fall back to serial
  re-resolution by the responsible dev agent. No standalone merge agent (yet).
- **Template structure constraint:** must use split-file + convention-aggregation
  layout so parallel tasks rarely touch the same file.
- **Coding CLI = `claude` (Claude Code) only at start.** Multi-CLI detection
  deferred. Skills are imported + hash-locked (Multica's skills-lock model) and
  materialized into `.claude/skills/<name>/SKILL.md` in the worktree; agent
  instructions name which skill to use. The **designated skill = `suid`** (the
  team's `@ead/suid` component library).
- **Frontend stack of the generated app (the template) = Vite + React + TS +
  `@ead/suid` + `@ead/antd-style` + `@ead/suid-utils-react` + MSW.** Same stack
  as the platform UI (NOT Tailwind/shadcn). Its API Contract follows EADP
  conventions (`ResultData` envelope + ExtTable paging shape) so the deferred
  EADP backend drops in (see ADR 0002, ADR 0004).
- **Deploy = `vite build` → static `dist/`, served on a stable per-project port;**
  preview is an iframe to that port. Each iteration rebuilds and replaces the
  served dir (stable URL, refresh shows new version). No dev server, no
  containers at start.
- **Platform stack = Java on the EADP/sei-core layered framework (server),
  PostgreSQL (persistence), React + `@ead/suid` (Web UI).** NOT generic Spring
  Boot, NOT Tailwind/shadcn. Java chosen for team familiarity over Go (ADR 0003);
  multica's Go execution layer is architectural reference only.
  - **Backend** follows `eadp-backend`: `Entity → DAO → DAOImpl → Service →
    Controller → API(Feign)` + DTO; JPA-only; base types (`BaseAuditableEntity`,
    `BaseEntityDto/Dao/Service/Controller/Api`); String-UUID ids via
    `IdGenerator.nextIdStr()`; `ResultData` responses; `OperateResult` +
    `@Transactional` for writes.
  - **Platform UI** follows `suid`: components from `@ead/suid` (ExtTable,
    ExtModal, Form, ComboList…), styling via `@ead/antd-style` `createStyles` +
    design tokens, icons from `@ead/suid-icons`, data via `useStore`/`request`,
    global state via `createAppStore`. No Tailwind, no shadcn, no raw antd. Run
    `suid info <component> --format json` before using any component.
- **PostgreSQL (not SQLite)** chosen to avoid a DB migration when the team adopts
  the tool later. This does NOT mean building multi-tenancy now: MVP stays
  single-user / no-auth (per Q3). Team use is a flagged future direction only.

## Implementation standards (how WE build the platform)

Distinct from the platform's own skill feature — these govern building the
rapid-app-dev platform itself:

- **Java backend** follows the **`eadp-backend`** skill.
- **Frontend (the platform's own React UI)** follows the **`suid`** skill.
- Both skills live at `D:\project\monorepo\sei-ams-mono\.claude\skills`; consult
  them at implementation time.

The platform's **own "designated skill" subsystem** — the *mechanism* by which
its agents import/lock/materialize skills into `.claude/skills/` — is
**implemented by referencing Multica's skill machinery** (see
MULTICA-REFERENCE-MAP, skill/ section). The *content* of the designated skill the
agents use to build the generated product is **`suid`** (the generated product
uses the same `@ead/suid` + EADP-contract stack as the platform UI).
`eadp-backend` stays build-the-platform-only — the generated product has no
backend yet, but its API Contract follows EADP conventions (`ResultData` +
ExtTable paging) so a future EADP backend drops in.

## Decisions

- **Greenfield codebase, architecture reference only.** New domain model
  (Project Design → Spec → Build Iteration → Deploy → Preview). Multica's
  daemon machinery (detect local CLI → isolated workdir → spawn → stream →
  heartbeat → GC) is portable and worth borrowing; its Issue/task semantics
  are not adopted.
- **Single-developer local topology.** Server + agent runner + frontend UI +
  the coding CLI all run on one machine (one docker-compose at most). No
  multi-tenancy, no auth, no remote daemon registration. (Whether server and
  agent-runner are separate processes or one process is still open.)

## Flagged ambiguities

- The word **"repo/仓库" is overloaded** — three distinct things, kept separate:
  1. **Platform source repo** — where rapid-app-dev's own code lives (trivial, not a domain term).
  2. **Workspace** (+ Workspace Root) — the per-project working dir cloned/generated for the build.
  3. **Template Repo** — the GitLab starter source (or the generated canonical scaffold).
- Two distinct "frontends": the **platform's own UI** (built with `suid`) vs the
  **generated product's frontend** (built by agents using Multica-style
  designated skills). Two distinct skill roles: `eadp-backend`/`suid` =
  build-the-platform standards; the platform's skill subsystem = Multica-referenced.
