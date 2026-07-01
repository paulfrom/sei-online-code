# sei-online-code Platform — Founding Docs (`init/`)

Requirement-driven platform: a user describes a project in prose, an agent
refines it into a confirmable **Spec**, and the `claude` CLI (driven by a fixed
workflow + designated skills) builds, deploys, and iterates the **frontend**
with mock data. Backend is deferred. Architecture references Multica.

## Read in this order

1. **[CONTEXT.md](./CONTEXT.md)** — domain language + every decision made during
   the design interview (topology, stack, concurrency model, mock strategy,
   state machine, agent roster, config surface). Start here.
2. **[PLAN.md](./PLAN.md)** — phased implementation plan (walking skeleton first,
   then thicken). Each module links to its Multica reference.
3. **[MULTICA-REFERENCE-MAP.md](./MULTICA-REFERENCE-MAP.md)** — for each module,
   the exact Multica source path to study (and what to take / ignore), so the
   implementation never re-locates. Multica is a blueprint, not a code source.
4. **ADRs** — hard-to-reverse decisions with rationale:
   - [adr-0001](./adr-0001-parallel-worktree-isolation.md) — parallel dev agents in isolated git worktrees, merged back
   - [adr-0002](./adr-0002-msw-contract-first-mock.md) — frontend codes against a real API contract, mocked with MSW
   - [adr-0003](./adr-0003-java-spring-boot-server.md) — platform server is Java (EADP/sei-core), not Go
   - [adr-0004](./adr-0004-generated-products-use-eadp-suid-stack.md) — generated products use the team's EADP + SUID stack, not a neutral stack

## Snapshot of locked decisions

| Dimension | Decision |
|---|---|
| Shape | Full platform; greenfield codebase, Multica as architecture blueprint only |
| Topology | Single-developer local; no auth/multi-tenancy (PostgreSQL chosen for eventual team use) |
| Output | Pure frontend in **`@ead/suid`** (Vite + React + TS + @ead/antd-style + @ead/suid-utils-react); EADP-shaped API contract |
| Template | Configurable GitLab URL (no default); if unset → generate the same canonical SUID scaffold for confirmation |
| Workspace | Configurable Workspace Root (default OS temp), per-project subfolder, clone-once + reuse |
| Concurrency | Parallel dev agents in isolated git worktrees, merged back; conflict = file-boundary split + serial fallback |
| CLI | `claude` only; designated skill = `suid`; skills materialized to `.claude/skills/<name>/SKILL.md` |
| Agents | Customizable (Multica model); built-in: Requirement, Dispatch, Deploy |
| Mock | MSW + Spec-defined API contract in EADP shape (`ResultData` + ExtTable paging); seamless EADP backend later |
| Deploy | `vite build` → static `dist/` on a stable per-project port → iframe preview |
| Feedback | Optimization re-enters via Spec update (Spec = single source of truth) |
| Platform stack | Java (EADP/sei-core layered) + PostgreSQL + React/`@ead/suid` UI |

## Implementation standards

- Build the **Java backend** per the **`eadp-backend`** skill (EADP/sei-core
  layered framework, JPA, `ResultData`); build the **platform's React UI** per
  the **`suid`** skill (`@ead/suid` + `@ead/antd-style` + `@ead/suid-icons`).
  Both skills live at `D:\project\monorepo\sei-ams-mono\.claude\skills`.
- **`suid` is also the designated skill** the platform's agents use to build the
  generated product (same SUID stack). The skill *mechanism* (import/lock/
  materialize) is implemented by referencing Multica — see
  MULTICA-REFERENCE-MAP.md.

## Still open (implementation-time)

- Concrete template file-boundary conventions (router/mock aggregation)
- The concrete Spec JSON schema

## Phase 0 blocker before scaffolding

- Confirm whether a template GitLab URL exists, or the generate-scaffold path is the day-one path.
