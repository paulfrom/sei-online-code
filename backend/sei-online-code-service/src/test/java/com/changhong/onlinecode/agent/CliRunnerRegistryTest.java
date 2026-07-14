package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link CliRunnerRegistry} 单元测试。
 *
 * <p>验证 WHY：registry 是 per-agent CLI 路由的核心契约——按 {@code tool()} 索引，null/blank/未知
 * 回落默认 claude（向后兼容既有 null cliTool 的 agent）。错配会导致 codex agent 误用 claude runner
 * 或反之，spawn 出错误协议的进程。</p>
 */
class CliRunnerRegistryTest {

    private final CliRunnerRegistry registry = new CliRunnerRegistry(
            List.of(new ClaudeRunner(), new CodexRunner()));

    @Test
    void resolve_claude_returnsClaudeRunner() {
        assertEquals("claude", registry.resolve("claude").tool());
    }

    @Test
    void resolve_codex_returnsCodexRunner() {
        assertEquals("codex", registry.resolve("codex").tool());
    }

    @Test
    void resolve_null_defaultsToClaude() {
        assertEquals("claude", registry.resolve(null).tool());
    }

    @Test
    void resolve_blank_defaultsToClaude() {
        assertEquals("claude", registry.resolve("").tool());
        assertEquals("claude", registry.resolve("   ").tool());
    }

    @Test
    void resolve_unknown_defaultsToClaude() {
        assertEquals("claude", registry.resolve("gemini").tool());
    }

    @Test
    void defaultTool_isClaude() {
        assertEquals("claude", registry.defaultTool());
    }

    @Test
    void cancel_routesToOwningRunner() {
        CliRunner cancellable = new CliRunner() {
            public String tool() { return "fake"; }
            public CompletableFuture<CliRunResult> executeDetailed(String i, String t, String r, String p, String c, String m, String mc) {
                return CompletableFuture.completedFuture(null);
            }
            public boolean cancel(String runId) { return "run-1".equals(runId); }
        };
        CliRunnerRegistry cancellableRegistry = new CliRunnerRegistry(List.of(cancellable));

        assertTrue(cancellableRegistry.cancel("run-1"));
    }

    @Test
    void execute_usesResolvedProjectWorkspace(@TempDir Path workspace) {
        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        when(workspaceManager.resolve("project-1")).thenReturn(
                new WorkspaceResolveResult(workspace.toString(), true, null));
        AtomicReference<String> actualCwd = new AtomicReference<>();
        CliRunner runner = capturingRunner(actualCwd);
        CliRunnerRegistry boundRegistry = new CliRunnerRegistry(List.of(runner), workspaceManager, null);

        AgentWorkspace binding = boundRegistry.workspace("project-1");
        boundRegistry.executeDetailed(binding,
                new AgentInvocationContext("run-1", "iteration-1", null, null, null, "fake", null),
                "prompt", null).thenApply(CliRunResult::getOutput).join();

        assertEquals(workspace.toAbsolutePath().normalize().toString(), actualCwd.get());
    }

    @Test
    void execute_workspaceConfigurationChanged_rejectsOldBinding(@TempDir Path root) throws Exception {
        Path original = java.nio.file.Files.createDirectory(root.resolve("original"));
        Path changed = java.nio.file.Files.createDirectory(root.resolve("changed"));
        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        when(workspaceManager.resolve("project-1")).thenReturn(
                new WorkspaceResolveResult(original.toString(), true, null),
                new WorkspaceResolveResult(changed.toString(), true, null));
        AtomicReference<String> actualCwd = new AtomicReference<>();
        CliRunnerRegistry boundRegistry = new CliRunnerRegistry(
                List.of(capturingRunner(actualCwd)), workspaceManager, null);

        AgentWorkspace stale = boundRegistry.workspace("project-1");

        assertThrows(IllegalStateException.class,
                () -> boundRegistry.executeDetailed(stale,
                        new AgentInvocationContext("run-1", "iteration-1", null, null, null, "fake", null),
                        "prompt", null));
        assertEquals(null, actualCwd.get());
    }

    private static CliRunner capturingRunner(AtomicReference<String> cwd) {
        return new CliRunner() {
            public String tool() { return "fake"; }
            public CompletableFuture<CliRunResult> executeDetailed(String i, String t, String r, String p,
                                                     String c, String m, String mc) {
                cwd.set(c);
                CliRunResult result = new CliRunResult();
                result.setOutput("ok");
                result.setProcessSucceeded(true);
                return CompletableFuture.completedFuture(result);
            }
        };
    }
}
