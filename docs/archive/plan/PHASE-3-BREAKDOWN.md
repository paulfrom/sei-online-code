# Phase 3 Breakdown — Skills + Custom Agents (multica model)

> Derived from `docs/init/PLAN.md` Phase 3 + `docs/contracts/API-CONTRACT-PHASE3.md`.
> Dispatch basis: front/back built by **separate sub-agents in separate contexts**
> against the frozen Phase 3 contract. Backend → `eadp-backend`; frontend → `suid`.

## Goal (Phase 3 success contract)

- **Input**: a confirmed Spec + at least one imported Skill and one custom Agent
  bound to it.
- **Output**: skills import + hash-lock (idempotent by hash); custom agents
  persisted with bound skills; on dispatch, each task's resolved agent has its
  bound skills materialized into the worktree's `.claude/skills/<name>/SKILL.md`;
  Agent + Skill CRUD UI operational against MSW.
- **Verified by**: compile (backend) / build (frontend) green; `skill`/`agent`
  rows exist with correct `computedHash`/`skillIds`; `SkillMaterializer` unit
  test proves idempotent write; UI create-agent→attach-skill flow round-trips.

## Locked decisions (carried + new)

| Item | Decision | Impact |
|---|---|---|
| Backend run | **Compile-only this round** (Phase 2 bar carried) | Materializer is real+unit-tested; spawn/worktree path stays deferred seam. |
| Skill files | **Single-file (SKILL.md) only** this phase | No `FileRef[]`; multi-file bundle deferred. |
| Skill lock | **sha256 length-prefixed parts** (§6) | Idempotent re-import; server authoritative, FE never recomputes. |
| Agent↔Skill link | **Pick ONE**: join table `agent_skill` OR `skill_ids` JSON column | Backend documents choice in migration; FE only sees `skillIds[]`. |
| Built-in agents | **Seed 3** (`requirement/dispatch/deploy`), non-deletable | `builtin=true`; delete ep rejects them. |
| Designated skills | **Seed `suid` + `eadp-backend`** as LOCAL stubs | Satisfies CONTEXT designated-skill rule without vendoring bundles. |

## Track B — Backend (`eadp-backend`)

| Task | Deliverable | Contract ref |
|---|---|---|
| B16 | Entities `Skill`, `Agent` (+ `SkillSourceType` enum, String-UUID pk, audit base); agent↔skill link (join or JSON column, documented) | §1 |
| B17 | Flyway `V3__skill_agent.sql` (2 tables [+ join] + indexes; built-in agent + LOCAL skill seed) | §1,§4 |
| B18 | DTOs `SkillDto`/`AgentDto` (Swagger) + Feign `SkillApi`/`AgentApi` (eps #16–24) | §1,§2 |
| B19 | `SkillService`: import + §6 hash-lock (idempotent by hash); delete guarded (bound → reject) | §2,§6 |
| B20 | `AgentService`: CRUD + built-in seed/guard + `skills` attach (ep #24) | §2,§4 |
| B21 | `SkillMaterializer`: bound skills → `.claude/skills/<name>/SKILL.md` (+`.lock`), idempotent; **unit test** | §3 |
| B22 | Wire `DispatchService.fanOut`: resolve `Task.assignedAgent`→Agent, prepend `instructions`, materialize skills before spawn | §3,§5 |

> B22 real worktree-path + spawn stays Phase-2 `TODO(oma-deferred)`; wire the
> resolution+materialize call structure so it compiles and is unit-reachable.

## Track F — Frontend (`suid`)

| Task | Deliverable | Contract ref |
|---|---|---|
| F13 | MSW handlers eps #16–24 (import idempotent by hash; agent CRUD; skills attach) | §2 |
| F14 | Skills page: `ExtTable` list (ep #17) + import `ExtModal` (ep #16) + delete (ep #19) | ep #16–19 |
| F15 | Agents page: `ExtTable` list (ep #21); built-in rows read-only badge | ep #21 |
| F16 | Create/edit agent dialog: name/description/instructions/model + skill multi-select; two-step create→attach (ep #20 then #24) | ep #20,#24 |
| F17 | Router + menu entries (Skills, Agents); services in `src/services/` for eps #16–24 | §2 |

## Dependency order

```
Phase 3 contract (frozen) ──┬── Track B (compile-only + materializer unit test)  ← independent
                            └── Track F (MSW = live data)                          ← independent
```

Both depend ONLY on the frozen Phase 3 contract, never on each other.

## Out of scope (Phase 4+, do NOT build now)

- Full Build-Loop feedback re-entry, iteration timeline (Phase 4).
- Workspace GC / disk reclaim (Phase 4).
- Template GitLab repo + scaffold generator (Phase 5).
- Multi-file skill bundles, multi-CLI, real git spawn / runtime materialize.
