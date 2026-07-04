package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClaudeRunner} 端到端 real-claude 测试。
 *
 * <p>验证 WHY：ClaudeRunner 是 PlanAgentService / DispatchService / FeatureDesignBuildService 等
 * agent 链路的执行底座，核心契约是「spawn 真实 claude CLI → 解析 {@code --output-format json}
 * 的 result envelope → 剥离 markdown 围栏 → 返回非空 result 文本」。单元测试 mock 掉进程，
 * 无法证明真实 envelope 解析与进程 stdout/stderr 流式读取端到端正确。本测试钉死这条路径：
 * 用一个确定性 echo prompt 触发 claude 真实响应，断言 future 完成且 result 非空、含期望回声。</p>
 *
 * <p>环境 gating：真实 claude 调用属外部依赖，CI / 未安装 claude 的开发机不可用。
 * {@link #assumeClaudeAvailable()} 在 {@code @BeforeAll} 用 {@link Assumptions#assumeTrue}
 * 探测可执行文件（尊重 {@code CLAUDE_EXECUTABLE_PATH} 约定，否则查 PATH），不可用时整类优雅跳过，
 * 不破坏构建；装了 claude 的环境自动启用。</p>
 *
 * @author sei-online-code
 */
class ClaudeRunnerRealClaudeTest {

    private static ClaudeRunner runner;
    private static Path workdir;

    @BeforeAll
    static void beforeAll() throws IOException {
        assumeClaudeAvailable();
        runner = new ClaudeRunner();
        // 临时空目录作 cwd，避免 claude 读取仓库 CLAUDE.md / .claude 干扰响应内容。
        workdir = Files.createTempDirectory("claude-runner-e2e-");
    }

    @AfterAll
    static void afterAll() {
        if (workdir != null) {
            try {
                Files.deleteIfExists(workdir);
            } catch (IOException ignored) {
                // claude 可能在 cwd 写缓存文件致目录非空——残留于系统临时区，OS 会清，不影响测试结论。
            }
        }
    }

    @Test
    void execute_returnsNonEmptyResult_whenClaudeResponds() throws Exception {
        // 确定性 echo prompt——claude 正常响应时必含 PONG，证明端到端 spawn + envelope 解析成功。
        String prompt = "Reply with exactly the word PONG and nothing else.";
        CompletableFuture<String> future = runner.execute("real-claude-e2e", prompt, workdir.toString());

        String result = future.get(120, TimeUnit.SECONDS);
        assertNotNull(result, "claude 进程退出码非 0 或 result envelope 解析失败 → future 返回 null");
        assertFalse(result.isBlank(), "result 文本不得为空");
        assertTrue(result.toLowerCase().contains("pong"),
                "claude 应回声 PONG，实际: " + result);
    }

    /**
     * 探测 claude CLI 是否可用：尊重 {@link ClaudeRunner} 构造函数的 {@code CLAUDE_EXECUTABLE_PATH}
     * 约定，缺省查 PATH 中的 {@code claude}。不可用则 {@link Assumptions#assumeTrue} 跳过整类。
     */
    private static void assumeClaudeAvailable() {
        String env = System.getenv("CLAUDE_EXECUTABLE_PATH");
        String executable = (env == null || env.isBlank()) ? "claude" : env;
        boolean available;
        try {
            Process probe = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true).start();
            available = probe.waitFor(15, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (IOException e) {
            available = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            available = false;
        }
        Assumptions.assumeTrue(available,
                "claude CLI 不可用（CLAUDE_EXECUTABLE_PATH=" + env + "）— 跳过 real-claude E2E");
    }
}
