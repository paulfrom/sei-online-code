package com.changhong.onlinecode.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Codex app-server 的 thread/tokenUsage/updated 事件解析。
 *
 * <p>WHY：token 统计的核心在于从 codex 通知中正确提取归一化 token，
 * 且 cachedInputTokens 是 inputTokens 的分项，不得重复计入 totalTokens。</p>
 */
class CodexAppServerEventsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode params(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void singleTokenUsageUpdate_camelCaseParsed() throws Exception {
        CodexAppServerEvents events = new CodexAppServerEvents();
        events.handleNotification("thread/tokenUsage/updated", params(
                "{\"threadId\":\"thr_1\",\"turnId\":\"turn_1\","
                        + "\"tokenUsage\":{\"total\":{\"totalTokens\":100,\"inputTokens\":80,"
                        + "\"cachedInputTokens\":20,\"outputTokens\":20},\"last\":{}}}"));

        AgentUsage usage = events.latestUsage();
        assertEquals(80L, usage.getInputTokens());
        assertEquals(20L, usage.getOutputTokens());
        assertEquals(20L, usage.getCacheReadTokens());
        // Codex 当前不提供 cache-write 指标。
        assertNull(usage.getCacheWriteTokens());
        assertEquals(100L, usage.getTotalTokens());
        assertFalse(events.isTurnDone());
    }

    @Test
    void multipleUpdates_keepLatestAccumulated() throws Exception {
        CodexAppServerEvents events = new CodexAppServerEvents();
        events.handleNotification("thread/tokenUsage/updated", params(
                "{\"tokenUsage\":{\"total\":{\"totalTokens\":50,\"inputTokens\":40,"
                        + "\"cachedInputTokens\":10,\"outputTokens\":10}}}"));
        events.handleNotification("thread/tokenUsage/updated", params(
                "{\"tokenUsage\":{\"total\":{\"totalTokens\":120,\"inputTokens\":90,"
                        + "\"cachedInputTokens\":30,\"outputTokens\":30}}}"));

        AgentUsage usage = events.latestUsage();
        // 多次更新只保留最终累计值。
        assertEquals(120L, usage.getTotalTokens());
        assertEquals(90L, usage.getInputTokens());
    }

    @Test
    void turnCompleted_marksTurnDoneWithoutClearingUsage() throws Exception {
        CodexAppServerEvents events = new CodexAppServerEvents();
        events.handleNotification("thread/tokenUsage/updated", params(
                "{\"tokenUsage\":{\"total\":{\"totalTokens\":100,\"inputTokens\":80,"
                        + "\"cachedInputTokens\":20,\"outputTokens\":20}}}"));
        events.handleNotification("turn/completed", params(
                "{\"turn\":{\"id\":\"turn_1\",\"status\":\"completed\"}}"));

        assertTrue(events.isTurnDone());
        assertFalse(events.isFailed());
        // turn/completed 不得清空已取得的 usage。
        assertEquals(100L, events.latestUsage().getTotalTokens());
    }

    @Test
    void noUsage_leavesLatestUsageNull() {
        CodexAppServerEvents events = new CodexAppServerEvents();
        events.handleNotification("turn/completed", mapper.createObjectNode());

        assertNull(events.latestUsage());
    }

    @Test
    void failedTurn_setsFailedFlag() throws Exception {
        CodexAppServerEvents events = new CodexAppServerEvents();
        events.handleNotification("turn/completed", params(
                "{\"turn\":{\"id\":\"turn_1\",\"status\":\"failed\","
                        + "\"error\":{\"message\":\"boom\"}}}"));

        assertTrue(events.isFailed());
        assertTrue(events.failureReason().contains("boom"));
    }
}
