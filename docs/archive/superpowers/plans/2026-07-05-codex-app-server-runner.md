# Codex App-Server Runner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace SEI's Codex runner with `codex app-server --listen stdio://`, complete Claude MCP wiring, enrich runtime brief context, and update the roadmap.

**Architecture:** Keep `CliRunner` and service call sites stable. `CodexRunner` remains the Spring bean and delegates JSON-RPC line handling to package-private app-server helper classes. Tests use fake shell executables to simulate Codex/Claude CLIs so the core wiring is verified without OpenAI network access.

**Tech Stack:** Java 17, Spring Boot service module, Jackson `ObjectMapper`, JUnit 5, Gradle, Bash fake executables.

---

## File Structure

- Modify: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/CodexRunner.java`
  - Replace `codex exec --json -o` lifecycle with `codex app-server --listen stdio://`.
  - Keep per-run `CODEX_HOME` setup and `stripFences`.
- Create: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/CodexAppServerClient.java`
  - JSON-RPC request/response matching, notification handling, server request responses, terminal turn tracking.
- Create: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/CodexAppServerEvents.java`
  - Accumulate output, status, and failure reason from app-server notifications.
- Modify: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/ClaudeRunner.java`
  - Materialize `mcpConfig` into a temp file and pass `--mcp-config <file> --strict-mcp-config`.
- Modify: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/AgentBriefWriter.java`
  - Add richer overload with runtime context while preserving existing signature.
- Modify: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/PlanAgentService.java`
  - Call richer brief overload.
- Modify: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/DispatchService.java`
  - Call richer brief overload.
- Modify: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/FeatureDesignBuildService.java`
  - Call richer brief overload.
- Modify: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexRunnerTest.java`
  - Update args assertions for app-server.
- Modify: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexRunnerFakeExecutableTest.java`
  - Replace `-o` fake behavior with JSONL app-server behavior.
- Create: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexAppServerClientTest.java`
  - Unit-test JSON-RPC line handling and approval responses.
- Create: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/ClaudeRunnerTest.java`
  - Unit-test/build-test Claude MCP flags through a fake executable.
- Modify: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/AgentBriefWriterTest.java`
  - Add runtime context assertions.
