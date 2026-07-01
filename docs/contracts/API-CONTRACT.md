# Phase 1 API Contract — sei-online-code Platform

> Contract-first per **ADR-0002 / ADR-0004**. The frontend codes against these
> HTTP endpoints; MSW mocks them in the browser. When the EADP/sei-core backend
> arrives it implements the same endpoints and MSW is disabled — **zero frontend
> change**. Response/request shapes therefore MUST match EADP conventions exactly.
>
> Scope = **Phase 1 walking skeleton only** (PLAN.md Phase 1): create project,
> refine → Spec, review/confirm Spec, deploy, preview. Concurrency, custom
> agents, skills materialization (Phase 2–5) are out of scope here.

## 1. Envelope conventions (EADP — non-negotiable)

### 1.1 `ResultData<T>` — every non-paging response

Source of truth: `eadp-backend` skill (`references/dto.md`). Java produces this via
`ResultData.success(data)` / `ResultData.success(message, data)` / `ResultData.fail(message)`
/ `ResultData.exception(message)`.

```jsonc
{
  "success": true,        // boolean — operation succeeded
  "message": null,        // string | null — human message (set on fail/exception)
  "data": { }             // T | null — payload; null on failure
}
```

- MSW MUST serialize exactly `{ success, message, data }`.
- On error: `{ "success": false, "message": "<reason>", "data": null }`.
- `@ead/suid-utils-react` `request` returns the parsed body; call sites read
  `res.data`. Per Multica-Reference-Map (ADR-0002/0004), the frontend SHOULD
  schema-validate this envelope defensively (`parseWithFallback` discipline)
  before trusting `data`.

### 1.2 `PageResult<T>` — every paged list response

Source of truth: `eadp-backend` `references/service.md` / `dto.md`. This is the
`data` payload of a `ResultData<PageResult<T>>`.

```jsonc
{
  "page": 1,              // int — current page (1-based)
  "records": 137,         // int — TOTAL RECORD COUNT (not page size)
  "total": 7,             // int — TOTAL PAGE COUNT
  "rows": [ /* T[] */ ]   // array — the page's rows
}
```

> ⚠️ Field-name traps (verified against the skill):
> - `records` = **total record count**, `total` = **total page count**. Do NOT
>   swap. `@ead/suid` `ExtTable remotePaging` expects this exact shape.
> - `rows` holds the data array (not `list` / `content` / `data`).

### 1.3 `Search` — every paged list request body

Source: `eadp-backend` `references/service.md` (`Search` = filters + sortOrders +
pageInfo + quickSearch). Sent as the POST body from `@ead/suid` `ExtTable`/`useStore`.

```jsonc
{
  "pageInfo": { "page": 1, "rows": 15 },     // defaults 1 / 15
  "quickSearchValue": "kw",                   // optional — LIKE across quickSearchProperties
  "quickSearchProperties": ["name", "code"],  // optional
  "filters": [                                 // optional AND-combined conditions
    { "fieldName": "state", "value": "PREVIEW", "operator": "EQ" }
  ],
  "sortOrders": [
    { "property": "createdDate", "direction": "DESC" }
  ]
}
```

- `operator` ∈ EADP `SearchFilter.Operator` (`EQ`, `NE`, `LK`, `IN`, `GT`, `GE`,
  `LT`, `LE`, `NU`, `NN`, …). Phase 1 mocks only need `EQ` + `LK`.
- `direction` ∈ `ASC` | `DESC`.

## 2. Domain payloads (Phase 1 entities)

Ids are **String-UUID** (`IdGenerator.nextIdStr()`), never numeric.
`BaseEntityDto` contributes `id`; audit fields (`createdDate`, `lastEditedDate`,
etc.) are present on read responses.

### 2.1 `ProjectDto`

```jsonc
{
  "id": "PRJ0001",
  "name": "库存管理台",
  "design": "用户输入的原始 Project Design 文字…",  // prose input
  "state": "DRAFTING",   // lifecycle state — see §4
  "currentSpecId": null, // string | null — confirmed/active spec
  "currentIterationId": null,
  "createdDate": "2026-07-01T10:00:00",
  "lastEditedDate": "2026-07-01T10:00:00"
}
```

### 2.2 `SpecDto` — the refined, confirmable structure

Structure per CONTEXT.md "Spec format": `pages[]`, `components[]`, `entities[]`,
`apiContract[]`; each field carries a free-form `description`.

