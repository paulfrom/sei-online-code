# Codex App-Server Runner Design

## Purpose

Replace SEI's Codex CLI integration from one-shot `codex exec --json -o <file>` to the official `codex app-server --listen stdio://` JSON-RPC interface, while completing the remaining multi-CLI runner tail work: Claude MCP argument wiring, a richer SEI runtime brief, and updated verification/docs.

The current PR1-PR3 foundation stays intact: `CliRunner`, `CliRunnerRegistry`, `Agent.cliTool`, `Agent.model`, `Agent.mcpConfig`, per-run `CODEX_HOME`, sandbox config, MCP managed blocks, shared Codex home links, user skill seeding, and `AgentBriefWriter`.

## Sources Checked

- `docs/plan/MULTI-CLI-RUNNER.md`: PR1 through PR3 are complete; remaining major item is Codex app-server JSON-RPC, plus Claude MCP and brief enrichment.
- Official Codex manual, `Codex App Server` section fetched on 2026-07-05: app-server uses JSON-RPC 2.0 messages over JSONL stdio, starts with `initialize` then `initialized`, then `thread/start`, `turn/start`, and notifications until `turn/completed`.
- Local Codex CLI `0.142.5`: `codex app-server --listen stdio://` and `generate-json-schema --experimental` are available.
- multica reference: `server/pkg/agent/codex.go` uses app-server stdio, auto-approves server approval requests, accumulates agent message deltas, and treats `turn/completed` as the terminal event.

## Scope

In scope:

- Switch `CodexRunner` production execution to `codex app-server --listen stdio://`.
- Add a focused Java JSON-RPC helper for newline-delimited app-server messages.
- Preserve existing per-run Codex environment setup before spawning the app-server process.
- Aggregate final output from `item/agentMessage/delta` notifications.
- Treat `turn/completed` as success unless the notification carries a failed/errored status.
- Auto-approve known Codex app-server approval requests in daemon mode.
- Add fake-executable tests that simulate app-server JSONL without OpenAI network access.
- Keep or adapt the real Codex e2e as env/on-demand verification, expected to fail on the current machine if OpenAI returns `403 unsupported_country_region_territory`.
- Wire Claude MCP via `--mcp-config` using a temporary JSON file, not argv inline JSON.
- Enrich the runtime brief with SEI execution context while keeping marker-block idempotence.
- Update `docs/plan/MULTI-CLI-RUNNER.md` to mark completed items and list residual external verification.

Out of scope for this pass:

- Persistent cross-run Codex app-server daemon reuse.
- Thread resume/fork/history support.
- Token usage scanning from Codex session JSONL.
- Full multica semantic inactivity diagnostics.
- WebSocket or Unix-socket app-server transports.
- Adding a third vendor such as Gemini unless a separate product requirement appears.

## Architecture

`CodexRunner` remains the Spring `CliRunner` bean for `tool() == "codex"`. Its public interface does not change. Internally, it delegates app-server protocol work to a small package-private helper so runner code stays about process lifecycle and result handling.

New package-private units:

- `CodexAppServerClient`: owns JSON-RPC request IDs, pending response futures, stdin writes, stdout line handling, notifications, and server requests.
- `CodexAppServerEvents`: collects user-visible output and terminal status from notifications.
- `CodexAppServerMessage`: small Jackson-backed helpers for method/id/result/error classification.

The execution flow is:

1. Create per-run `CODEX_HOME`.
2. Call existing `CodexSandboxConfig.write`, `linkSharedHome`, `seedUserSkills`, and `writeMcpBlock`.
3. Spawn `codex app-server --listen stdio://` with `CODEX_HOME` and inherited proxy env.
4. Start stdout reader and stderr pump.
5. Send `initialize` with client metadata and `capabilities.experimentalApi=true`.
6. Send `initialized` notification.
7. Send `thread/start` with `model` when present and `cwd` when present.
8. Extract `thread.id` from the response.
9. Send `turn/start` with `threadId` and `input: [{type:"text", text: prompt}]`.
10. Read notifications until `turn/completed` or process/context failure.
11. Return accumulated `item/agentMessage/delta` text after stripping markdown fences.
12. Close stdin and clean up temporary Codex home.

## Protocol Handling

Requests sent by SEI include `jsonrpc: "2.0"` for compatibility, though Codex app-server omits that field requirement on the wire. Responses are matched by numeric `id`.

Supported server notifications:

- `item/agentMessage/delta`: append `params.delta` to final output and emit stdout log frames.
- `turn/started`: record `turn.id` when available and emit progress.
- `turn/completed`: mark the run complete; inspect common status fields defensively.
- `thread/started`, `item/started`, `item/completed`, and other known progress notifications: emit log frames but do not affect final output.
- `codex/event` legacy notifications: parse only if encountered; support agent-message text defensively, but do not rely on it.

Supported server requests:

