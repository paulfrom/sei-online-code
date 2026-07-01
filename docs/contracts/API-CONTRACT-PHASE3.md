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
| Skill import (GitHub/inline source) → hash-lock; re-import idempotent by hash | Template GitLab repo + scaffold generator (Phase 5) |
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
  "source": "local:suid",                 // "github:<owner>/<repo>[/path]" | "local:<name>" | "inline"
  "sourceType": "LOCAL",                  // GITHUB | LOCAL | INLINE
  "content": "# SUID Skill\n...",         // the SKILL.md body (frontmatter + markdown)
  "computedHash": "sha256:ab12…",         // lock: sha256 over (v1|source|name|description|content)
  "createdDate": "2026-07-01T10:00:00"
}
```

- `computedHash` is the **lock**: recomputed from the canonical parts on every
  import; re-importing identical content yields the same hash → idempotent (no
  new row, `save` returns the existing one). Hash recipe mirrors multica
  `BuildManifest`: `sha256("v1" ∥ source ∥ name ∥ description ∥ content)` with
  each part length-prefixed (see §6). Single-file skills only this phase
  (no per-file `FileRef[]`); multi-file bundles deferred.
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
  On dispatch, DispatchService resolves the assigned agent, and
  `SkillMaterializer` writes each bound skill into the worktree before spawn.

## 2. New endpoints (Phase 3) — all under `/api`

| # | Method | Path | Request | Response `data` | Purpose |
|---|--------|------|---------|-----------------|---------|
| 16 | POST | `/api/skill/import` | `{ name, description, source, sourceType, content }` | `SkillDto` | Import + hash-lock a skill; idempotent by hash |
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
for skill in agent.skillIds:
    dir  = <worktreePath>/.claude/skills/<skill.name>/
    write dir/SKILL.md  = skill.content
    write dir/.lock      = skill.computedHash    // reproducibility marker
```

- Idempotent: identical `computedHash` already on disk → skip rewrite.
- Compile-only this phase: the materializer is a real, unit-testable method;
  the actual worktree path resolution + spawn stays the Phase 2
  `TODO(oma-deferred)` runtime seam. `.claude/skills/` layout matches the CLI.

## 4. Built-in skill seeding (LOCAL sourceType)

`suid` and `eadp-backend` are seeded as `LOCAL` skills on first boot (Flyway
seed OR service-startup upsert). `source = "local:<name>"`, `content` is a
short pointer stub (full skill lives on the operator machine at
`~/.claude/skills/<name>`). This satisfies the CONTEXT rule that the designated
runtime skill = `suid` without vendoring the whole bundle into the DB.

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
writePart(h, source)
writePart(h, name)
writePart(h, description)
writePart(h, content)
computedHash = "sha256:" + hex(h.digest())

writePart(h, s):   // length-prefixed to avoid boundary ambiguity
    h.update(utf8(len(utf8(s))))   // decimal ascii length
    h.update(0x00)                 // separator
    h.update(utf8(s))
```

Backend implements this; frontend does NOT recompute (server is authoritative,
returns `computedHash`). Documented here so both sides read the same recipe.