```jsonc
{
  "id": "SPEC0001",
  "projectId": "PRJ0001",
  "version": 1,           // int — incremented on each incremental refine
  "state": "SPEC_REVIEW", // DRAFT | SPEC_REVIEW | CONFIRMED
  "pages": [
    { "key": "list", "title": "库存列表", "route": "/stock/list",
      "description": "分页表格 + 关键字搜索" }
  ],
  "components": [
    { "key": "StockTable", "type": "ExtTable", "page": "list",
      "description": "远程分页表格，列: 编码/名称/数量" }
  ],
  "entities": [
    { "key": "Stock", "fields": [
        { "name": "code", "type": "string", "description": "库存编码" },
        { "name": "qty",  "type": "number", "description": "数量" }
    ] }
  ],
  "apiContract": [
    { "method": "POST", "path": "/api/stock/findByPage",
      "requestShape": "Search", "responseShape": "ResultData<PageResult<StockDto>>",
      "description": "库存分页查询" }
  ],
  "createdDate": "2026-07-01T10:05:00"
}
```

> `apiContract[]` is what the generated product's MSW handlers implement. Its
> `requestShape` / `responseShape` reference the §1 envelopes so the generated
> product itself stays EADP-shaped (ADR-0004 recursion).

### 2.3 `IterationDto` — one Build Loop round

```jsonc
{
  "id": "ITER0001",
  "projectId": "PRJ0001",
  "specId": "SPEC0001",
  "specVersion": 1,
  "state": "PREVIEW",     // mirrors project lifecycle for this round — see §4
  "previewUrl": "http://localhost:41001",  // per-project static port; null until DEPLOYING done
  "createdDate": "2026-07-01T10:10:00"
}
```

## 3. Endpoints (Phase 1)

All under base path `/api`. List endpoints are POST + `Search` body (EADP idiom).

| # | Method | Path | Request | Response `data` | Purpose |
|---|--------|------|---------|-----------------|---------|
| 1 | POST | `/api/project/save` | `{ name, design }` | `ProjectDto` | Create project (state → `DRAFTING`) |
| 2 | GET  | `/api/project/findOne?id=` | — | `ProjectDto` | Load one project |
| 3 | POST | `/api/project/findByPage` | `Search` | `PageResult<ProjectDto>` | List projects |
| 4 | POST | `/api/project/refineSpec` | `{ projectId }` | `SpecDto` | Requirement Agent: design → Spec (state → `SPEC_REVIEW`) |
| 5 | GET  | `/api/spec/findOne?id=` | — | `SpecDto` | Load a Spec |
| 6 | POST | `/api/spec/confirm` | `{ specId }` | `IterationDto` | Confirm Spec → start iteration (state → `DISPATCHING`) |
| 7 | POST | `/api/iteration/deploy` | `{ iterationId }` | `IterationDto` | Deploy Agent: vite build → previewUrl (state → `PREVIEW`) |
| 8 | GET  | `/api/iteration/findOne?id=` | — | `IterationDto` | Poll iteration state / previewUrl |
| 9 | GET  | `/api/project/state?id=` | — | `{ state, iterationId }` | Poll project lifecycle |

### 3.1 WebSocket — live run-log streaming

Ref: Multica `daemonws/hub.go` (server→browser direction only).

- URL: `ws://<host>/ws/run/{iterationId}`
- Server pushes newline-delimited JSON frames:
  ```jsonc
  { "iterationId": "ITER0001", "stream": "stdout", "line": "vite v5 building…", "ts": "2026-07-01T10:10:03" }
  ```
- `stream` ∈ `stdout` | `stderr` | `system`. A terminal frame carries
  `{ "stream": "system", "line": "DONE", "state": "PREVIEW" }`.

## 4. Lifecycle state machine (authoritative — from CONTEXT.md)

```
DRAFTING → SPEC_REFINING → SPEC_REVIEW → DISPATCHING → DEVELOPING
        → MERGING → DEPLOYING → PREVIEW → ACCEPTED
```

- Any stage → `FAILED`; user abort → `CANCELLED`.
- From `PREVIEW`: user optimization → back to `SPEC_REFINING`; acceptance → `ACCEPTED`.
- **Phase 1 runs it serially, single built-in path** — `DISPATCHING`/`DEVELOPING`/
  `MERGING` collapse to a single serial `ClaudeRunner` execution (no worktrees yet;
  that's Phase 2). The states still exist in the enum for forward compatibility.
- `state` values are transmitted as the exact uppercase tokens above in every DTO
  and WS frame. MSW mocks MUST use these tokens verbatim.

## 5. MSW mock obligations (frontend side)

For Phase 1 the frontend ships MSW handlers for endpoints 1–9 + a WS mock (or a
polling fallback on `/api/iteration/findOne` if WS mocking is deferred). Every
handler returns the §1 envelope. A handler that lists returns
`ResultData<PageResult<T>>`; a handler that reads one returns `ResultData<T>`.

> This contract file is the single source both the frontend sub-agent (MSW +
> `@ead/suid` pages) and the future backend sub-agent (EADP controllers) build
> against. Neither may diverge from §1–§4 without updating this file first.
