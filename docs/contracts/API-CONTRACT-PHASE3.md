# Phase 3 API Contract Extension — Skills + Custom Agents (multica model)

> Extends `docs/contracts/API-CONTRACT.md` (Phase 1) and `-PHASE2.md` (Phase 2).
> Same EADP envelope rules (`ResultData<T>` / `PageResult<T>` / `Search`) apply
> verbatim — see Phase 1 §1. Phase 3 adds **importable/hash-locked Skills** and
> **user-defined custom dev Agents** (instructions + bound skills), plus the
> `SkillMaterializer` seam that writes bound skills into each worktree's
> `.claude/skills/<name>/SKILL.md` before a `ClaudeRunner` spawn.
> Refs (multica, blueprint only): `skills-lock.json`, `pkg/skillbundle/hash.go`,
> `daemon/local_skills.go`, `skill/frontmatter.go`; agent config
> `pkg/agent/models.go`, `handler/agent.go`, `core/types/agent.ts`.

## 0. Scope

| In scope (Phase 3) | Out of scope (Phase 4+) |
|---|---|
| `Skill` entity: import source + content + `computedHash` (lock) | Full Build-Loop feedback re-entry / iteration timeline (Phase 4) |
| `Agent` entity: name/description/instructions/model + bound `skillIds[]` | Workspace GC / disk reclaim (Phase 4) |
| Skill import (GitHub/inline/local/`builtin:` source) → name-dedup (409); runtime hash-lock | Template GitLab repo + scaffold generator (Phase 5) |
| `SkillMaterializer`: bound skills → `.claude/skills/<name>/SKILL.md` in worktree | Multi-CLI, multi-tenancy/auth, dedicated merge agent (deferred) |
| Dispatch uses `Task.assignedAgent` → resolve custom Agent → materialize its skills | Runtime spawn/live git (compile-only bar carried from Phase 2) |
| Agent CRUD UI (list/detail/create/edit) + skill multi-select | — |

## 1. New domain payloads

Ids are String-UUID (`IdGenerator.nextIdStr()`). Audit fields on read responses.

### 1.1 `SkillDto` — an importable, hash-locked instruction bundle

```jsonc
{
  "id": "SKIL0001",
  "name": "suid",                         // unique; maps to .claude/skills/<name>/
  "description": "@ead/suid component library skill",
  "config": { "origin": "local:suid" },   // "github:<owner>/<repo>[/path]" | "local:<name>" | "inline"
  "content": "# SUID Skill\n...",         // the SKILL.md body (frontmatter + markdown)
  "files": [ {"path": "references/general.md", "content": "..."} ],  // aux files (Phase 5)
  "computedHash": "sha256:ab12…",         // runtime lock: sha256 over (v1|config.origin|name|description|content)
  "createdDate": "2026-07-01T10:00:00"
}
```

- `config.origin` (Phase 4) replaces the former `source`/`sourceType` pair; the
  type is implicit in the prefix (`github:` / `local:` / `inline`).
- `computedHash` is the **lock**: recomputed at runtime from the canonical parts
  (never persisted). Re-importing the same **name** is rejected with 409 (dedup
  by `name`, not by hash). Hash recipe mirrors multica `BuildManifest`:
  `sha256("v1" ∥ config.origin ∥ name ∥ description ∥ content)` with each part
  length-prefixed (see §6). `files[]` do NOT enter the hash — the `.lock` still
  covers only the SKILL.md five-tuple; import is name-dedup'd and there is no
  update endpoint → aux files are immutable post-import → idempotency holds.
- `files[]` (Phase 5) carries per-file `{path, content}` aux files (sub-directories
  allowed; `path` validated to forbid absolute/`..` segments). Materialized into
  `.claude/skills/<name>/<path>` alongside `SKILL.md`.
- **Built-in skills** (`suid`, `eadp-backend`, `project-planning`, `feature-design`)
  are NOT `oc_skill` rows: they are vendored to the backend classpath
  `resources/skills/<name>/` and resolved via `builtin:<name>` synthetic ids (see
  §4). They never appear in `/skill/findByPage` / `/skill/findOne`.
