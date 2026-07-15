package com.changhong.onlinecode.agent;

import com.fasterxml.jackson.databind.JsonNode;

final class CodexAppServerEvents {

    private final StringBuilder output = new StringBuilder();
    private boolean turnDone;
    private boolean failed;
    private String failureReason;
    private String turnId;
    private AgentUsage latestUsage;

    synchronized void handleNotification(String method, JsonNode params) {
        if ("item/agentMessage/delta".equals(method)) {
            output.append(params.path("delta").asText(""));
            return;
        }
        if ("turn/started".equals(method)) {
            turnId = params.path("turn").path("id").asText(turnId);
            return;
        }
        if ("thread/tokenUsage/updated".equals(method)) {
            updateUsage(params);
            return;
        }
        if ("turn/completed".equals(method)) {
            turnDone = true;
            JsonNode turn = params.path("turn");
            turnId = turn.path("id").asText(turnId);
            String status = turn.path("status").asText("");
            if (!status.isBlank() && !"completed".equals(status)) {
                failed = true;
                failureReason = firstNonBlank(
                        turn.path("error").path("message").asText(null),
                        turn.path("error").asText(null),
                        "codex turn completed with status=" + status);
            }
        }
    }

    private void updateUsage(JsonNode params) {
        JsonNode tokenUsage = params.path("tokenUsage");
        if (tokenUsage.isMissingNode() || !tokenUsage.isObject()) {
            return;
        }
        JsonNode total = tokenUsage.path("total");
        if (total.isMissingNode() || !total.isObject()) {
            return;
        }
        // 当前每个 Run 新建 thread 且只执行一个 turn，固定读取 tokenUsage.total。
        AgentUsage usage = new AgentUsage();
        usage.setInputTokens(readLong(total.path("inputTokens")));
        usage.setOutputTokens(readLong(total.path("outputTokens")));
        usage.setCacheReadTokens(readLong(total.path("cachedInputTokens")));
        // Codex 当前不提供 cache-write 指标。
        usage.setCacheWriteTokens(null);
        usage.setTotalTokens(readLong(total.path("totalTokens")));
        usage.setRawUsageJson(total.toString());
        this.latestUsage = usage;
    }

    private static Long readLong(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            long value = node.asLong();
            return value >= 0 ? value : null;
        }
        return null;
    }

    synchronized boolean isTurnDone() {
        return turnDone;
    }

    synchronized boolean isFailed() {
        return failed;
    }

    synchronized String failureReason() {
        return failureReason;
    }

    synchronized String output() {
        return output.toString();
    }

    synchronized String turnId() {
        return turnId;
    }

    synchronized AgentUsage latestUsage() {
        return latestUsage;
    }

    synchronized void markFailed(String reason) {
        turnDone = true;
        failed = true;
        failureReason = reason;
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
