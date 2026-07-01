# Parallel development agents work in isolated git worktrees, merged back

The platform decomposes a confirmed Spec into tasks (Dispatch Agent) and runs
development agents **in parallel**, each in its own git worktree branched off the
project Workspace, merging results back on completion — mirroring Multica's
isolation model.

We rejected a simple serial pipeline (one agent at a time in one directory): it
avoids all merge complexity but is slow, and the user explicitly wants agents to
work concurrently. We rejected multiple agents writing the same physical
directory: it collides almost certainly.

Consequence: merge conflicts are possible. Mitigated primarily by having the
Dispatch Agent cut non-overlapping tasks along the template's file-boundary
conventions (per-page route files, per-feature mock files, glob-aggregated entry
points), with serial re-resolution by the responsible agent as fallback. A
dedicated merge/integration agent is deliberately deferred until conflicts prove
frequent.