- `name` MUST match `^[a-z0-9][a-z0-9-]{0,63}$` (materializes to a dir name).

### 1.2 `AgentDto` — a user-defined dev agent (instructions + bound skills)

```jsonc
{
  "id": "AGNT0001",
  "name": "suid-dev",
  "description": "Builds SUID pages against the EADP contract",
  "instructions": "You implement one page…",   // prompt prepended to Task.description
  "model": "",                                  // "" = let CLI resolve its own default
  "builtin": false,                             // true for Requirement/Dispatch/Deploy
  "skillIds": ["SKIL0001"],                     // bound skills, materialized before spawn
  "createdDate": "2026-07-01T10:05:00"
}
```

- Three **built-in** agents are seeded (`builtin=true`, non-deletable):
  `requirement-agent`, `dispatch-agent`, `deploy-agent`. Custom dev agents are
  `builtin=false`. `Task.assignedAgent` (Phase 2, default `dev-agent`) now
  resolves to an Agent `name`; if unresolved → fall back to built-in behavior.
- `skillIds` binding is the two-step multica flow: create agent → attach skills.
  Entries may be either a DB skill id or a `builtin:<name>` synthetic id (built-in
  skills, §4). On dispatch, DispatchService resolves the assigned agent, and
  `SkillMaterializer` writes each bound skill into the worktree before spawn.

## 2. New endpoints (Phase 3) — all under `/api`

| # | Method | Path | Request | Response `data` | Purpose |
|---|--------|------|---------|-----------------|---------|
| 16 | POST | `/api/skill/import` | `{ name, description, config, content, files[] }` | `SkillDto` | Import a skill; dedup by name (409 on conflict) |
| 17 | POST | `/api/skill/findByPage` | `Search` | `PageResult<SkillDto>` | List skills |
| 18 | GET  | `/api/skill/findOne?id=` | — | `SkillDto` | Load one skill |
| 19 | DELETE | `/api/skill/delete?id=` | — | `void` | Delete a skill (rejected if bound to any agent) |
| 20 | POST | `/api/agent/save` | `AgentDto` (no id = create) | `AgentDto` | Create/update a custom agent |
| 21 | POST | `/api/agent/findByPage` | `Search` | `PageResult<AgentDto>` | List agents (built-in + custom) |
| 22 | GET  | `/api/agent/findOne?id=` | — | `AgentDto` | Load one agent |
| 23 | DELETE | `/api/agent/delete?id=` | — | `void` | Delete a custom agent (built-in rejected) |
| 24 | POST | `/api/agent/skills` | `{ agentId, skillIds }` | `AgentDto` | Attach/replace an agent's bound skills |

