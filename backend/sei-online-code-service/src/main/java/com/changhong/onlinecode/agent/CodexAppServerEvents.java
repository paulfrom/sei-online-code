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
            if (!status.isBlank() && !"completed".equals(status)) {
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
