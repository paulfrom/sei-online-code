# Frontend codes against a real API Contract, mocked with MSW

All frontend data is mocked, but the mock is delivered via MSW (Mock Service
Worker) intercepting a **real HTTP API Contract** defined in the Spec — not via
static data imported directly into components.

We chose this over direct static-fixture imports because the backend is an
explicitly deferred follow-up. With MSW + a contract-first frontend, adding the
backend later means implementing the same endpoints and disabling MSW, with zero
changes to component code. Static imports would force a rewrite of every data
access site when the real backend arrives.

Consequence: the template must ship MSW scaffolding, and the Requirement Agent
must produce an API Contract (endpoints + request/response schemas) as part of
every Spec. The contract follows **EADP conventions** — `ResultData` response
envelope and the ExtTable paging request/response shape (`@ead/suid`'s
`useStore`/`ExtTable` consume exactly this) — so the deferred EADP backend (see
ADR 0004) drops in with zero frontend change. This mirrors Multica's own
schema-validated API discipline.