- `item/commandExecution/requestApproval` and `execCommandApproval`: respond `{decision:"accept"}`.
- `item/fileChange/requestApproval` and `applyPatchApproval`: respond `{decision:"accept"}`.
- `item/permissions/requestApproval`: grant recognized `network` and `fileSystem` permissions with `scope:"turn"`.
- `mcpServer/elicitation/request`: respond with an accept/no-content shape.

Unknown server requests receive JSON-RPC error `-32601` and mark the turn failed. This is intentionally fail-closed: a new approval shape should be visible rather than silently denied or accepted incorrectly.

## Error Handling

Failure cases return `null` to preserve existing caller fallback behavior unless the current caller already treats failed runner output differently.

Handled failures:

- app-server process fails to start.
- `initialize`, `thread/start`, or `turn/start` returns JSON-RPC error.
- stdout line is invalid JSON: emit a stderr/system log and ignore the line.
- app-server exits before `turn/completed`.
- `turn/completed` contains a failed status or error-like field.
- interrupted Java thread: restore interrupt flag and mark run failed.

Process cleanup closes stdin and waits for process exit. A small bounded wait should be used so a stuck app-server does not block the `CompletableFuture` indefinitely.

## Claude MCP

`ClaudeRunner` already receives `mcpConfig`. This pass will materialize nonblank MCP config into a temp JSON file and add:

```text
--mcp-config <temp-file>
--strict-mcp-config
```

The temp file keeps secrets out of process argv and logs. The spawn log should redact or omit the exact temp path content; it may show the file path but never inline JSON.

Invalid MCP JSON fails softly for parity with current runner behavior: log a warning, omit the flag, and let the task continue. Tests will pin the intended behavior before implementation.

## Runtime Brief

`AgentBriefWriter` keeps existing file mapping:

- Codex writes `AGENTS.md`.
- Claude writes `CLAUDE.md`.
- Unknown tools skip.

The managed block will include:

- `# SEI Agent Runtime`
- `## Agent Identity`
- agent name and instructions
- `## Runtime Context`
- selected CLI tool, model if known, and MCP availability if configured

Because current `writeBrief` receives only name/instructions, this pass will add an overload that accepts runtime context while preserving the existing signature for older tests/callers. Services that already have the `Agent` will call the richer overload. Marker-block replacement semantics remain unchanged.

## Testing

Backend tests:

- `CodexAppServerClientTest`: response matching, JSON-RPC errors, invalid JSON, unknown server request fail-closed, known approvals auto-accepted.
- `CodexRunnerFakeExecutableTest`: fake app-server script validates request sequence and emits deltas plus `turn/completed`; runner returns aggregated text.
- `CodexRunnerFakeExecutableTest`: fake approval request path verifies SEI responds and continues.
- `CodexRunnerFakeExecutableTest`: app-server exits before completion returns `null`.
- `CodexRunnerTest`: build args now expect `app-server --listen stdio://`, not `exec --json -o`.
- `ClaudeRunnerTest`: nonblank MCP config writes temp file and adds `--mcp-config`; blank config does not.
- `AgentBriefWriterTest`: richer runtime context is included and idempotent.

Verification commands:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain \
  --tests "*CodexRunnerTest" \
  --tests "*CodexRunnerFakeExecutableTest" \
  --tests "*CodexAppServerClientTest" \
  --tests "*ClaudeRunnerTest" \
  --tests "*AgentBriefWriterTest"
./gradlew :sei-online-code-service:compileTestJava --console=plain
```

Real e2e:

```bash
codex app-server --listen stdio://
```

and the existing real-runner test should remain gated. On this host, OpenAI access may still fail with `403 unsupported_country_region_territory`; that is an environment limitation, not a Java wiring failure.

## Migration Plan

This is an internal runner replacement. No database migration is needed.

Runtime impact:

- Existing Codex agents keep using `cliTool=codex`.
- Existing fallback behavior remains: `null` runner output lets services use their current fallback paths.
- Existing MCP config remains stored in `Agent.mcpConfig`.
- Existing per-run `CODEX_HOME` isolation remains.

Rollback:

- The app-server implementation can keep `codex exec` argument construction in git history only. If a production rollback is needed, revert the runner commit.
- No persisted state shape changes are introduced.

## Acceptance Criteria

- Codex runner spawn command is `codex app-server --listen stdio://`.
- Fake app-server test proves request order: `initialize`, `initialized`, `thread/start`, `turn/start`.
- Fake app-server test proves final output comes from `item/agentMessage/delta`.
- Known app-server approval requests are answered automatically.
- Unknown app-server requests fail closed.
- Existing Codex sandbox/MCP/home/skills setup still executes before spawn.
- Claude MCP config is passed through `--mcp-config` temp file.
- Runtime brief includes SEI runtime context and stays idempotent.
- Focused backend tests and `compileTestJava` pass.
- `docs/plan/MULTI-CLI-RUNNER.md` reflects completion and residual real-network verification.
