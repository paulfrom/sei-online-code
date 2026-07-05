package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentBriefWriter} 单元测试（PR3 #7）。
 *
 * <p>验证 WHY：brief 让 codex/claude 在 workdir 经原生机制（AGENTS.md/CLAUDE.md）发现 agent
 * identity。核心契约：codex→AGENTS.md、claude→CLAUDE.md、marker 块幂等（重跑不堆叠）、
 * 用户既有内容保留。错配会让 codex 读不到 brief（写错文件名）或污染用户 repo 配置。</p>
 */
class AgentBriefWriterTest {

    @TempDir
    Path workDir;

    @Test
    void writeBrief_codexWritesAgentsMd() throws IOException {
        AgentBriefWriter.writeBrief(workDir.toString(), "codex", "suid-dev",
                "You implement one page per run.", LoggerFactory.getLogger(AgentBriefWriterTest.class));

        Path agents = workDir.resolve("AGENTS.md");
        assertTrue(Files.exists(agents), "codex 须写 AGENTS.md（非 CLAUDE.md）");
        assertFalse(Files.exists(workDir.resolve("CLAUDE.md")), "codex 不应写 CLAUDE.md");
        String content = Files.readString(agents, StandardCharsets.UTF_8);
        assertTrue(content.contains(AgentBriefWriter.MARKER_BEGIN), "须含 marker BEGIN");
        assertTrue(content.contains(AgentBriefWriter.MARKER_END), "须含 marker END");
        assertTrue(content.contains("**You are: suid-dev**"), "须含 agent 名");
        assertTrue(content.contains("You implement one page per run."), "须含 instructions");
    }

    @Test
    void writeBrief_claudeWritesClaudeMd() throws IOException {
        AgentBriefWriter.writeBrief(workDir.toString(), "claude", "planner",
                "Plan the work.", LoggerFactory.getLogger(AgentBriefWriterTest.class));

        assertTrue(Files.exists(workDir.resolve("CLAUDE.md")), "claude 须写 CLAUDE.md");
        assertFalse(Files.exists(workDir.resolve("AGENTS.md")), "claude 不应写 AGENTS.md");
    }

    @Test
    void writeBrief_nullCliToolDefaultsToClaude() throws IOException {
        // 默认 claude（cliTool null，对齐 CliRunnerRegistry.DEFAULT_TOOL）→ 仍写 CLAUDE.md
        AgentBriefWriter.writeBrief(workDir.toString(), null, "a", "i", null);

        assertTrue(Files.exists(workDir.resolve("CLAUDE.md")), "null cliTool 须默认 claude → CLAUDE.md");
    }

    @Test
    void writeBrief_idempotentSingleBlockAfterDoubleWrite() throws IOException {
        AgentBriefWriter.writeBrief(workDir.toString(), "codex", "a", "i", null);
        AgentBriefWriter.writeBrief(workDir.toString(), "codex", "a", "i", null);

        String content = Files.readString(workDir.resolve("AGENTS.md"), StandardCharsets.UTF_8);
        long beginCount = content.lines().filter(l -> l.equals(AgentBriefWriter.MARKER_BEGIN)).count();
        assertEquals(1L, beginCount, "二次写入后仍只含一个 marker 块（替换块体，非追加）");
    }

    @Test
    void writeBrief_preservesUserContentOutsideMarkerBlock() throws IOException {
        Files.writeString(workDir.resolve("AGENTS.md"), "# My Repo\n\n用户自定义内容\n", StandardCharsets.UTF_8);

        AgentBriefWriter.writeBrief(workDir.toString(), "codex", "a", "i", null);

        String content = Files.readString(workDir.resolve("AGENTS.md"), StandardCharsets.UTF_8);
        assertTrue(content.contains("# My Repo"), "用户既有内容须保留");
        assertTrue(content.contains("用户自定义内容"), "用户自定义内容须保留");
        assertTrue(content.contains(AgentBriefWriter.MARKER_BEGIN), "托管块须追加");
    }

    @Test
    void writeBrief_unknownCliToolSkips() {
        AgentBriefWriter.writeBrief(workDir.toString(), "gemini", "a", "i", null);

        assertFalse(Files.exists(workDir.resolve("AGENTS.md")), "未知 cliTool 须 skip（不写文件）");
        assertFalse(Files.exists(workDir.resolve("CLAUDE.md")), "未知 cliTool 须 skip");
    }

    @Test
    void writeBrief_blankWorkDirSkips() {
        AgentBriefWriter.writeBrief(null, "codex", "a", "i", null);
        AgentBriefWriter.writeBrief("  ", "codex", "a", "i", null);

        // 无异常即通过；null/blank workDir 不应抛
    }

    @Test
    void writeBrief_emptyNameAndInstructionsSkips() {
        AgentBriefWriter.writeBrief(workDir.toString(), "codex", null, null, null);
        AgentBriefWriter.writeBrief(workDir.toString(), "codex", "  ", "  ", null);

        assertFalse(Files.exists(workDir.resolve("AGENTS.md")),
                "name + instructions 皆空时不应写文件（无 identity 可写）");
    }

    @Test
    void buildBrief_returnsNullWhenIdentityEmpty() {
        assertNull(AgentBriefWriter.buildBrief(null, null), "name+instructions 皆 null → null");
        assertNull(AgentBriefWriter.buildBrief("", ""), "name+instructions 皆空 → null");
    }

    @Test
    void runtimeConfigPath_codexClaudeDefault() {
        assertEquals(workDir.resolve("AGENTS.md"), AgentBriefWriter.runtimeConfigPath(workDir, "codex"));
        assertEquals(workDir.resolve("CLAUDE.md"), AgentBriefWriter.runtimeConfigPath(workDir, "claude"));
        assertEquals(workDir.resolve("CLAUDE.md"), AgentBriefWriter.runtimeConfigPath(workDir, null),
                "null → 默认 claude → CLAUDE.md");
        assertNull(AgentBriefWriter.runtimeConfigPath(workDir, "unknown"), "未知 tool → null（skip）");
    }
}
