package com.changhong.onlinecode.service.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于 {@link ProcessBuilder} 的验证命令执行器默认实现。
 */
@Component
public class ProcessValidationCommandExecutor implements ValidationCommandExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessValidationCommandExecutor.class);
    private static final long DEFAULT_TIMEOUT_MINUTES = 30;
    private static final int MAX_OUTPUT_CHARS = 8192;

    @Override
    public ValidationResult execute(Path workingDir, String command) {
        if (command == null || command.isBlank()) {
            return new ValidationResult(1, "", "validation command is empty", Duration.ZERO);
        }
        Instant start = Instant.now();
        try {
            List<String> tokens = tokenize(command);
            ProcessBuilder pb = new ProcessBuilder(tokens);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return new ValidationResult(-1, "", "validation command timed out after "
                        + DEFAULT_TIMEOUT_MINUTES + " minutes", Duration.between(start, Instant.now()));
            }
            String combined = readString(process.getInputStream());
            combined = truncate(combined);
            return new ValidationResult(process.exitValue(), combined, "",
                    Duration.between(start, Instant.now()));
        } catch (Exception e) {
            LOGGER.warn("validation command failed: {}", command, e);
            return new ValidationResult(1, "", truncate(e.getMessage()),
                    Duration.between(start, Instant.now()));
        }
    }

    private static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : command.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String readString(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "";
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= MAX_OUTPUT_CHARS) {
            return s;
        }
        return s.substring(0, MAX_OUTPUT_CHARS) + "\n...[truncated]";
    }
}