> Phase 2 dispatch (ep #10) is unchanged on the wire; internally DispatchService
> now resolves `assignedAgent` → Agent and materializes its skills per task.
> No lifecycle-state changes in Phase 3.

## 3. `SkillMaterializer` seam (backend, ref `daemon/local_skills.go`)

Before each per-task `ClaudeRunner` spawn, for the task's resolved Agent:

```
for skillId in agent.skillIds:
    payload = skillId.startsWith("builtin:")
              ? BuiltInSkillRegistry.resolve(skillId)   // classpath skills/<name>/
              : SkillService.findOne(skillId)            // DB row (config/content/files)
    if payload == null: continue
    dir  = <worktreePath>/.claude/skills/<payload.name>/
    write dir/SKILL.md  = payload.content
    write dir/.lock      = payload.computedHash          // reproducibility marker
    for f in payload.files:
        write dir/<f.path> = f.content                   // aux files (sub-dirs allowed, §1.1)
```

- `builtin:<name>` ids are routed to `BuiltInSkillRegistry` (§4); all other ids
  load from `oc_skill` via `SkillService` (which populates `files[]`).
- Idempotent: identical `computedHash` already on disk (`.lock`) → skip rewrite
  of `SKILL.md` and all aux files. Aux files do not enter the hash (§1.1/§6).
- Compile-only this phase: the materializer is a real, unit-testable method;
  the actual worktree path resolution + spawn stays the Phase 2
  `TODO(oma-deferred)` runtime seam. `.claude/skills/` layout matches the CLI.

## 4. Built-in skills — classpath-vendored, `builtin:<name>` synthetic id

The four designated skills — `suid`, `eadp-backend`, `project-planning`,
`feature-design` — are **not** `oc_skill` rows. They are vendored into the
backend jar at `src/main/resources/skills/<name>/` (`SKILL.md` + optional
`references/**` aux files) and loaded at runtime by `BuiltInSkillRegistry`
(`ClassPathResource` + `PathMatchingResourcePatternResolver`).

Agents bind them through `oc_agent_skill` using the synthetic id
`builtin:<name>`. The join table's `skill_id` column deliberately has **no FK**
to `oc_skill` (V7) precisely so these synthetic ids can live there. At
materialize time (§3), `builtin:`-prefixed ids are routed to the registry
instead of `SkillService.findOne`.

- `suid` / `eadp-backend` are copied from the operator machine
  (`~/.claude/skills/<name>/`) — full content + `references/`.
- `project-planning` / `feature-design` are stub `SKILL.md` with a `TODO`
  placeholder; real content is to be authored later.
- V11 removes the former `LOCAL` pointer-stub `oc_skill` seed rows and rewrites
  the two seeded agent bindings (`planning-agent`, `feature-design-agent`) to
  `builtin:project-planning` / `builtin:feature-design`.
- Frontend: built-in skills do not appear in `/skill/findByPage`; the Agents
  multi-select merges a fixed `BUILTIN_SKILLS` constant into its options so
  users can bind `builtin:<name>` to any custom agent.

## 5. Sub-agent obligations (dispatch basis — separate contexts)

- **Backend (`eadp-backend`)**: `Skill`/`Agent` entities + Flyway
  `V3__skill_agent.sql` (2 tables + `agent_skill` join or `skill_ids` JSON
  column — pick ONE, document it); `SkillDto`/`AgentDto` + `SkillApi`/`AgentApi`;
  `SkillService` (import + hash-lock, idempotent) with the §6 hash;
  `AgentService` (CRUD + built-in seed + skill attach); `SkillMaterializer`
  (§3, unit-tested); wire DispatchService to resolve agent + materialize.
  Compile-only bar applies unless runtime verification is explicitly asked.
- **Frontend (`suid`)**: MSW handlers eps #16–24 (same envelopes); a **Skills**
  page (`ExtTable` list + import modal) and an **Agents** page (list + create/
  edit dialog with name/description/instructions/model + skill multi-select,
  the two-step create→attach flow); built-in agents shown read-only.

> Neither sub-agent may diverge from §1–§4 without updating THIS file first.
> Both build ONLY against this frozen contract, in separate contexts (project
> CLAUDE.md rule: front/back never in one context).

## 6. Canonical hash recipe (shared, normative)

```
h = sha256()
writePart(h, "v1")
writePart(h, config.origin)          // "github:..." | "local:..." | "inline" | "builtin:<name>"
writePart(h, name)
writePart(h, description)
writePart(h, content)
computedHash = "sha256:" + hex(h.digest())

writePart(h, s):   // length-prefixed to avoid boundary ambiguity
    h.update(utf8(len(utf8(s))))   // decimal ascii length
    h.update(0x00)                 // separator
    h.update(utf8(s))
```

- `config.origin` (Phase 4) is the hash input that replaced the former `source`
  column; the value string is unchanged, so hashes computed before PR3 remain
  stable (no reproducibility break).
- For built-in skills (§4), origin = `builtin:<name>`, description = null; the
  hash is a deterministic `.lock` marker only (classpath content is immutable).
- `files[]` (Phase 5) do NOT enter the recipe — the lock covers only the
  SKILL.md five-tuple (see §1.1 for why this preserves idempotency).
- Backend implements this; frontend does NOT recompute (server is authoritative,
  returns `computedHash`). Documented here so both sides read the same recipe.