- Modify: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexRunnerRealCodexTest.java`
  - Update comments/expectations for app-server.
- Modify: `docs/plan/MULTI-CLI-RUNNER.md`
  - Mark app-server, Claude MCP, and brief enrichment as complete; leave only real-network verification and optional third vendor.

---

### Task 1: Add App-Server Event Accumulator

**Files:**
- Create: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/CodexAppServerEvents.java`
- Create: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexAppServerClientTest.java`

- [ ] **Step 1: Write failing tests for event accumulation**

Add these tests to `CodexAppServerClientTest`:

```java
package com.changhong.onlinecode.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexAppServerClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void events_appendAgentMessageDeltaUntilTurnCompleted() throws Exception {
        CodexAppServerEvents events = new CodexAppServerEvents();

        events.handleNotification("item/agentMessage/delta",
                objectMapper.readTree("{\"delta\":\"PO\",\"threadId\":\"t\",\"turnId\":\"u\",\"itemId\":\"i\"}"));
        events.handleNotification("item/agentMessage/delta",
                objectMapper.readTree("{\"delta\":\"NG\",\"threadId\":\"t\",\"turnId\":\"u\",\"itemId\":\"i\"}"));
        events.handleNotification("turn/completed",
                objectMapper.readTree("{\"threadId\":\"t\",\"turn\":{\"id\":\"u\",\"status\":\"completed\"}}"));

        assertTrue(events.isTurnDone());
        assertFalse(events.isFailed());
        assertEquals("PONG", events.output());
    }

    @Test
    void events_failedTurnCapturesReason() throws Exception {
        CodexAppServerEvents events = new CodexAppServerEvents();

        events.handleNotification("turn/completed",
                objectMapper.readTree("{\"threadId\":\"t\",\"turn\":{\"id\":\"u\",\"status\":\"failed\",\"error\":{\"message\":\"blocked\"}}}"));

        assertTrue(events.isTurnDone());
        assertTrue(events.isFailed());
        assertEquals("blocked", events.failureReason());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain --tests "*CodexAppServerClientTest"
```

Expected: compilation fails because `CodexAppServerEvents` does not exist.

- [ ] **Step 3: Implement minimal accumulator**

Create `CodexAppServerEvents.java`:

```java
package com.changhong.onlinecode.agent;

import com.fasterxml.jackson.databind.JsonNode;

final class CodexAppServerEvents {

    private final StringBuilder output = new StringBuilder();
    private boolean turnDone;
    private boolean failed;
    private String failureReason;
    private String turnId;

    void handleNotification(String method, JsonNode params) {
        if ("item/agentMessage/delta".equals(method)) {
            output.append(params.path("delta").asText(""));
            return;
        }
        if ("turn/started".equals(method)) {
            turnId = params.path("turn").path("id").asText(turnId);
            return;
        }
        if ("turn/completed".equals(method)) {
            turnDone = true;
            JsonNode turn = params.path("turn");
            turnId = turn.path("id").asText(turnId);
            String status = turn.path("status").asText("");
            if ("failed".equals(status) || "errored".equals(status) || "cancelled".equals(status)) {
                failed = true;
                failureReason = firstNonBlank(
                        turn.path("error").path("message").asText(null),
                        turn.path("error").asText(null),
                        "codex turn completed with status=" + status);
            }
        }
    }

    boolean isTurnDone() {
        return turnDone;
    }

    boolean isFailed() {
        return failed;
    }

    String failureReason() {
        return failureReason;
    }

    String output() {
        return output.toString();
    }

    String turnId() {
        return turnId;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain --tests "*CodexAppServerClientTest"
```

Expected: tests pass.

---

### Task 2: Add JSON-RPC App-Server Client

**Files:**
- Create: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/CodexAppServerClient.java`
- Modify: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexAppServerClientTest.java`

- [ ] **Step 1: Write failing tests for request/response and approvals**

Append tests:

```java
    @Test
    void client_matchesResponseToPendingRequest() throws Exception {
        java.io.ByteArrayOutputStream stdin = new java.io.ByteArrayOutputStream();
        CodexAppServerClient client = new CodexAppServerClient(stdin, line -> {});

        java.util.concurrent.CompletableFuture<com.fasterxml.jackson.databind.JsonNode> future =
                client.request("thread/start", Map.of("model", "gpt-5-codex"));

        String outbound = stdin.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(outbound.contains("\"method\":\"thread/start\""));
        assertTrue(outbound.contains("\"id\":1"));

        client.handleLine("{\"id\":1,\"result\":{\"thread\":{\"id\":\"thr_1\"}}}");

        assertEquals("thr_1", future.get(5, java.util.concurrent.TimeUnit.SECONDS)
                .path("thread").path("id").asText());
    }

    @Test
    void client_autoApprovesKnownServerRequest() throws Exception {
        java.io.ByteArrayOutputStream stdin = new java.io.ByteArrayOutputStream();
        CodexAppServerClient client = new CodexAppServerClient(stdin, line -> {});

        client.handleLine("{\"id\":7,\"method\":\"item/commandExecution/requestApproval\",\"params\":{}}");

        String outbound = stdin.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(outbound.contains("\"id\":7"));
        assertTrue(outbound.contains("\"decision\":\"accept\""));
    }

    @Test
    void client_unknownServerRequestFailsClosed() throws Exception {
        java.io.ByteArrayOutputStream stdin = new java.io.ByteArrayOutputStream();
        CodexAppServerEvents events = new CodexAppServerEvents();
        CodexAppServerClient client = new CodexAppServerClient(stdin, line -> {}, events);

        client.handleLine("{\"id\":9,\"method\":\"new/approval/shape\",\"params\":{}}");

        String outbound = stdin.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(outbound.contains("\"code\":-32601"));
        assertTrue(events.isFailed());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain --tests "*CodexAppServerClientTest"
```

Expected: compilation fails because `CodexAppServerClient` does not exist.

- [ ] **Step 3: Implement minimal client**

Create `CodexAppServerClient.java` with these methods:

```java
package com.changhong.onlinecode.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class CodexAppServerClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OutputStream stdin;
    private final Consumer<String> logLine;
    private final CodexAppServerEvents events;
    private final AtomicInteger nextId = new AtomicInteger();
    private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();

    CodexAppServerClient(OutputStream stdin, Consumer<String> logLine) {
        this(stdin, logLine, new CodexAppServerEvents());
    }

    CodexAppServerClient(OutputStream stdin, Consumer<String> logLine, CodexAppServerEvents events) {
        this.stdin = stdin;
        this.logLine = logLine;
        this.events = events;
    }

    CompletableFuture<JsonNode> request(String method, Object params) throws IOException {
        int id = nextId.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);

        ObjectNode msg = OBJECT_MAPPER.createObjectNode();
        msg.put("jsonrpc", "2.0");
        msg.put("id", id);
        msg.put("method", method);
        msg.set("params", OBJECT_MAPPER.valueToTree(params == null ? Map.of() : params));
        write(msg);
        return future;
    }

    void notify(String method) throws IOException {
        ObjectNode msg = OBJECT_MAPPER.createObjectNode();
        msg.put("jsonrpc", "2.0");
        msg.put("method", method);
        msg.set("params", OBJECT_MAPPER.createObjectNode());
        write(msg);
    }

    void handleLine(String line) {
        JsonNode raw;
        try {
            raw = OBJECT_MAPPER.readTree(line);
        } catch (Exception e) {
            logLine.accept("invalid codex app-server JSON: " + line);
            return;
        }
        if (raw.has("id") && (raw.has("result") || raw.has("error"))) {
            handleResponse(raw);
            return;
        }
        if (raw.has("id") && raw.has("method")) {
            handleServerRequest(raw);
            return;
        }
        if (raw.has("method")) {
            String method = raw.path("method").asText();
            events.handleNotification(method, raw.path("params"));
            logLine.accept(method + " " + raw.path("params"));
        }
    }

    CodexAppServerEvents events() {
        return events;
    }

    private void handleResponse(JsonNode raw) {
        int id = raw.path("id").asInt();
        CompletableFuture<JsonNode> future = pending.remove(id);
        if (future == null) {
            return;
        }
        if (raw.has("error")) {
            JsonNode error = raw.path("error");
            future.completeExceptionally(new IllegalStateException(
                    error.path("message").asText("codex app-server error") + " (code=" + error.path("code").asInt() + ")"));
        } else {
            future.complete(raw.path("result"));
        }
    }

    private void handleServerRequest(JsonNode raw) {
        int id = raw.path("id").asInt();
        String method = raw.path("method").asText();
        try {
            switch (method) {
                case "item/commandExecution/requestApproval":
                case "execCommandApproval":
                case "item/fileChange/requestApproval":
                case "applyPatchApproval":
                    respond(id, Map.of("decision", "accept"));
                    return;
                case "item/permissions/requestApproval":
                    respond(id, Map.of("permissions", raw.path("params").path("permissions"), "scope", "turn"));
                    return;
                case "mcpServer/elicitation/request":
                    respond(id, Map.of("action", "accept", "content", null, "_meta", null));
                    return;
                default:
                    events.markFailed("unsupported codex app-server request: " + method);
                    respondError(id, -32601, "unsupported codex app-server request: " + method);
            }
        } catch (IOException e) {
            events.markFailed("failed to respond to codex app-server request: " + e.getMessage());
        }
    }

    private void respond(int id, Object result) throws IOException {
        ObjectNode msg = OBJECT_MAPPER.createObjectNode();
        msg.put("jsonrpc", "2.0");
        msg.put("id", id);
        msg.set("result", OBJECT_MAPPER.valueToTree(result));
        write(msg);
    }

    private void respondError(int id, int code, String message) throws IOException {
        ObjectNode err = OBJECT_MAPPER.createObjectNode();
        err.put("code", code);
        err.put("message", message);
        ObjectNode msg = OBJECT_MAPPER.createObjectNode();
        msg.put("jsonrpc", "2.0");
        msg.put("id", id);
        msg.set("error", err);
        write(msg);
    }

    private synchronized void write(JsonNode msg) throws IOException {
        stdin.write(OBJECT_MAPPER.writeValueAsBytes(msg));
        stdin.write('\n');
        stdin.flush();
    }
}
```

Add to `CodexAppServerEvents`:

```java
    void markFailed(String reason) {
        turnDone = true;
        failed = true;
        failureReason = reason;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain --tests "*CodexAppServerClientTest"
```

Expected: tests pass.

---

### Task 3: Switch CodexRunner Args and Lifecycle to App-Server

**Files:**
- Modify: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexRunnerTest.java`
- Modify: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/CodexRunner.java`

- [ ] **Step 1: Rewrite failing args tests**

Replace `buildArgs_*` tests in `CodexRunnerTest` with:

```java
    @Test
    void buildArgs_usesAppServerStdio() {
        CodexRunner runner = new CodexRunner();

        List<String> args = runner.buildArgs();

        assertEquals(List.of("codex", "app-server", "--listen", "stdio://"), args);
        assertFalse(args.contains("exec"));
        assertFalse(args.contains("--json"));
        assertFalse(args.contains("-o"));
    }
```

Keep `readResultFile_*` tests only if `stripFences` remains package-visible through a renamed helper. Otherwise replace them with a `stripFences` package-visible test.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain --tests "*CodexRunnerTest"
```

Expected: args test fails because current runner still uses `exec --json -o`.

- [ ] **Step 3: Implement `buildArgs()` and app-server flow**

In `CodexRunner.java`:

- Change `buildArgs(String prompt, String model, Path outputFile)` to `List<String> buildArgs()`.
- Return `[executable, "app-server", "--listen", "stdio://"]`.
- Remove output file creation and `readResultFile` from production path.
- Keep `stripFences(String)` for final app-server output.

Add lifecycle helper inside `runBlocking`:

```java
List<String> args = buildArgs();
ProcessBuilder pb = new ProcessBuilder(args);
...
Process process = pb.start();
Thread stderrPump = pumpStderr(...);
stderrPump.start();
CodexAppServerEvents events = new CodexAppServerEvents();
CodexAppServerClient client = new CodexAppServerClient(
        process.getOutputStream(),
        line -> emit(iterationId, taskId, runId, "stdout", line, null),
        events);
Thread stdoutPump = pumpAppServerStdout(iterationId, taskId, runId, process.getInputStream(), client);
stdoutPump.start();

client.request("initialize", Map.of(
        "clientInfo", Map.of("name", "sei-online-code", "title", "SEI Online Code", "version", "0.1.0"),
        "capabilities", Map.of("experimentalApi", true))).get(30, TimeUnit.SECONDS);
client.notify("initialized");
JsonNode threadResult = client.request("thread/start", threadStartParams(cwd, model)).get(30, TimeUnit.SECONDS);
String threadId = threadResult.path("thread").path("id").asText();
client.request("turn/start", Map.of(
        "threadId", threadId,
        "input", List.of(Map.of("type", "text", "text", prompt)))).get(30, TimeUnit.SECONDS);
waitForTurn(events, process, Duration.ofMinutes(30));
```

Use `CompletableFuture.supplyAsync` as today; add imports for `JsonNode`, `Map`, `Duration`, `TimeUnit`, and `TimeoutException`.

- [ ] **Step 4: Run focused test**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain --tests "*CodexRunnerTest"
```

Expected: args and strip tests pass.

---

### Task 4: Rewrite Fake Codex Integration Tests for App-Server

**Files:**
- Modify: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexRunnerFakeExecutableTest.java`

- [ ] **Step 1: Replace old `-o` fake tests with app-server fake tests**

Replace tests with these behaviors:

```java
    @Test
    void execute_returnsAggregatedAgentMessageDeltas() throws Exception {
        CodexRunner runner = new CodexRunner(fakeAppServerHappy().toString());

        String result = runner.execute("fake-it", "do something", workdir.toString(), "gpt-5-codex", null)
                .get(60, TimeUnit.SECONDS);

        assertEquals("PONG", result);
    }

    @Test
    void execute_autoApprovesCommandRequestAndContinues() throws Exception {
        CodexRunner runner = new CodexRunner(fakeAppServerApproval().toString());

        String result = runner.execute("fake-it", "do something", workdir.toString(), null, null)
                .get(60, TimeUnit.SECONDS);

        assertEquals("APPROVED", result);
    }

    @Test
    void execute_processExitsBeforeTurnCompletedReturnsNull() throws Exception {
        CodexRunner runner = new CodexRunner(fakeAppServerExitsEarly().toString());

        assertNull(runner.execute("fake-it", "p", workdir.toString(), null, null)
                .get(60, TimeUnit.SECONDS));
    }

    @Test
    void execute_mcpBlockWrittenBeforeAppServerStarts() throws Exception {
        CodexRunner runner = new CodexRunner(fakeAppServerSnapshotConfig().toString());
        String mcpConfig = "{\"mcpServers\":{\"fetch\":{\"command\":\"uvx\",\"args\":[\"mcp-server-fetch\"]}}}";

        String result = runner.execute("fake-it", "p", workdir.toString(), null, mcpConfig)
                .get(60, TimeUnit.SECONDS);

        assertTrue(result.contains("[mcp_servers.fetch]"), result);
        assertTrue(result.contains("command = \"uvx\""), result);
    }
```

- [ ] **Step 2: Add fake app-server scripts**

Implement Bash scripts that read JSONL from stdin and respond by method substring:

```bash
#!/usr/bin/env bash
while IFS= read -r line; do
  if [[ "$line" == *'"method":"initialize"'* ]]; then
    printf '{"id":1,"result":{"userAgent":"fake","platformFamily":"linux","platformOs":"linux"}}\n'
  elif [[ "$line" == *'"method":"thread/start"'* ]]; then
    printf '{"id":2,"result":{"thread":{"id":"thr_fake"}}}\n'
  elif [[ "$line" == *'"method":"turn/start"'* ]]; then
    printf '{"id":3,"result":{"turn":{"id":"turn_fake"}}}\n'
    printf '{"method":"item/agentMessage/delta","params":{"threadId":"thr_fake","turnId":"turn_fake","itemId":"i","delta":"PO"}}\n'
    printf '{"method":"item/agentMessage/delta","params":{"threadId":"thr_fake","turnId":"turn_fake","itemId":"i","delta":"NG"}}\n'
    printf '{"method":"turn/completed","params":{"threadId":"thr_fake","turn":{"id":"turn_fake","status":"completed"}}}\n'
    exit 0
  fi
done
```

Use exact request IDs if the Java client sends initialize=1, thread/start=2, turn/start=3. For approval script, emit a server request with `id:77` before deltas, then wait for the Java response containing `"id":77`.

- [ ] **Step 3: Run test to verify it fails, then fix runner timing**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain --tests "*CodexRunnerFakeExecutableTest"
```

Expected first failure: runner does not yet wait correctly for `turn/completed` or fake script IDs. Fix `waitForTurn` and stdout pump until tests pass.

- [ ] **Step 4: Run fake tests green**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain --tests "*CodexRunnerFakeExecutableTest"
```

Expected: all fake app-server tests pass.

---

### Task 5: Wire Claude MCP Through Temp File

**Files:**
- Create: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/ClaudeRunnerTest.java`
- Modify: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/ClaudeRunner.java`

- [ ] **Step 1: Write failing fake-executable tests**

Create `ClaudeRunnerTest.java`:

```java
package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void execute_passesMcpConfigViaTempFile() throws Exception {
        Path fakeClaude = installScript("""
                #!/usr/bin/env bash
                mcp=""
                prev=""
                for a in "$@"; do
                  if [ "$prev" = "--mcp-config" ]; then mcp="$a"; fi
                  prev="$a"
                done
                content="$(cat "$mcp")"
                printf '{"result":"%s"}\\n' "$content"
                """, "fake-claude");

        ClaudeRunner runner = new ClaudeRunner(fakeClaude.toString());
        String result = runner.execute("it", "p", tempDir.toString(), null,
                "{\"mcpServers\":{\"fetch\":{\"command\":\"uvx\"}}}")
                .get(60, TimeUnit.SECONDS);

        assertTrue(result.contains("\"mcpServers\""));
        assertTrue(result.contains("\"fetch\""));
    }

    @Test
    void execute_blankMcpConfigDoesNotPassMcpFlag() throws Exception {
        Path fakeClaude = installScript("""
                #!/usr/bin/env bash
                printf '{"result":"%s"}\\n' "$*"
                """, "fake-claude-args");

        ClaudeRunner runner = new ClaudeRunner(fakeClaude.toString());
        String result = runner.execute("it", "p", tempDir.toString(), null, " ")
                .get(60, TimeUnit.SECONDS);

        assertEquals("-p p --output-format json", result);
    }

    private Path installScript(String content, String name) throws Exception {
        Path script = Files.createFile(tempDir.resolve(name + ".sh"));
        Files.writeString(script, content, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(script, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException e) {
            Assumptions.assumeTrue(false, "non-POSIX filesystem");
        }
        return script;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain --tests "*ClaudeRunnerTest"
```

Expected: compilation fails because `ClaudeRunner(String executable)` does not exist, or assertion fails because MCP flag is ignored.

- [ ] **Step 3: Implement constructor and MCP temp file**

In `ClaudeRunner.java`:

- Add package-private constructor `ClaudeRunner(String executable)`.
- Pass `mcpConfig` into `runBlocking`.
- Add temp file creation:

```java
Path mcpConfigFile = writeMcpConfigFile(mcpConfig);
List<String> args = buildArgs(prompt, model, mcpConfigFile);
...
finally {
    if (mcpConfigFile != null) {
        Files.deleteIfExists(mcpConfigFile);
    }
}
```

- Add flags in `buildArgs`:

```java
if (mcpConfigFile != null) {
    args.add("--mcp-config");
    args.add(mcpConfigFile.toString());
    args.add("--strict-mcp-config");
}
```

- Validate JSON before writing:

```java
private Path writeMcpConfigFile(String mcpConfig) throws IOException {
    if (mcpConfig == null || mcpConfig.isBlank()) {
        return null;
    }
    objectMapper.readTree(mcpConfig);
    Path file = Files.createTempFile("claude-mcp-", ".json");
    Files.writeString(file, mcpConfig, StandardCharsets.UTF_8);
    return file;
}
```

- If JSON validation fails, log warning and return null.

- [ ] **Step 4: Run Claude tests green**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain --tests "*ClaudeRunnerTest"
```

Expected: tests pass.

---

### Task 6: Enrich Runtime Brief

**Files:**
- Modify: `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/AgentBriefWriter.java`
- Modify: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/AgentBriefWriterTest.java`
- Modify service call sites using graph/search if line numbers drift:
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/PlanAgentService.java`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/DispatchService.java`
  - `backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/FeatureDesignBuildService.java`

- [ ] **Step 1: Write failing brief context test**

Append:

```java
    @Test
    void writeBrief_richOverloadIncludesRuntimeContext() throws IOException {
        AgentBriefWriter.writeBrief(workDir.toString(), "codex", "builder",
                "Build safely.", "gpt-5-codex", true, null);

        String content = Files.readString(workDir.resolve("AGENTS.md"), StandardCharsets.UTF_8);
        assertTrue(content.contains("## Runtime Context"));
        assertTrue(content.contains("- CLI tool: codex"));
        assertTrue(content.contains("- Model: gpt-5-codex"));
        assertTrue(content.contains("- MCP config: configured"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain --tests "*AgentBriefWriterTest"
```

Expected: compilation fails because overload does not exist.

- [ ] **Step 3: Implement overload**

In `AgentBriefWriter.java`:

```java
public static void writeBrief(String workDir, String cliTool, String agentName,
                              String instructions, String model, boolean hasMcpConfig, Logger logger) {
    writeBriefInternal(workDir, cliTool, agentName, instructions, model, hasMcpConfig, logger);
}

public static void writeBrief(String workDir, String cliTool, String agentName,
                              String instructions, Logger logger) {
    writeBriefInternal(workDir, cliTool, agentName, instructions, null, false, logger);
}
```

Update `buildBrief` to accept runtime context:

```java
static String buildBrief(String agentName, String instructions, String cliTool, String model, boolean hasMcpConfig) {
    ...
    sb.append("## Runtime Context\n\n");
    sb.append("- CLI tool: ").append(tool).append('\n');
    if (model != null && !model.isBlank()) {
        sb.append("- Model: ").append(model.trim()).append('\n');
    }
    sb.append("- MCP config: ").append(hasMcpConfig ? "configured" : "not configured").append('\n');
}
```

Keep existing `buildBrief(String, String)` delegating to the richer version so old tests keep compiling.

- [ ] **Step 4: Update service call sites**

For each `AgentBriefWriter.writeBrief(...)` call, pass:

```java
agent.getModel()
agent.getMcpConfig() != null && !agent.getMcpConfig().isBlank()
```

Use null guards where `agent` can be null.

- [ ] **Step 5: Run brief and service tests**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain \
  --tests "*AgentBriefWriterTest" \
  --tests "*PlanAgentServiceTest"
```

Expected: tests pass.

---

### Task 7: Real Codex Test and Roadmap Update

**Files:**
- Modify: `backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexRunnerRealCodexTest.java`
- Modify: `docs/plan/MULTI-CLI-RUNNER.md`

- [ ] **Step 1: Update real Codex test comments and prompt expectations**

Keep the e2e gated by Codex availability. Update comments to say it verifies app-server handshake, thread/start, turn/start, deltas, and per-run `CODEX_HOME`.

Do not require this test in normal local verification because this host may return OpenAI `403 unsupported_country_region_territory`.

- [ ] **Step 2: Update roadmap**

In `docs/plan/MULTI-CLI-RUNNER.md`:

- Add a `PR4` section for app-server, Claude MCP, and brief enrichment.
- Strike through item #8.
- Mark Claude MCP and brief enrichment complete.
- Leave these residual follow-up items:
  - real Codex app-server e2e in an OpenAI-accessible environment
  - second non-Claude vendor, if product need appears

- [ ] **Step 3: Run docs status check**

Run:

```bash
git diff -- docs/plan/MULTI-CLI-RUNNER.md backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexRunnerRealCodexTest.java
```

Expected: diff reflects only roadmap/test-comment changes.

---

### Task 8: Full Verification and Commit

**Files:**
- All files changed in previous tasks.

- [ ] **Step 1: Run focused backend tests**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:test --console=plain \
  --tests "*CodexRunnerTest" \
  --tests "*CodexRunnerFakeExecutableTest" \
  --tests "*CodexAppServerClientTest" \
  --tests "*ClaudeRunnerTest" \
  --tests "*AgentBriefWriterTest" \
  --tests "*PlanAgentServiceTest"
```

Expected: all selected tests pass.

- [ ] **Step 2: Compile all tests**

Run:

```bash
cd backend
./gradlew :sei-online-code-service:compileTestJava --console=plain
```

Expected: compilation passes.

- [ ] **Step 3: Check worktree**

Run:

```bash
git status --short
git diff --stat
```

Expected: only planned files changed.

- [ ] **Step 4: Commit**

Run:

```bash
git add backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/CodexRunner.java \
  backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/CodexAppServerClient.java \
  backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/CodexAppServerEvents.java \
  backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/ClaudeRunner.java \
  backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/agent/AgentBriefWriter.java \
  backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/PlanAgentService.java \
  backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/DispatchService.java \
  backend/sei-online-code-service/src/main/java/com/changhong/onlinecode/service/FeatureDesignBuildService.java \
  backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexRunnerTest.java \
  backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexRunnerFakeExecutableTest.java \
  backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexAppServerClientTest.java \
  backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/ClaudeRunnerTest.java \
  backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/AgentBriefWriterTest.java \
  backend/sei-online-code-service/src/test/java/com/changhong/onlinecode/agent/CodexRunnerRealCodexTest.java \
  docs/plan/MULTI-CLI-RUNNER.md
git commit -m "feat: run codex through app-server protocol"
```

Expected: commit succeeds.

---

## Self-Review

- Spec coverage: app-server replacement is covered by Tasks 1-4; Claude MCP by Task 5; brief enrichment by Task 6; roadmap and residual verification by Task 7; final verification by Task 8.
- Red-flag scan: no task uses unspecified implementation gaps; each behavior has concrete tests and implementation shape.
- Type consistency: `CodexAppServerClient`, `CodexAppServerEvents`, and runner methods use package-private Java classes in `com.changhong.onlinecode.agent`; test names match planned files.
