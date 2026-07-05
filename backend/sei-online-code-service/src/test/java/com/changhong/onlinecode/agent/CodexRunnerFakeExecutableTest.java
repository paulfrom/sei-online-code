package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CodexRunner} 离线集成测试（PR1.1）：用 fake 可执行脚本替身验证 {@code -o} 落盘→读取→剥围栏
 * 的 Java 接线端到端正确。
 *
 * <p>验证 WHY：PR1.1 用 {@code -o <tempfile>} 替代未核实的 NDJSON 猜解作为结果来源——这条路径
 * 的 Java 侧（buildArgs 传 {@code -o} → ProcessBuilder spawn → 读文件 → stripFences → 返回）
 * 从未对任何类 codex 程序跑过。{@code CodexRunnerRealCodexTest} 依赖 OpenAI 网络（受区域限制，
 * 多数环境跑不动），无法覆盖此契约。本测试用 fake 脚本模拟 codex 的 {@code -o} 行为（解析 argv
 * 中的 {@code -o <file>}，写最终消息到该文件，stdout 走 NDJSON 事件流），离线钉死整条 Java 接线。</p>
 *
 * <p>仅要求 POSIX 文件系统（chmod 可执行位）；非 POSIX（如 Windows）整类跳过，不破坏构建。</p>
 *
 * @author sei-online-code
 */
class CodexRunnerFakeExecutableTest {

    @TempDir
    Path tempDir;

    private Path fakeCodex;
    private Path workdir;

    @BeforeEach
    void setUp() throws IOException {
        fakeCodex = writeFakeCodex();
        workdir = Files.createTempDirectory(tempDir, "cwd-");
    }

    @Test
    void execute_readsResultFromOutputFile() throws Exception {
        // fake 脚本把 "PONG" 写入 -o 文件——证明 CodexRunner 的 -o 接线端到端正确。
        CodexRunner runner = new CodexRunner(fakeCodex.toString());
        CompletableFuture<String> future = runner.execute("fake-it", "do something", workdir.toString(), null, null);

        String result = future.get(60, TimeUnit.SECONDS);
        assertEquals("PONG", result, "-o 文件应由 spawned 进程写入并由 runner 读回");
    }

    @Test
    void execute_stripsMarkdownFencesFromFile() throws Exception {
        // fake 脚本写围栏内容——证明 readResultFile 剥围栏在真实 spawn 路径生效。
        CodexRunner runner = new CodexRunner(fakeCodexFenced().toString());
        CompletableFuture<String> future = runner.execute("fake-it", "p", workdir.toString(), null, null);

        assertEquals("{\"a\":1}", future.get(60, TimeUnit.SECONDS));
    }

    @Test
    void execute_nonZeroExitReturnsNull() throws Exception {
        // fake 脚本 exit 1 且不写 -o——证明失败路径返回 null（调用方走 fallback）。
        CodexRunner runner = new CodexRunner(fakeCodexFailing().toString());
        CompletableFuture<String> future = runner.execute("fake-it", "p", workdir.toString(), null, null);

        assertNull(future.get(60, TimeUnit.SECONDS), "进程退出码非 0 → null");
    }

    @Test
    void execute_modelFlagPassedThroughToExecutable() throws Exception {
        // 验证 -m 注入：fake 脚本把 argv 回写到 -o 文件，断言 argv 含 -m <model>。
        CodexRunner runner = new CodexRunner(fakeCodexEchoArgs().toString());
        CompletableFuture<String> future = runner.execute("fake-it", "p", workdir.toString(), "gpt-5-codex", null);

        String result = future.get(60, TimeUnit.SECONDS);
        assertTrue(result.contains("-m"), "argv 应含 -m，实际: " + result);
        assertTrue(result.contains("gpt-5-codex"), "argv 应含 model 名，实际: " + result);
    }

