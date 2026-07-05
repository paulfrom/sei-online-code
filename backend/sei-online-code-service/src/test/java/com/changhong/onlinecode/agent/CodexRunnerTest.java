package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CodexRunner} 单元测试（PR1 + PR1.1）。
 *
 * <p>验证 WHY：codex runner 的三个核心契约——
 * <ol>
 *   <li>「一次性 exec + {@code --json} + {@code -o <file>}」参数构造（PR1.1：{@code -o} 取代 NDJSON
 *       {@code item.text} 猜解，结果由 codex 自身落盘）；</li>
 *   <li>「{@code -m <model>} 当且仅当 model 非空时注入」（PR1.1：补 PR1 的 --model 缺口）；</li>
 *   <li>「从 {@code -o} 输出文件读最终消息并剥围栏，空/缺失返回 null」（PR1.1：结果提取权威路径）。</li>
 * </ol>
 * spawn 真实进程属 env-gated e2e（同 {@code CodexRunnerRealCodexTest}），本单测钉死 args 构造与
 * 文件读取，不依赖外部 codex 安装。</p>
 */
class CodexRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void buildArgs_jsonAndOutputFileAlwaysPresent_modelOmittedWhenBlank() throws IOException {
        CodexRunner runner = new CodexRunner();
        Path outFile = Files.createTempFile(tempDir, "codex-last-", ".txt");
        List<String> args = runner.buildArgs("do something", null, outFile);

        assertEquals("codex", args.get(0));
        assertEquals("exec", args.get(1));
        assertEquals("do something", args.get(2));
        assertEquals("--json", args.get(3));
        assertEquals("-o", args.get(4));
        assertEquals(outFile.toString(), args.get(5));
        // model=null → 不注入 -m
        assertFalse(args.stream().anyMatch("-m"::equals), "model 为空时不得注入 -m");
    }

    @Test
    void buildArgs_blankModelTreatedAsAbsent() throws IOException {
        CodexRunner runner = new CodexRunner();
        Path outFile = Files.createTempFile(tempDir, "codex-last-", ".txt");
        List<String> args = runner.buildArgs("p", "   ", outFile);
        assertFalse(args.stream().anyMatch("-m"::equals), "model 空白时不得注入 -m");
    }

    @Test
    void buildArgs_nonBlankModelInjectsMFlag() throws IOException {
        CodexRunner runner = new CodexRunner();
        Path outFile = Files.createTempFile(tempDir, "codex-last-", ".txt");
        List<String> args = runner.buildArgs("p", "gpt-5-codex", outFile);

        int mIdx = args.indexOf("-m");
        assertTrue(mIdx > 0, "model 非空时必须注入 -m");
        assertEquals("gpt-5-codex", args.get(mIdx + 1));
        // -o 仍在
        assertEquals("-o", args.get(4));
    }

    @Test
    void buildArgs_nullOutputFileOmitsOFlag_degeneratePath() {
        CodexRunner runner = new CodexRunner();
        // outputFile=null 仅退化场景（spawn 前建文件失败）；正常路径不会出现。
        List<String> args = runner.buildArgs("p", "m", null);
        assertFalse(args.stream().anyMatch("-o"::equals), "outputFile=null 时不得加 -o");
        assertTrue(args.stream().anyMatch("-m"::equals));
    }

    @Test
    void readResultFile_readsAndStripsFences() throws IOException {
        CodexRunner runner = new CodexRunner();
        Path f = Files.createTempFile(tempDir, "codex-last-", ".txt");
        Files.writeString(f, "```json\n{\"a\":1}\n```", StandardCharsets.UTF_8);
        assertEquals("{\"a\":1}", runner.readResultFile(f));
    }

    @Test
    void readResultFile_plainTextReturnedAsIs() throws IOException {
        CodexRunner runner = new CodexRunner();
        Path f = Files.createTempFile(tempDir, "codex-last-", ".txt");
        Files.writeString(f, "hello world", StandardCharsets.UTF_8);
        assertEquals("hello world", runner.readResultFile(f));
    }

    @Test
    void readResultFile_blankReturnsNull() throws IOException {
        CodexRunner runner = new CodexRunner();
        Path f = Files.createTempFile(tempDir, "codex-last-", ".txt");
        Files.writeString(f, "   \n  ", StandardCharsets.UTF_8);
        assertNull(runner.readResultFile(f), "空文件 → null，调用方走 fallback");
    }

    @Test
    void readResultFile_missingFileReturnsNull() {
        CodexRunner runner = new CodexRunner();
        Path nonexistent = tempDir.resolve("does-not-exist.txt");
        assertNull(runner.readResultFile(nonexistent));
    }

    @Test
    void readResultFile_nullPathReturnsNull() {
        CodexRunner runner = new CodexRunner();
        assertNull(runner.readResultFile(null));
    }

    @Test
    void readResultFile_nonEmptyProvesAuthoritativeSource() throws IOException {
        // WHY: codex -o 是结果文本的权威来源——非空即证明 spawn+落盘端到端成功。
        CodexRunner runner = new CodexRunner();
        Path f = Files.createTempFile(tempDir, "codex-last-", ".txt");
        Files.writeString(f, "PONG", StandardCharsets.UTF_8);
        assertNotNull(runner.readResultFile(f));
    }
}
