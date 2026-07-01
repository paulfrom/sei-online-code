package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
