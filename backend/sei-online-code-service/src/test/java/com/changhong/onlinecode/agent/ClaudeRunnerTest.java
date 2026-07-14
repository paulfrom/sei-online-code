package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void execute_passesMcpConfigViaTempFile() throws Exception {
        Path fakeClaude = installScript("""
                #!/usr/bin/env bash
                mcp=""
                strict=""
                prev=""
                for a in "$@"; do
                  if [ "$prev" = "--mcp-config" ]; then mcp="$a"; fi
                  if [ "$a" = "--strict-mcp-config" ]; then strict="yes"; fi
                  prev="$a"
                done
                if [ -z "$mcp" ] || [ "$strict" != "yes" ]; then
                  echo "missing mcp flags" >&2
                  exit 1
                fi
                if ! grep -q '"mcpServers"' "$mcp" || ! grep -q '"fetch"' "$mcp"; then
                  echo "mcp config content missing" >&2
                  exit 1
                fi
                printf '{"result":"mcpServers fetch"}\\n'
                """, "fake-claude");

        ClaudeRunner runner = new ClaudeRunner(fakeClaude.toString());
        String result = runner.executeDetailed("it", null, null, "p", tempDir.toString(), null,
                "{\"mcpServers\":{\"fetch\":{\"command\":\"uvx\"}}}")
                .get(60, TimeUnit.SECONDS).getOutput();

        assertTrue(result.contains("mcpServers"));
        assertTrue(result.contains("fetch"));
    }

    @Test
    void execute_blankMcpConfigDoesNotPassMcpFlag() throws Exception {
        Path fakeClaude = installScript("""
                #!/usr/bin/env bash
                printf '{"result":"%s"}\\n' "$*"
                """, "fake-claude-args");

        ClaudeRunner runner = new ClaudeRunner(fakeClaude.toString());
        String result = runner.executeDetailed("it", null, null, "p", tempDir.toString(), null, " ")
                .get(60, TimeUnit.SECONDS).getOutput();

        assertFalse(result.contains("--mcp-config"));
        assertFalse(result.contains("--strict-mcp-config"));
        assertTrue(result.contains("--add-dir"));
        assertTrue(result.contains("--permission-mode bypassPermissions"));
    }

    @Test
    void execute_normalizesClaudeUsageEnvelope() throws Exception {
        Path fakeClaude = installScript("""
                #!/usr/bin/env bash
                printf '{"result":"hello world","usage":{"input_tokens":80,"output_tokens":40,'
                printf '"cache_read_input_tokens":20,"cache_creation_input_tokens":10}}\\n'
                """, "fake-claude-usage");

        ClaudeRunner runner = new ClaudeRunner(fakeClaude.toString());
        CliRunResult runResult = runner.executeDetailed("it", null, null, "p", tempDir.toString(), null, null)
                .get(60, TimeUnit.SECONDS);

        assertTrue(runResult.isProcessSucceeded());
        assertEquals("hello world", runResult.getOutput());
        AgentUsage usage = runResult.getUsage();
        // input = input_tokens + cache_read + cache_creation
        assertEquals(110L, usage.getInputTokens());
        assertEquals(40L, usage.getOutputTokens());
        assertEquals(20L, usage.getCacheReadTokens());
        assertEquals(10L, usage.getCacheWriteTokens());
        // total = input + output，不重复计算缓存分项
        assertEquals(150L, usage.getTotalTokens());
        assertEquals(com.changhong.onlinecode.dto.enums.UsageStatus.COMPLETE, usage.getStatus());
    }

    @Test
    void execute_missingUsageMarksUnavailable() throws Exception {
        Path fakeClaude = installScript("""
                #!/usr/bin/env bash
                printf '{"result":"no usage here"}\\n'
                """, "fake-claude-no-usage");

        ClaudeRunner runner = new ClaudeRunner(fakeClaude.toString());
        CliRunResult runResult = runner.executeDetailed("it", null, null, "p", tempDir.toString(), null, null)
                .get(60, TimeUnit.SECONDS);

        assertTrue(runResult.isProcessSucceeded());
        assertEquals("no usage here", runResult.getOutput());
        assertEquals(com.changhong.onlinecode.dto.enums.UsageStatus.UNAVAILABLE, runResult.getUsage().getStatus());
        assertNull(runResult.getUsage().getInputTokens());
    }

    @Test
    void buildArgs_grantsWorkspaceAndEditToolsForNonInteractiveCoding() {
        ClaudeRunner runner = new ClaudeRunner("/tmp/fake-claude");

        List<String> args = runner.buildArgs("p", null, null, tempDir.toString());

        assertTrue(args.contains("--add-dir"));
        assertTrue(args.contains(tempDir.toAbsolutePath().normalize().toString()));
        assertTrue(args.contains("--permission-mode"));
        assertTrue(args.contains("bypassPermissions"));
        assertTrue(args.contains("--allowedTools"));
        String joined = String.join(" ", args);
        assertTrue(joined.contains("Edit"));
        assertTrue(joined.contains("MultiEdit"));
        assertTrue(joined.contains("Write"));
        assertTrue(joined.contains("Bash"));
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
