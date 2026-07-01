# Platform server is Java/Spring Boot, not Go (despite the Multica reference)

Multica — the architectural reference — is written in Go, and its daemon
(spawn-CLI, git worktree isolation, log streaming, heartbeat, GC) is the part we
borrow. We are nonetheless building the platform server in **Java / Spring Boot**.

The deciding factor is team familiarity: the team's stack is Java — specifically
the **EADP/sei-core layered framework** (Entity→DAO→DAOImpl→Service→Controller→
API, JPA, `ResultData`), not vanilla Spring Boot; backend code follows the
`eadp-backend` skill. The portable Multica machinery is a small, language-agnostic
surface — detect a CLI on PATH, `git worktree add`, spawn `claude` as a
subprocess and stream stdout/stderr over WebSocket, scheduled GC. Because we
target only the `claude` CLI (no multi-CLI / ACP adapters), there is no large Go
body worth copying. Maintainability under a Java team outweighs reusing a few
hundred lines of Go.

Consequence: Multica is treated as a blueprint, not a code source. The React/Vite
frontend (both the generated app and the platform Web UI) is unavoidably TS
regardless of server language. PostgreSQL is the datastore (chosen for eventual
team use), but MVP remains single-user with no multi-tenancy.
