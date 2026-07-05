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
 * {@link CodexRunner} 端到端 real-codex 测试（PR1.1）。
 *
 * <p>验证 WHY：PR1 的 codex 路径从未对真实 codex 跑过——{@code -o <tempfile>} 结果提取（替代
 * 未核实的 NDJSON 猜解）、{@code --json} 事件流、per-run {@code CODEX_HOME} + sandbox 配置、
 * ProcessBuilder 透传代理 env，整条链路只有真实 spawn 一次才能证明端到端正确。本测试钉死：
 * 确定性 echo prompt 触发 codex 真实响应 → {@code -o} 文件非空 → {@link CodexRunner#readResultFile}
 * 返回非空且含期望回声。</p>
 *
 * <p>环境 gating：真实 codex 调用属外部依赖（需 codex 已装 + 网络可达 OpenAI；若经代理，JVM 启动
 * 环境须 export {@code HTTPS_PROXY}——见 {@link CodexRunner} 类 javadoc）。{@link #assumeCodexAvailable()}
 * 在 {@code @BeforeAll} 探测可执行文件（尊重 {@code CODEX_EXECUTABLE_PATH}，否则查 PATH），
 * 不可用时整类优雅跳过，不破坏构建。</p>
 *
 * <p>cwd 处理：codex 默认检查 cwd 是否 git 仓库（对应 {@code --skip-git-repo-check} flag）。
 * 生产路径 cwd 为 workspace git 仓库，故本测试 {@code git init} 临时目录以镜像生产，而非在
 * {@code buildArgs} 注入 {@code --skip-git-repo-check}（后者超 PR1.1 范围）。</p>
 *
 * @author sei-online-code
 */
class CodexRunnerRealCodexTest {

    private static CodexRunner runner;
    private static Path workdir;

    @BeforeAll
    static void beforeAll() throws IOException {
        assumeCodexAvailable();
        runner = new CodexRunner();
        // 临时目录作 cwd 并 git init，镜像生产 workspace（git 仓库）——避免触发 codex 的 git-repo-check。
        workdir = Files.createTempDirectory("codex-runner-e2e-");
        boolean gitReady = gitInit(workdir);
        Assumptions.assumeTrue(gitReady,
                "git init 失败（git 不可用？）— 跳过 real-codex E2E，workdir=" + workdir);
    }

    @AfterAll
    static void afterAll() {
        if (workdir != null) {
            try {
                Files.walkFileTree(workdir, new SimpleFileDeleter());
            } catch (IOException ignored) {
                // codex / git 可能在 cwd 写文件——残留于系统临时区，OS 会清，不影响测试结论。
            }
        }
    }

    @Test
    void execute_returnsNonEmptyResult_whenCodexResponds() throws Exception {
        // 确定性 echo prompt——codex 正常响应时必含 PONG，证明端到端 spawn + -o 落盘 + 文件读取成功。
        String prompt = "Reply with exactly the word PONG and nothing else.";
        CompletableFuture<String> future = runner.execute("real-codex-e2e", prompt, workdir.toString(), null);

        String result = future.get(180, TimeUnit.SECONDS);
        assertNotNull(result, "codex 进程退出码非 0 或 -o 输出文件为空 → future 返回 null");
        assertFalse(result.isBlank(), "result 文本不得为空");
        assertTrue(result.toLowerCase().contains("pong"),
                "codex 应回声 PONG，实际: " + result);
    }

    /**
     * 探测 codex CLI 是否可用：尊重 {@link CodexRunner} 构造函数的 {@code CODEX_EXECUTABLE_PATH}
     * 约定，缺省查 PATH 中的 {@code codex}。不可用则 {@link Assumptions#assumeTrue} 跳过整类。
     */
    private static void assumeCodexAvailable() {
        String env = System.getenv("CODEX_EXECUTABLE_PATH");
        String executable = (env == null || env.isBlank()) ? "codex" : env;
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
                "codex CLI 不可用（CODEX_EXECUTABLE_PATH=" + env + "）— 跳过 real-codex E2E");
    }

    /** 在 cwd 跑 {@code git init}（无 commit），让 codex 把它当 git 仓库。git 不可用返回 false。 */
    private static boolean gitInit(Path dir) {
        try {
            Process git = new ProcessBuilder("git", "init")
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            return git.waitFor(15, TimeUnit.SECONDS) && git.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /** 简易递归删除 visitor（codex/git 可能在 cwd 写多文件，需递归清）。 */
    private static final class SimpleFileDeleter extends java.nio.file.SimpleFileVisitor<Path> {
        @Override
        public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs)
                throws IOException {
            Files.deleteIfExists(file);
            return java.nio.file.FileVisitResult.CONTINUE;
        }

        @Override
        public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.deleteIfExists(dir);
            return java.nio.file.FileVisitResult.CONTINUE;
        }
    }
}
