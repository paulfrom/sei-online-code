package com.changhong.onlinecode.service.evidence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunLogRedactorTest {

    private final RunLogRedactor redactor = new RunLogRedactor();

    @Test
    void redact_masksBearerTokensAssignmentsAndJsonSecrets() {
        String value = """
                Authorization: Bearer glpat-secret-value
                PRIVATE-TOKEN=private-token-value
                {"apiKey":"json-secret","safe":"visible"}
                """;

        String redacted = redactor.redact(value);

        assertFalse(redacted.contains("glpat-secret-value"));
        assertFalse(redacted.contains("private-token-value"));
        assertFalse(redacted.contains("json-secret"));
        assertTrue(redacted.contains("[REDACTED]"));
        assertTrue(redacted.contains("\"safe\":\"visible\""));
    }
}