    @Test
    void execute_mcpBlockWrittenToConfigToml() throws Exception {
        // 验证 PR3 #6：mcpConfig 经 writeMcpBlock 写入 per-run CODEX_HOME/config.toml 的 [mcp_servers.*] 托管块。
        // fake 脚本把 $CODEX_HOME/config.toml 快照到 -o 文件，runner 读回后断言含 server 表。
        CodexRunner runner = new CodexRunner(fakeCodexSnapshotConfig().toString());
        String mcpConfig = "{\"mcpServers\":{\"fetch\":{\"command\":\"uvx\",\"args\":[\"mcp-server-fetch\"]}}}";
        CompletableFuture<String> future = runner.execute("fake-it", "p", workdir.toString(), null, mcpConfig);

        String result = future.get(60, TimeUnit.SECONDS);
        assertTrue(result.contains("[mcp_servers.fetch]"), "config.toml 须含 [mcp_servers.fetch] 托管块，实际: " + result);
        assertTrue(result.contains("command = \"uvx\""), "config.toml 须含 server command，实际: " + result);
        assertTrue(result.contains(CodexSandboxConfig.MCP_BEGIN_MARKER), "config.toml 须含托管块 marker，实际: " + result);
    }

    /**
     * fake codex 脚本：解析 argv 中 {@code -o <file>}，写 {@code PONG} 到该文件；stdout 走 NDJSON
     * 事件流（模拟 codex --json，CodexRunner 不再解析仅流式展示）；exit 0。
     */
    private Path writeFakeCodex() throws IOException {
        String script = "#!/usr/bin/env bash\n"
                + "out=\"\"\n"
                + "prev=\"\"\n"
                + "for a in \"$@\"; do\n"
                + "  if [ \"$prev\" = \"-o\" ]; then out=\"$a\"; fi\n"
                + "  prev=\"$a\"\n"
                + "done\n"
                + "printf '{\"type\":\"thread.started\"}\\n'\n"
                + "printf '{\"type\":\"turn.started\"}\\n'\n"
                + "if [ -n \"$out\" ]; then printf 'PONG' > \"$out\"; fi\n"
                + "exit 0\n";
        return installScript(script, "fake-codex");
    }

    /** fake codex：写围栏 JSON 到 -o 文件。 */
    private Path fakeCodexFenced() throws IOException {
        String script = "#!/usr/bin/env bash\n"
                + "out=\"\"\nprev=\"\"\n"
                + "for a in \"$@\"; do if [ \"$prev\" = \"-o\" ]; then out=\"$a\"; fi; prev=\"$a\"; done\n"
                + "if [ -n \"$out\" ]; then printf '```json\\n{\"a\":1}\\n```' > \"$out\"; fi\n"
                + "exit 0\n";
        return installScript(script, "fake-codex-fenced");
    }

    /** fake codex：exit 1 不写 -o（模拟 turn.failed）。 */
    private Path fakeCodexFailing() throws IOException {
        String script = "#!/usr/bin/env bash\n"
                + "printf '{\"type\":\"turn.failed\"}\\n'\n"
                + "exit 1\n";
        return installScript(script, "fake-codex-fail");
    }

    /** fake codex：把全部 argv 写入 -o 文件（用于断言 -m 注入）。 */
    private Path fakeCodexEchoArgs() throws IOException {
        String script = "#!/usr/bin/env bash\n"
                + "out=\"\"\nprev=\"\"\n"
                + "for a in \"$@\"; do if [ \"$prev\" = \"-o\" ]; then out=\"$a\"; fi; prev=\"$a\"; done\n"
                + "if [ -n \"$out\" ]; then printf '%s' \"$*\" > \"$out\"; fi\n"
                + "exit 0\n";
        return installScript(script, "fake-codex-echoargs");
    }

    /** fake codex：把 {@code $CODEX_HOME/config.toml} 快照到 -o 文件（用于断言 MCP 托管块写入）。 */
    private Path fakeCodexSnapshotConfig() throws IOException {
        String script = "#!/usr/bin/env bash\n"
                + "out=\"\"\nprev=\"\"\n"
                + "for a in \"$@\"; do if [ \"$prev\" = \"-o\" ]; then out=\"$a\"; fi; prev=\"$a\"; done\n"
                + "if [ -n \"$out\" ] && [ -n \"$CODEX_HOME\" ] && [ -f \"$CODEX_HOME/config.toml\" ]; then\n"
                + "  cp \"$CODEX_HOME/config.toml\" \"$out\"\n"
                + "fi\n"
                + "exit 0\n";
        return installScript(script, "fake-codex-snapshot");
    }

    private Path installScript(String content, String name) throws IOException {
        Path script = Files.createFile(tempDir.resolve(name + ".sh"));
        Files.writeString(script, content, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(script, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException e) {
            // 非 POSIX 文件系统（如 Windows）——整类跳过。
            Assumptions.assumeTrue(false, "非 POSIX 文件系统，跳过 fake-codex 集成测试");
        }
        return script;
    }
}
