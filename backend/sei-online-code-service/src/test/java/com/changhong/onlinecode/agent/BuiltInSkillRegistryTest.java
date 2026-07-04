package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BuiltInSkillRegistry} 单元测试（multica 维度 g）。
 *
 * <p>验证 WHY：内置技能不再落 oc_skill，agent 经 {@code builtin:<name>} synthetic id 绑定。
 * registry 必须能从 classpath 还原出可 materialize 的 payload（SKILL.md + references），否则
 * dispatch 时内置技能静默丢失、worktree 缺技能目录。故断言：已知内置名→present 且内容/文件就绪；
 * 未知名 / 非 builtin id / 非法名 → empty（caller 回退 DB 路径，不误判）。</p>
 *
 * @author sei-online-code
 */
class BuiltInSkillRegistryTest {

    private final BuiltInSkillRegistry registry = new BuiltInSkillRegistry();

    @Test
    void resolve_builtinSuid_returnsPayloadWithContentAndReferenceFiles() {
        Optional<SkillMaterializer.SkillPayload> payload = registry.resolve("builtin:suid");

        assertTrue(payload.isPresent(), "builtin:suid 必须可解析（已 vendor 到 classpath）");
        assertEquals("suid", payload.get().name());
        assertTrue(payload.get().content().contains("suid"), "SKILL.md 正文应含技能名");
        assertTrue(payload.get().computedHash().startsWith("sha256:"),
                "hash 按 §6 recipe 计算，形如 sha256:<hex>");
        assertFalse(payload.get().files().isEmpty(),
                "suid vendor 含 references/*.md，应作为辅助文件加载");
    }

    @Test
    void resolve_builtinEadpBackend_returnsPayloadWithReferenceFiles() {
        Optional<SkillMaterializer.SkillPayload> payload = registry.resolve("builtin:eadp-backend");

        assertTrue(payload.isPresent());
        assertEquals("eadp-backend", payload.get().name());
        assertFalse(payload.get().files().isEmpty(), "eadp-backend vendor 含 references/*.md");
    }

    @Test
    void resolve_builtinStubSkill_returnsPayloadWithoutReferenceFiles() {
        Optional<SkillMaterializer.SkillPayload> payload = registry.resolve("builtin:project-planning");

        assertTrue(payload.isPresent(), "stub 技能也应有 SKILL.md");
        assertTrue(payload.get().content().contains("project-planning"));
        assertTrue(payload.get().files().isEmpty(), "project-planning 无 references 目录");
    }

    @Test
    void resolve_unknownBuiltinName_returnsEmpty() {
        assertTrue(registry.resolve("builtin:does-not-exist").isEmpty(),
                "未知内置名 → empty（不臆造 payload）");
    }

    @Test
    void resolve_nonBuiltinId_returnsEmpty() {
        // 非 builtin: 前缀的 id（如 DB uuid）由 caller 走 DB 路径，registry 不处理
        assertTrue(registry.resolve("SKIL0001").isEmpty());
    }

    @Test
    void resolve_blankOrNullId_returnsEmpty() {
        assertTrue(registry.resolve(null).isEmpty());
        assertTrue(registry.resolve("").isEmpty());
        assertTrue(registry.resolve("builtin:").isEmpty(), "空 name 应被正则拒绝");
    }
}
