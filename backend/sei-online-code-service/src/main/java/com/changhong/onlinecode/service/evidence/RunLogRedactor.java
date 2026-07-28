package com.changhong.onlinecode.service.evidence;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 在日志离开 Runner 边界前执行的最小强制脱敏。
 */
@Component
public class RunLogRedactor {

    private static final String MASK = "$1[REDACTED]";

    private final List<Pattern> assignmentPatterns = List.of(
            Pattern.compile("(?i)(authorization\\s*[:=]\\s*(?:bearer|basic)\\s+)[^\\s,;]+"),
            Pattern.compile("(?i)((?:private[-_]?token|access[-_]?token|api[-_]?key|secret|password)"
                    + "\\s*[:=]\\s*[\"']?)[^\\s,\"';}]+"),
            Pattern.compile("(?i)(\\\"(?:token|apiKey|api_key|password|secret)\\\"\\s*:\\s*\\\")[^\\\"]+"),
            Pattern.compile("(?i)(^|[^a-z0-9])(?:glpat|ghp|github_pat|sk)-[a-z0-9_\\-]{12,}")
    );

    public String redact(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String redacted = value;
        for (Pattern pattern : assignmentPatterns) {
            redacted = pattern.matcher(redacted).replaceAll(MASK);
        }
        return redacted;
    }
}
