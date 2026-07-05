package com.changhong.onlinecode.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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

    @Test
    void events_interruptedTurnFallsBackToStatusReason() throws Exception {
        CodexAppServerEvents events = new CodexAppServerEvents();

        events.handleNotification("turn/completed",
                objectMapper.readTree("{\"threadId\":\"t\",\"turn\":{\"id\":\"u\",\"status\":\"interrupted\"}}"));

        assertTrue(events.isTurnDone());
        assertTrue(events.isFailed());
        assertEquals("codex turn completed with status=interrupted", events.failureReason());
    }
}
