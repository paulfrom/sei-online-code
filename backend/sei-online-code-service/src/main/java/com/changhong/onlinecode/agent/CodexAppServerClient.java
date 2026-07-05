package com.changhong.onlinecode.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
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
    private Throwable terminalFailure;

    CodexAppServerClient(OutputStream stdin, Consumer<String> logLine) {
        this(stdin, logLine, new CodexAppServerEvents());
    }

    CodexAppServerClient(OutputStream stdin, Consumer<String> logLine, CodexAppServerEvents events) {
        this.stdin = stdin;
        this.logLine = logLine;
        this.events = events;
    }

    synchronized CompletableFuture<JsonNode> request(String method, Object params) throws IOException {
        if (terminalFailure != null) {
            throw asIOException(terminalFailure);
        }
        int id = nextId.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);

        ObjectNode msg = OBJECT_MAPPER.createObjectNode();
        msg.put("jsonrpc", "2.0");
        msg.put("id", id);
        msg.put("method", method);
        msg.set("params", OBJECT_MAPPER.valueToTree(params == null ? Map.of() : params));
        try {
            write(msg);
        } catch (IOException e) {
            pending.remove(id);
            future.completeExceptionally(e);
            throw e;
        }
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

    int pendingCount() {
        return pending.size();
    }

    synchronized void failPendingRequests(Throwable cause) {
        terminalFailure = cause;
        pending.forEach((id, future) -> future.completeExceptionally(cause));
        pending.clear();
    }

    private IOException asIOException(Throwable cause) {
        if (cause instanceof IOException io) {
            return io;
        }
        return new IOException(cause.getMessage(), cause);
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
                    error.path("message").asText("codex app-server error")
                            + " (code=" + error.path("code").asInt() + ")"));
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
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("action", "accept");
                    result.put("content", null);
                    result.put("_meta", null);
                    respond(id, result);
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
