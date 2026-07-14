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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CodexRunner} 离线集成测试：用 fake app-server 可执行脚本替身验证 JSON-RPC stdio 接线端到端正确。
 *
 * <p>验证 WHY：real-codex e2e 依赖 OpenAI 网络（受区域限制，多数环境跑不动），无法覆盖
 * {@code codex app-server --listen stdio://} 的 initialize → thread/start → turn/start → notifications
 * 契约。本测试用 fake 脚本模拟 app-server JSONL 行为，离线钉死 Java 侧进程接线。</p>
 *
 * <p>仅要求 POSIX 文件系统（chmod 可执行位）；非 POSIX（如 Windows）整类跳过，不破坏构建。</p>
 *
 * @author sei-online-code
 */
class CodexRunnerFakeExecutableTest {

    @TempDir
    Path tempDir;

    private Path workdir;

    @BeforeEach
    void setUp() throws IOException {
        workdir = Files.createTempDirectory(tempDir, "cwd-");
    }

    @Test
    void execute_returnsAggregatedAgentMessageDeltas() throws Exception {
        CodexRunner runner = new CodexRunner(fakeAppServerHappy().toString());

        String result = runner.executeDetailed("fake-it", null, null, "do something", workdir.toString(), "gpt-5-codex", null)
                .get(60, TimeUnit.SECONDS).getOutput();

        assertEquals("PONG", result);
    }

    @Test
    void execute_autoApprovesCommandRequestAndContinues() throws Exception {
        CodexRunner runner = new CodexRunner(fakeAppServerApproval().toString());

        String result = runner.executeDetailed("fake-it", null, null, "do something", workdir.toString(), null, null)
                .get(60, TimeUnit.SECONDS).getOutput();

        assertEquals("APPROVED", result);
    }

    @Test
    void execute_processExitsBeforeTurnCompletedReturnsNull() throws Exception {
        CodexRunner runner = new CodexRunner(fakeAppServerExitsEarly().toString());

        assertNull(runner.executeDetailed("fake-it", null, null, "p", workdir.toString(), null, null)
                .get(60, TimeUnit.SECONDS).getOutput());
    }

    @Test
    void execute_mcpBlockWrittenBeforeAppServerStarts() throws Exception {
        CodexRunner runner = new CodexRunner(fakeAppServerSnapshotConfig().toString());
        String mcpConfig = "{\"mcpServers\":{\"fetch\":{\"command\":\"uvx\",\"args\":[\"mcp-server-fetch\"]}}}";

        String result = runner.executeDetailed("fake-it", null, null, "p", workdir.toString(), null, mcpConfig)
                .get(60, TimeUnit.SECONDS).getOutput();

        assertTrue(result.contains("[mcp_servers.fetch]"), result);
        assertTrue(result.contains("command = \"uvx\""), result);
        assertTrue(result.contains(CodexSandboxConfig.MCP_BEGIN_MARKER), result);
        assertTrue(result.contains("args = [\"mcp-server-fetch\"]"), result);
    }

    @Test
    void execute_sandboxConfigIncludesCurrentWorkdirWritableRoot() throws Exception {
        CodexRunner runner = new CodexRunner(fakeAppServerSnapshotConfig().toString());

        String result = runner.executeDetailed("fake-it", null, null, "p", workdir.toString(), null, null)
                .get(60, TimeUnit.SECONDS).getOutput();

        assertTrue(result.contains("sandbox_mode = \"workspace-write\""), result);
        assertTrue(result.contains("writable_roots = [\"" + workdir.toAbsolutePath().normalize() + "\"]"), result);
    }

    private Path fakeAppServerHappy() throws IOException {
        return installScript(appServerScript("""
                emit_delta PO
                emit_delta NG
                emit_completed
                exit 0
                """), "fake-codex-happy");
    }

    private Path fakeAppServerApproval() throws IOException {
        return installScript(appServerScript("""
                printf '{"jsonrpc":"2.0","id":77,"method":"item/commandExecution/requestApproval","params":{}}\\n'
                while IFS= read -r -t 5 approval; do
                  if [[ "$approval" == *'"id":77'* ]] && [[ "$approval" == *'"decision":"accept"'* ]]; then
                    emit_delta APPROVED
                    emit_completed
                    exit 0
                  fi
                done
                echo "timed out waiting for approval response" >&2
                exit 1
                """), "fake-codex-approval");
    }

    private Path fakeAppServerExitsEarly() throws IOException {
        return installScript(appServerScript("""
                exit 0
                """), "fake-codex-early-exit");
    }

    private Path fakeAppServerSnapshotConfig() throws IOException {
        return installScript(appServerScript("""
                snapshot_file="$(mktemp)"
                trap 'rm -f "$snapshot_file"' EXIT
                if [ -n "$CODEX_HOME" ] && [ -f "$CODEX_HOME/config.toml" ]; then
                  cp "$CODEX_HOME/config.toml" "$snapshot_file"
                fi
                """, """
                while IFS= read -r config_line; do
                  emit_delta "${config_line}"$'\\n'
                done < "$snapshot_file"
                emit_completed
                exit 0
                """), "fake-codex-snapshot");
    }

    private String appServerScript(String turnStartBody) {
        return appServerScript("", turnStartBody);
    }

    private String appServerScript(String startupBody, String turnStartBody) {
        return """
                #!/usr/bin/env bash
                emit_delta() {
                  local delta="$1"
                  local escaped
                  escaped="$(printf '%s_' "$delta" | sed ':a;N;$!ba; s/\\\\/\\\\\\\\/g; s/"/\\\\"/g; s/\\n/\\\\n/g; s/\\r/\\\\r/g; s/\\t/\\\\t/g')"
                  escaped="${escaped%_}"
                  printf '{"method":"item/agentMessage/delta","params":{"threadId":"thr_fake","turnId":"turn_fake","itemId":"i","delta":"%s"}}\\n' "$escaped"
                }
                emit_completed() {
                  printf '{"method":"turn/completed","params":{"threadId":"thr_fake","turn":{"id":"turn_fake","status":"completed"}}}\\n'
                }
                request_id() {
                  printf '%s\\n' "$1" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\\([^,}]*\\).*/\\1/p'
                }
                request_method() {
                  printf '%s\\n' "$1" | sed -n 's/.*"method"[[:space:]]*:[[:space:]]*"\\([^"]*\\)".*/\\1/p'
                }
                """
                + startupBody.indent(0)
                + """
                while IFS= read -r -t 5 line; do
                  id="$(request_id "$line")"
                  method="$(request_method "$line")"
                  if [[ "$method" == "initialize" ]]; then
                    printf '{"jsonrpc":"2.0","id":%s,"result":{"userAgent":"fake","platformFamily":"linux","platformOs":"linux"}}\\n' "$id"
                  elif [[ "$method" == "thread/start" ]]; then
                    printf '{"jsonrpc":"2.0","id":%s,"result":{"thread":{"id":"thr_fake"}}}\\n' "$id"
                  elif [[ "$method" == "turn/start" ]]; then
                    printf '{"jsonrpc":"2.0","id":%s,"result":{"turn":{"id":"turn_fake"}}}\\n' "$id"
                """
                + turnStartBody.indent(4)
                + """
                  fi
                done
                echo "timed out waiting for JSON-RPC request" >&2
                exit 1
                """;
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
