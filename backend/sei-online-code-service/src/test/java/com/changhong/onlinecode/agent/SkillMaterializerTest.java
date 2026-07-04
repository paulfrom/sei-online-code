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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SkillMaterializer} 单元测试（B21）。
 *
 * <p>验证 WHY：materialize 必须幂等——同一 worktree 在多次 dispatch/重试中会被反复调用，
 * 若每次都重写会造成不必要的磁盘 IO 且破坏「hash 即复现标记」的语义。因此断言：内容相同
 * （computedHash 相同）时第二次调用不再写盘；只有内容变化（hash 变化）才重写。</p>
 *
 * @author sei-online-code
 */
class SkillMaterializerTest {

    private final SkillMaterializer materializer = new SkillMaterializer();

    @Test
    void materialize_writesSkillMdAndLock_onFirstRun(@TempDir Path worktree) throws IOException {
        SkillMaterializer.SkillPayload suid =
                new SkillMaterializer.SkillPayload("suid", "# SUID Skill\n", "sha256:aaa");

        int written = materializer.materialize(worktree.toString(), List.of(suid));

        assertEquals(1, written, "首次写入应产生 1 次实际写盘");
        Path dir = worktree.resolve(".claude/skills/suid");
        assertTrue(Files.exists(dir.resolve("SKILL.md")), "SKILL.md 应被写入");
        assertEquals("# SUID Skill\n",
                Files.readString(dir.resolve("SKILL.md"), StandardCharsets.UTF_8));
        assertEquals("sha256:aaa",
                Files.readString(dir.resolve(".lock"), StandardCharsets.UTF_8),
                ".lock 应等于 computedHash");
    }

    @Test
    void materialize_isIdempotent_whenHashUnchanged(@TempDir Path worktree) {
        SkillMaterializer.SkillPayload suid =
                new SkillMaterializer.SkillPayload("suid", "# SUID Skill\n", "sha256:aaa");

        int first = materializer.materialize(worktree.toString(), List.of(suid));
        int second = materializer.materialize(worktree.toString(), List.of(suid));

        assertEquals(1, first, "首次应写盘");
        assertEquals(0, second, "hash 未变时第二次应幂等跳过，不写盘");
    }

    @Test
    void materialize_rewrites_whenHashChanged(@TempDir Path worktree) throws IOException {
        materializer.materialize(worktree.toString(),
                List.of(new SkillMaterializer.SkillPayload("suid", "old", "sha256:aaa")));

        int written = materializer.materialize(worktree.toString(),
                List.of(new SkillMaterializer.SkillPayload("suid", "new", "sha256:bbb")));

        assertEquals(1, written, "hash 变化应触发重写");
        Path dir = worktree.resolve(".claude/skills/suid");
        assertEquals("new", Files.readString(dir.resolve("SKILL.md"), StandardCharsets.UTF_8));
        assertEquals("sha256:bbb", Files.readString(dir.resolve(".lock"), StandardCharsets.UTF_8));
    }

    @Test
    void materialize_writesAuxFiles_includingNestedDirs(@TempDir Path worktree) throws IOException {
        // WHY: Phase 5 辅助文件须随 SKILL.md 一并写入 worktree（含子目录），否则 agent 拿不到完整技能包。
        SkillMaterializer.SkillPayload skill = new SkillMaterializer.SkillPayload(
                "suid", "# SUID\n", "sha256:aaa",
                List.of(
                        new SkillMaterializer.SkillFileRef("references/bar.md", "# bar"),
                        new SkillMaterializer.SkillFileRef("run.sh", "echo hi")));

        int written = materializer.materialize(worktree.toString(), List.of(skill));

        assertEquals(1, written);
        Path dir = worktree.resolve(".claude/skills/suid");
        assertEquals("# bar", Files.readString(dir.resolve("references/bar.md"), StandardCharsets.UTF_8),
                "嵌套子目录辅助文件应写入");
        assertEquals("echo hi", Files.readString(dir.resolve("run.sh"), StandardCharsets.UTF_8),
                "根级辅助文件应写入");
    }

    @Test
    void materialize_skipsPathTraversal_auxFiles(@TempDir Path worktree) throws IOException {
        // WHY: 辅助文件 path 来自导入请求；即便 service 层 @Valid 拦截，materializer 仍须
        //      defense-in-depth——越界路径（../escape）绝不写出技能目录之外。
        SkillMaterializer.SkillPayload skill = new SkillMaterializer.SkillPayload(
                "suid", "# SUID\n", "sha256:aaa",
                List.of(new SkillMaterializer.SkillFileRef("../escape.md", "evil")));

        materializer.materialize(worktree.toString(), List.of(skill));

        Path dir = worktree.resolve(".claude/skills/suid");
        assertTrue(Files.exists(dir.resolve("SKILL.md")), "SKILL.md 仍应写入（越界只跳过该单文件）");
        assertFalse(Files.exists(worktree.resolve(".claude/skills/escape.md")),
                "越界路径不得写到技能目录之外");
    }

    @Test
    void materialize_isIdempotent_withFiles(@TempDir Path worktree) throws IOException {
        // WHY: 辅助文件与 SKILL.md 共享同一 .lock gate——hash 未变时二次调用必须整体跳过，
        //      不得重写辅助文件（避免重复 IO + 保持「hash 即复现标记」语义）。
        SkillMaterializer.SkillPayload skill = new SkillMaterializer.SkillPayload(
                "suid", "# SUID\n", "sha256:aaa",
                List.of(new SkillMaterializer.SkillFileRef("references/bar.md", "# bar")));

        int first = materializer.materialize(worktree.toString(), List.of(skill));
        int second = materializer.materialize(worktree.toString(), List.of(skill));

        assertEquals(1, first);
        assertEquals(0, second, "hash 未变时二次调用应整体幂等跳过");
        assertEquals("# bar",
                Files.readString(worktree.resolve(".claude/skills/suid/references/bar.md"),
                        StandardCharsets.UTF_8));
    }
}
