package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link CodexRunner} 单元测试。
 */
class CodexRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void buildArgs_usesAppServerStdio() {
        CodexRunner runner = new CodexRunner();

        List<String> args = runner.buildArgs();

        assertEquals(List.of("codex", "app-server", "--listen", "stdio://"), args);
        assertFalse(args.contains("exec"));
        assertFalse(args.contains("--json"));
        assertFalse(args.contains("-o"));
    }

    @Test
    void buildArgs_usesCustomExecutable() {
        CodexRunner runner = new CodexRunner("/tmp/fake-codex");

        List<String> args = runner.buildArgs();

        assertEquals(List.of("/tmp/fake-codex", "app-server", "--listen", "stdio://"), args);
    }

    @Test
    void threadStartParams_omitsNullAndBlankValues() {
        CodexRunner runner = new CodexRunner();

        Map<String, Object> params = runner.threadStartParams(null, "   ");

        assertEquals(Map.of(), params);
    }

    @Test
    void threadStartParams_includesCwdAndModelWhenPresent() {
        CodexRunner runner = new CodexRunner();

        Map<String, Object> params = runner.threadStartParams("/workspace/project", "gpt-5-codex");

        assertEquals(Map.of("cwd", "/workspace/project", "model", "gpt-5-codex"), params);
    }

    @Test
    void stripFences_removesMarkdownFence() {
        CodexRunner runner = new CodexRunner();

        assertEquals("{\"a\":1}", runner.stripFences("```json\n{\"a\":1}\n```"));
    }

    @Test
    void stripFences_plainTextReturnedAsIs() {
        CodexRunner runner = new CodexRunner();

        assertEquals("hello world", runner.stripFences("hello world"));
    }

    @Test
    void resolveCodexHome_usesWorkspaceScopedRetainedDirectoryWhenRunIsKnown() throws Exception {
        CodexRunner runner = new CodexRunner();

        Path codexHome = runner.resolveCodexHome(tempDir.toString(), "run-1");

        assertEquals(tempDir.resolve(".agent_context").resolve("codex-home").resolve("run-1"), codexHome);
        assertFalse(runner.shouldDeleteCodexHome(tempDir.toString(), "run-1"));
    }
}
