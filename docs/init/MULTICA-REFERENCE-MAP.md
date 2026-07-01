# Multica Reference Map

Exact multica source locations to study for each rapid-app-dev module, so the
implementation doesn't re-locate them every time. Multica is a Go + cloud +
multi-CLI + multi-tenant system; we borrow only the **`claude` + single-user
local** slice. Each entry says what to TAKE and what to IGNORE. Paths are
relative to the multica repo root (`D:\project\github\multica`). Treat as a
blueprint to reimplement in Java, not code to copy. Paths may drift — verify
before relying.

## execution/ — the borrowed "gold"

| rapid-app-dev module | multica reference | Take / Ignore |
|---|---|---|
| `ClaudeRunner` (spawn claude, stream stdout/stderr) | `server/pkg/agent/claude.go`, `server/pkg/agent/agent.go` (the runner abstraction), `server/pkg/agent/proc_windows.go` (Windows process group/kill) | TAKE: how claude is invoked, args, stdout parsing, cancellation. IGNORE: the other 15 agent files (codex/gemini/...). |
| Orchestration (claim task → build env → spawn → report) | `server/internal/daemon/daemon.go` | TAKE: the run lifecycle skeleton. IGNORE: server polling/claim, heartbeat-to-cloud, multi-workspace watch. |
| `WorkspaceManager` (resolve dir, clone-once, reuse) | `server/internal/daemon/local_directory.go`, `server/internal/daemon/workdir_race_test.go`, `server/internal/daemon/repocache/cache.go` | TAKE: workspace-root layout, clone-once reuse, race-safe dir creation. IGNORE: cloud task-id dir scheme. |
| `WorktreeManager` (per-task worktree + merge) | `server/internal/daemon/execenv/git.go` | TAKE: `git worktree add` / branch / merge-back / cleanup commands. IGNORE: multi-remote / PR push flow. |
| Exec env assembly (prompt + skills + config into the workdir) | `server/internal/daemon/execenv/execenv.go`, `server/internal/daemon/execenv/runtime_config.go` | TAKE: how prompt + skills + per-run config are laid into the workdir before spawn. IGNORE: codex_*/cursor_*/openclaw_* CLI-specific files. |
| Workspace GC / disk reclaim | `server/internal/daemon/gc.go`, `server/internal/daemon/diskusage.go` | TAKE: TTL-based task-dir + artifact cleanup model. IGNORE: cloud-issue-status-driven triggers. |

## agents/ — built-in agent behavior + custom agent config

| rapid-app-dev | multica reference | Take / Ignore |
|---|---|---|
| Custom Agent config model (instructions + bound skills) | `server/pkg/agent/models.go`, `server/internal/handler/agent.go`, `server/internal/service/builtin_skills/multica-creating-agents/SKILL.md` | TAKE: agent = instructions + skill bindings; the create-agent UX. IGNORE: workspace membership, polymorphic assignee. |
| Custom Agent config **UI** | `packages/core/types/agent.ts` (agent shape: `name/description/instructions/model/skills[]`); `packages/views/agents/components/create-agent-dialog.tsx` + its `instructions-editor.tsx` / `skill-multi-select.tsx` / `model-dropdown.tsx`; `agent-detail-page.tsx`, `agent-detail-inspector.tsx`, `agent-profile-card.tsx`, `agents-page.tsx` | TAKE: the create/edit form (name, description, instructions, model, multi-select skills) and the two-step "create agent → attach skill_ids" flow; list + detail layout. IGNORE: runtime picker (claude-only), visibility/workspace scoping, squad context, custom_env. |
| Requirement / Dispatch / Deploy agents | (no direct analog — these are new) | New. Reference only the run mechanics above. |

## skill/ — import + lock + materialize to .claude/skills/

| rapid-app-dev | multica reference | Take / Ignore |
|---|---|---|
| `SkillMaterializer` (write SKILL.md into worktree) | `server/internal/daemon/local_skills.go`, `server/internal/daemon/execenv/codex_user_skills.go` | TAKE: materializing skills into the agent's skill dir. IGNORE: codex-specific stripping. |
| Skill lock + hash | `skills-lock.json` (root), `server/pkg/skillbundle/hash.go` | TAKE: lock-file schema + content hashing for reproducible imports. |
| Skill import sources + frontmatter | `server/internal/service/builtin_skills/multica-skill-importing/SKILL.md`, `server/internal/skill/frontmatter.go`, `server/internal/skill/reserved.go` | TAKE: GitHub-source import model, SKILL.md frontmatter parsing. |

## api/ — WebSocket log streaming

| rapid-app-dev | multica reference | Take / Ignore |
|---|---|---|
| Live run-log streaming to UI | `server/internal/daemonws/hub.go`, `server/internal/daemonws/notifier.go` | TAKE: hub/broadcast pattern for streaming run output to clients. IGNORE: daemon-to-cloud direction; we stream server→browser. |

## Frontend discipline (template + platform UI)

> Stack note: both the platform UI and the generated product use **`@ead/suid`**
> (per the `suid` skill) — NOT shadcn/Tailwind. Borrow only the *discipline*
> below, applied to EADP `ResultData` responses; components/styling come from
> `@ead/suid` / `@ead/antd-style`, not multica.

| rapid-app-dev | multica reference | Take / Ignore |
|---|---|---|
| Defensive network-JSON parsing (ADR-0002/0004) | `packages/core/api/schema.ts` (`parseWithFallback` + zod) | TAKE: schema-validate-network-JSON discipline; apply to the `ResultData`/ExtTable-paging contract. IGNORE: multica's UI components — use `@ead/suid`. |

## Deliberately NOT referenced
Multi-CLI adapters (`pkg/agent/*` except claude), cloud runtime
(`server/internal/cloudruntime`), multi-tenant workspace/auth, issue/comment/
project task semantics, autopilots. These are out of scope per CONTEXT decisions.
