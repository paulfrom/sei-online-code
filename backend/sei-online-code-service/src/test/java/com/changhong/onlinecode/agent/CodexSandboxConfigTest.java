package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CodexSandboxConfig} 单元测试。
 *
 * <p>验证 WHY：codex 沙箱策略是 PR1 唯一的 execenv 件，核心契约是「按平台写正确的 sandbox_mode
 * 托管块、幂等重写、不破坏用户既有 config.toml 内容」。错配会导致 codex 在 macOS 上网络静默失败
 * （openai/codex#10390）或在 Linux 上过度放开权限。</p>
 *
 * <p>平台分流通过临时改 {@code os.name} 系统属性驱动（{@link CodexSandboxConfig#isDarwin()} 读它），
 * 每测后恢复原值，避免污染同进程其他测试。</p>
 */
class CodexSandboxConfigTest {

    private Path codexHome;
    private String originalOsName;

    @BeforeEach
    void setUp() throws IOException {
        originalOsName = System.getProperty("os.name");
        codexHome = Files.createTempDirectory("codex-sandbox-test-");
    }

    @AfterEach
    void tearDown() {
        if (originalOsName != null) {
            System.setProperty("os.name", originalOsName);
        }
        deleteTree(codexHome);
    }

    @Test
    void write_nonDarwin_writesWorkspaceWriteWithNetwork() throws IOException {
        System.setProperty("os.name", "Linux");

        CodexSandboxConfig.write(codexHome, LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        assertTrue(config.contains("sandbox_mode = \"workspace-write\""), "非-darwin 须 workspace-write");
        assertTrue(config.contains("[sandbox_workspace_write]"), "须含段头");
        assertTrue(config.contains("network_access = true"), "须开放网络");
        assertFalse(config.contains("danger-full-access"), "非-darwin 不应回落全访问");
    }

    @Test
    void write_darwin_fallsBackToDangerFullAccess() throws IOException {
        System.setProperty("os.name", "Mac OS X");

        CodexSandboxConfig.write(codexHome, LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        assertTrue(config.contains("sandbox_mode = \"danger-full-access\""), "darwin 须回落全访问");
        assertFalse(config.contains("[sandbox_workspace_write]"), "darwin 不应含 workspace-write 段");
    }

    @Test
    void write_idempotent_singleBlockAfterDoubleWrite() throws IOException {
        System.setProperty("os.name", "Linux");

        CodexSandboxConfig.write(codexHome, LoggerFactory.getLogger(CodexSandboxConfigTest.class));
        CodexSandboxConfig.write(codexHome, LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        long beginCount = config.lines().filter(l -> l.equals(CodexSandboxConfig.BEGIN_MARKER)).count();
        assertEquals(1L, beginCount, "二次写入后仍只含一个托管块");
    }

    @Test
    void write_preservesUserContentOutsideManagedBlock() throws IOException {
        System.setProperty("os.name", "Linux");
        Files.writeString(codexHome.resolve("config.toml"),
                "model = \"gpt-5\"\n# 用户备注\n");

        CodexSandboxConfig.write(codexHome, LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        assertTrue(config.contains("model = \"gpt-5\""), "用户 model 行须保留");
        assertTrue(config.contains("# 用户备注"), "用户注释须保留");
        assertTrue(config.contains(CodexSandboxConfig.BEGIN_MARKER), "托管块须写入");
    }

    @Test
    void write_nonDarwin_containsMemoryAndMultiAgentDisables() throws IOException {
        System.setProperty("os.name", "Linux");

        CodexSandboxConfig.write(codexHome, LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        assertTrue(config.contains("features.memories = false"), "须禁用 memories（防跨任务记忆泄漏，multica#3130）");
        assertTrue(config.contains("features.multi_agent = false"), "须禁用 multi_agent（防 turn/completed 抖动）");
        assertTrue(config.contains("memories.generate_memories = false"), "须禁用记忆生成");
        assertTrue(config.contains("memories.use_memories = false"), "须禁用记忆使用");
    }

    @Test
    void write_darwin_containsMemoryAndMultiAgentDisables() throws IOException {
        System.setProperty("os.name", "Mac OS X");

        CodexSandboxConfig.write(codexHome, LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        assertTrue(config.contains("features.memories = false"), "darwin 亦须禁用 memories");
        assertTrue(config.contains("features.multi_agent = false"), "darwin 亦须禁用 multi_agent");
    }

    @Test
    void stripSkillsConfig_removesSkillsBlocksPreservingOtherTables() {
        String input = String.join("\n",
                "model = \"gpt-5\"",
                "[[skills.config]]",
                "name = \"foo\"",
                "",
                "[[skills.config]]",
                "name = \"bar\"",
                "",
                "[other]",
                "key = \"val\"");

        String result = CodexSandboxConfig.stripSkillsConfig(input);

        assertFalse(result.contains("[[skills.config]]"), "skills.config 段须全部剥离");
        assertFalse(result.contains("\"foo\""), "段内键值须随之移除");
        assertFalse(result.contains("\"bar\""), "段内键值须随之移除");
        assertTrue(result.contains("[other]"), "无关表头须保留");
        assertTrue(result.contains("key = \"val\""), "无关表内键值须保留");
        assertTrue(result.contains("model = \"gpt-5\""), "段外 top-level 键须保留");
    }

    @Test
    void write_stripsSkillsConfigFromUserContent() throws IOException {
        System.setProperty("os.name", "Linux");
        Files.writeString(codexHome.resolve("config.toml"),
                "[[skills.config]]\nname = \"desktop-skill\"\n\n[other]\nkey = \"val\"\n");

        CodexSandboxConfig.write(codexHome, LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        assertFalse(config.contains("[[skills.config]]"), "write 须剥离用户 skills.config 段（CLI 0.114 拒收缺 path）");
        assertFalse(config.contains("desktop-skill"), "段内键值须移除");
        assertTrue(config.contains("[other]"), "无关表须保留");
        assertTrue(config.contains("key = \"val\""), "无关键值须保留");
    }

    @Test
    void seedUserSkills_copiesTreeWhenSourceExists() throws IOException {
        Path userHome = Files.createTempDirectory("codex-userhome-test-");
        try {
            Path srcSkills = userHome.resolve(".codex/skills");
            Files.createDirectories(srcSkills.resolve("nested"));
            Files.writeString(srcSkills.resolve("top.md"), "# top skill");
            Files.writeString(srcSkills.resolve("nested/deep.md"), "# deep skill");

            CodexSandboxConfig.seedUserSkills(codexHome, userHome,
                    LoggerFactory.getLogger(CodexSandboxConfigTest.class));

            Path target = codexHome.resolve("skills");
            assertEquals("# top skill", Files.readString(target.resolve("top.md")),
                    "顶层 skill 文件须拷贝");
            assertEquals("# deep skill", Files.readString(target.resolve("nested/deep.md")),
                    "嵌套 skill 文件须拷贝");
        } finally {
            deleteTree(userHome);
        }
    }

    @Test
    void seedUserSkills_noopWhenSourceAbsent() {
        Path userHome = codexHome.resolve("nonexistent-userhome");

        CodexSandboxConfig.seedUserSkills(codexHome, userHome,
                LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        assertFalse(Files.exists(codexHome.resolve("skills")),
                "源 ~/.codex/skills 不存在时不应创建 target");
    }

    @Test
    void isDarwin_macOsName_returnsTrue() {
        System.setProperty("os.name", "Mac OS X");
        assertTrue(CodexSandboxConfig.isDarwin());
    }

    @Test
    void isDarwin_linuxOsName_returnsFalse() {
        System.setProperty("os.name", "Linux");
        assertFalse(CodexSandboxConfig.isDarwin());
    }

    private static void deleteTree(Path root) {
        if (root == null) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 测试临时区残留由 OS 清
                }
            });
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
