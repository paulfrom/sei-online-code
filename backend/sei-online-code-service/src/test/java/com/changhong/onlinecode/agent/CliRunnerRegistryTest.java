package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            public CompletableFuture<String> execute(String i, String p, String c, String m, String mc) {
                return CompletableFuture.completedFuture(null);
            }
            public CompletableFuture<String> execute(String i, String t, String r, String p, String c, String m, String mc) {
                return CompletableFuture.completedFuture(null);
            }
            public boolean cancel(String runId) { return "run-1".equals(runId); }
        };
        CliRunnerRegistry cancellableRegistry = new CliRunnerRegistry(List.of(cancellable));

        assertTrue(cancellableRegistry.cancel("run-1"));
    }
}
