package com.changhong.onlinecode.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void events_interruptedTurnFallsBackToStatusReason() throws Exception {
        CodexAppServerEvents events = new CodexAppServerEvents();

        events.handleNotification("turn/completed",
                objectMapper.readTree("{\"threadId\":\"t\",\"turn\":{\"id\":\"u\",\"status\":\"interrupted\"}}"));

        assertTrue(events.isTurnDone());
        assertTrue(events.isFailed());
        assertEquals("codex turn completed with status=interrupted", events.failureReason());
    }

    @Test
    void client_matchesResponseToPendingRequest() throws Exception {
        java.io.ByteArrayOutputStream stdin = new java.io.ByteArrayOutputStream();
        CodexAppServerClient client = new CodexAppServerClient(stdin, line -> {});

        CompletableFuture<JsonNode> future = client.request("thread/start", Map.of("model", "gpt-5-codex"));

        JsonNode outbound = readSingleOutboundJson(stdin);
        assertEquals("2.0", outbound.path("jsonrpc").asText());
        assertEquals(1, outbound.path("id").asInt());
        assertEquals("thread/start", outbound.path("method").asText());
        assertEquals("gpt-5-codex", outbound.path("params").path("model").asText());

        client.handleLine("{\"id\":1,\"result\":{\"thread\":{\"id\":\"thr_1\"}}}");

        assertEquals("thr_1", future.get(5, TimeUnit.SECONDS).path("thread").path("id").asText());
    }

    @Test
    void client_jsonRpcErrorResponseCompletesPendingRequestExceptionally() throws Exception {
        java.io.ByteArrayOutputStream stdin = new java.io.ByteArrayOutputStream();
        CodexAppServerClient client = new CodexAppServerClient(stdin, line -> {});

        CompletableFuture<JsonNode> future = client.request("thread/start", Map.of());

        client.handleLine("{\"id\":1,\"error\":{\"code\":-32000,\"message\":\"boom\"}}");

        ExecutionException error = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertEquals("boom (code=-32000)", error.getCause().getMessage());
    }

    @Test
    void client_autoApprovesKnownServerRequest() throws Exception {
        java.io.ByteArrayOutputStream stdin = new java.io.ByteArrayOutputStream();
        CodexAppServerClient client = new CodexAppServerClient(stdin, line -> {});

        client.handleLine("{\"id\":7,\"method\":\"item/commandExecution/requestApproval\",\"params\":{}}");

        JsonNode outbound = readSingleOutboundJson(stdin);
        assertEquals("2.0", outbound.path("jsonrpc").asText());
        assertEquals(7, outbound.path("id").asInt());
        assertEquals("accept", outbound.path("result").path("decision").asText());
    }

    @Test
    void client_unknownServerRequestFailsClosed() throws Exception {
        java.io.ByteArrayOutputStream stdin = new java.io.ByteArrayOutputStream();
        CodexAppServerEvents events = new CodexAppServerEvents();
        CodexAppServerClient client = new CodexAppServerClient(stdin, line -> {}, events);

        client.handleLine("{\"id\":9,\"method\":\"new/approval/shape\",\"params\":{}}");

        JsonNode outbound = readSingleOutboundJson(stdin);
        assertEquals("2.0", outbound.path("jsonrpc").asText());
        assertEquals(9, outbound.path("id").asInt());
        assertEquals(-32601, outbound.path("error").path("code").asInt());
        assertEquals("unsupported codex app-server request: new/approval/shape",
                outbound.path("error").path("message").asText());
        assertTrue(events.isFailed());
    }

    @Test
    void client_requestWriteFailureRemovesPendingRequest() {
        CodexAppServerClient client = new CodexAppServerClient(new FailingOutputStream(), line -> {});

        IOException error = assertThrows(IOException.class, () -> client.request("thread/start", Map.of()));

        assertEquals("write failed", error.getMessage());
        assertEquals(0, client.pendingCount());
    }

    private JsonNode readSingleOutboundJson(java.io.ByteArrayOutputStream stdin) throws IOException {
        String outbound = stdin.toString(StandardCharsets.UTF_8).trim();
        return objectMapper.readTree(outbound);
    }

    private static final class FailingOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            throw new IOException("write failed");
        }
    }
}
