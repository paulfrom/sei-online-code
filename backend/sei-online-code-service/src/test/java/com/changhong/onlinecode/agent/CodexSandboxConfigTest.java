package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void write_nonDarwin_includesProjectWorkspaceWritableRoot() throws IOException {
        System.setProperty("os.name", "Linux");
        Path workspace = codexHome.resolve("workspace");

        CodexSandboxConfig.write(codexHome, workspace, LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        assertTrue(config.contains("writable_roots = [\"" + workspace.toAbsolutePath().normalize() + "\"]"),
                "workspace-write 须显式授权项目工作区可写");
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
    void linkSharedHome_symlinksAuthJsonFromSharedHome() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("windows"),
                "Windows 无符号链接权限时实现按契约回退文件复制");
        Path sharedHome = Files.createTempDirectory("codex-shared-test-");
        try {
            Files.writeString(sharedHome.resolve("auth.json"), "{\"tokens\":\"abc\"}");

            CodexSandboxConfig.linkSharedHome(codexHome, sharedHome,
                    LoggerFactory.getLogger(CodexSandboxConfigTest.class));

            Path auth = codexHome.resolve("auth.json");
            assertTrue(Files.isSymbolicLink(auth), "auth.json 须为指向共享源的符号链接");
            assertEquals("{\"tokens\":\"abc\"}", Files.readString(auth),
                    "符号链接须可读到共享 auth.json 内容");
        } finally {
            deleteTree(sharedHome);
        }
    }

    @Test
    void linkSharedHome_symlinksSessionsAndPluginsCacheDirs() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("windows"),
                "Windows 无符号链接权限时目录共享为 best-effort");
        Path sharedHome = Files.createTempDirectory("codex-shared-test-");
        try {
            CodexSandboxConfig.linkSharedHome(codexHome, sharedHome,
                    LoggerFactory.getLogger(CodexSandboxConfigTest.class));

            assertTrue(Files.isSymbolicLink(codexHome.resolve("sessions")),
                    "sessions/ 须符号链接到共享源（codex 会话日志写共享家目录）");
            assertTrue(Files.isSymbolicLink(codexHome.resolve("plugins/cache")),
                    "plugins/cache 须符号链接到共享源（插件缓存复用）");
        } finally {
            deleteTree(sharedHome);
        }
    }

    @Test
    void linkSharedHome_copiesInstructionsMdAndConfigJsonIsolated() throws IOException {
        Path sharedHome = Files.createTempDirectory("codex-shared-test-");
        try {
            Files.writeString(sharedHome.resolve("instructions.md"), "# shared instructions");
            Files.writeString(sharedHome.resolve("config.json"), "{\"theme\":\"dark\"}");

            CodexSandboxConfig.linkSharedHome(codexHome, sharedHome,
                    LoggerFactory.getLogger(CodexSandboxConfigTest.class));

            assertEquals("# shared instructions", Files.readString(codexHome.resolve("instructions.md")),
                    "instructions.md 须隔离拷贝（per-run 修改不回传共享）");
            assertEquals("{\"theme\":\"dark\"}", Files.readString(codexHome.resolve("config.json")),
                    "config.json 须隔离拷贝");
        } finally {
            deleteTree(sharedHome);
        }
    }

    @Test
    void linkSharedHome_noopForMissingFileSources() throws IOException {
        Path sharedHome = Files.createTempDirectory("codex-shared-test-");
        try {
            // 共享家目录无 auth.json/instructions.md/config.json
            CodexSandboxConfig.linkSharedHome(codexHome, sharedHome,
                    LoggerFactory.getLogger(CodexSandboxConfigTest.class));

            assertFalse(Files.exists(codexHome.resolve("auth.json")),
                    "共享 auth.json 缺失时 per-run 不应创建（未登录态）");
            assertFalse(Files.exists(codexHome.resolve("instructions.md")),
                    "共享 instructions.md 缺失时不应残留 stale 拷贝");
            assertFalse(Files.exists(codexHome.resolve("config.json")),
                    "共享 config.json 缺失时不应残留 stale 拷贝");
        } finally {
            deleteTree(sharedHome);
        }
    }

    @Test
    void linkSharedHome_idempotentAcrossInvokes() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("windows"),
                "Windows 文件链接回退为复制，幂等语义由内容测试覆盖");
        Path sharedHome = Files.createTempDirectory("codex-shared-test-");
        try {
            Files.writeString(sharedHome.resolve("auth.json"), "{}");

            CodexSandboxConfig.linkSharedHome(codexHome, sharedHome,
                    LoggerFactory.getLogger(CodexSandboxConfigTest.class));
            CodexSandboxConfig.linkSharedHome(codexHome, sharedHome,
                    LoggerFactory.getLogger(CodexSandboxConfigTest.class));

            assertTrue(Files.isSymbolicLink(codexHome.resolve("auth.json")),
                    "二次调用后 auth.json 仍为符号链接（不重复创建/不抛）");
        } finally {
            deleteTree(sharedHome);
        }
    }

    @Test
    void linkSharedHome_refreshesIsolatedCopyWhenSourceChanges() throws IOException {
        Path sharedHome = Files.createTempDirectory("codex-shared-test-");
        try {
            Files.writeString(sharedHome.resolve("instructions.md"), "v1");
            CodexSandboxConfig.linkSharedHome(codexHome, sharedHome,
                    LoggerFactory.getLogger(CodexSandboxConfigTest.class));
            Files.writeString(sharedHome.resolve("instructions.md"), "v2");
            CodexSandboxConfig.linkSharedHome(codexHome, sharedHome,
                    LoggerFactory.getLogger(CodexSandboxConfigTest.class));

            assertEquals("v2", Files.readString(codexHome.resolve("instructions.md")),
                    "syncCopy 须刷新（multica MUL-2646：避免卡首次播种快照）");
        } finally {
            deleteTree(sharedHome);
        }
    }

    // ---- PR3 #6 MCP 托管块 ----

    @Test
    void writeMcpBlock_rendersStdioServerBlock() throws IOException {
        String mcpConfig = "{\"mcpServers\":{\"fetch\":{\"command\":\"uvx\",\"args\":[\"mcp-server-fetch\"]}}}";

        CodexSandboxConfig.writeMcpBlock(codexHome, mcpConfig,
                LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        assertTrue(config.contains(CodexSandboxConfig.MCP_BEGIN_MARKER), "须含托管块 BEGIN marker");
        assertTrue(config.contains(CodexSandboxConfig.MCP_END_MARKER), "须含托管块 END marker");
        assertTrue(config.contains("[mcp_servers.fetch]"), "须渲染 stdio server 表头");
        assertTrue(config.contains("command = \"uvx\""), "须含 command 键");
        assertTrue(config.contains("args = [\"mcp-server-fetch\"]"), "须含 args 数组（inline TOML）");
    }

    @Test
    void writeMcpBlock_normalizesHttpServer() throws IOException {
        String mcpConfig = "{\"mcpServers\":{\"remote\":{\"type\":\"http\",\"url\":\"https://x/mcp\",\"headers\":{\"a\":\"b\"}}}}";

        CodexSandboxConfig.writeMcpBlock(codexHome, mcpConfig,
                LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        assertTrue(config.contains("http_headers"), "http server 须 headers→http_headers 归一化");
        assertTrue(config.contains("experimental_use_rmcp_client = true"), "http server 须置 rmcp client");
        assertTrue(config.contains("url = \"https://x/mcp\""), "须含 url");
        assertFalse(config.contains("type ="), "须丢弃 type 键（codex 不接受）");
    }

    @Test
    void writeMcpBlock_emptySetWritesEmptyMarkerBlock() throws IOException {
        // "{}" = 托管空集（strict，禁用户全局 MCP）——仍写 marker 块 pin 状态于磁盘
        CodexSandboxConfig.writeMcpBlock(codexHome, "{}",
                LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        assertTrue(config.contains(CodexSandboxConfig.MCP_BEGIN_MARKER), "空集仍须写 BEGIN marker");
        assertTrue(config.contains(CodexSandboxConfig.MCP_END_MARKER), "空集仍须写 END marker");
        assertFalse(config.contains("[mcp_servers."), "空集不应渲染任何 server 表");
    }

    @Test
    void writeMcpBlock_stripsUserMcpTablesWhenManaged() throws IOException {
        Files.writeString(codexHome.resolve("config.toml"),
                "[mcp_servers.legacy]\ncommand = \"old\"\n\n[other]\nkey = \"val\"\n");

        CodexSandboxConfig.writeMcpBlock(codexHome,
                "{\"mcpServers\":{\"fetch\":{\"command\":\"uvx\"}}}",
                LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        assertFalse(config.contains("legacy"), "托管时须剥用户全局 [mcp_servers.*] 表（防 TOML 重定义）");
        assertFalse(config.contains("\"old\""), "用户 server 段体须随表头移除");
        assertTrue(config.contains("[mcp_servers.fetch]"), "须渲染托管 server");
        assertTrue(config.contains("[other]"), "无关表须保留");
    }

    @Test
    void writeMcpBlock_nullStripsPriorManagedBlockAndLeavesUserTables() throws IOException {
        Files.writeString(codexHome.resolve("config.toml"),
                CodexSandboxConfig.MCP_BEGIN_MARKER + "\n[mcp_servers.managed]\ncommand = \"x\"\n"
                        + CodexSandboxConfig.MCP_END_MARKER + "\n\n[model]\nname = \"gpt-5\"\n");

        CodexSandboxConfig.writeMcpBlock(codexHome, null,
                LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        assertFalse(config.contains(CodexSandboxConfig.MCP_BEGIN_MARKER), "null mcpConfig 须剥既有托管块");
        assertFalse(config.contains("[mcp_servers.managed]"), "托管块内 server 须移除");
        assertTrue(config.contains("[model]"), "非托管用户内容须保留（CLI 默认回退）");
    }

    @Test
    void writeMcpBlock_idempotentSingleBlockAfterDoubleWrite() throws IOException {
        String mcpConfig = "{\"mcpServers\":{\"fetch\":{\"command\":\"uvx\"}}}";

        CodexSandboxConfig.writeMcpBlock(codexHome, mcpConfig,
                LoggerFactory.getLogger(CodexSandboxConfigTest.class));
        CodexSandboxConfig.writeMcpBlock(codexHome, mcpConfig,
                LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        String config = Files.readString(codexHome.resolve("config.toml"));
        long beginCount = config.lines().filter(l -> l.equals(CodexSandboxConfig.MCP_BEGIN_MARKER)).count();
        assertEquals(1L, beginCount, "二次写入后仍只含一个 MCP 托管块");
    }

    @Test
    void writeMcpBlock_rejectsNonBareTomlKeyName() {
        // server 名含 "." 非 Codex bare-key → 拒收（防生成非法 TOML）
        String mcpConfig = "{\"mcpServers\":{\"foo.bar\":{\"command\":\"uvx\"}}}";
        assertThrows(IOException.class, () ->
                CodexSandboxConfig.writeMcpBlock(codexHome, mcpConfig,
                        LoggerFactory.getLogger(CodexSandboxConfigTest.class)));
    }

    @Test
    void writeMcpBlock_setsOwnerOnlyPermissionsOnPosix() throws IOException {
        Assumptions.assumeTrue(codexHome.getFileSystem().supportedFileAttributeViews().contains("posix"),
                "仅 POSIX 文件系统验证 0o600");

        CodexSandboxConfig.writeMcpBlock(codexHome,
                "{\"mcpServers\":{\"fetch\":{\"command\":\"uvx\"}}}",
                LoggerFactory.getLogger(CodexSandboxConfigTest.class));

        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(codexHome.resolve("config.toml"));
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms,
                "config.toml 须 0o600（mcp_servers.<id>.env 可载 secrets，防 world-readable）");
    }

    @Test
    void stripCodexUserMcpServerTables_removesMcpTablesPreservingOthers() {
        String input = String.join("\n",
                "[mcp_servers.legacy]",
                "command = \"old\"",
                "",
                "[mcp_servers.fetch.env]",
                "API_KEY = \"secret\"",
                "",
                "[other]",
                "key = \"val\"");

        String result = CodexSandboxConfig.stripCodexUserMcpServerTables(input);

        assertFalse(result.contains("[mcp_servers.legacy]"), "须剥 mcp_servers 表头");
        assertFalse(result.contains("[mcp_servers.fetch.env]"), "须连同子表剥除");
        assertFalse(result.contains("secret"), "段体内键值须移除");
        assertTrue(result.contains("[other]"), "无关表须保留");
        assertTrue(result.contains("key = \"val\""), "无关表内容须保留");
    }

    @Test
    void hasManagedCodexMcpConfig_threeStateSemantics() {
        assertFalse(CodexSandboxConfig.hasManagedCodexMcpConfig(null), "null = 不托管");
        assertFalse(CodexSandboxConfig.hasManagedCodexMcpConfig(""), "blank = 不托管");
        assertFalse(CodexSandboxConfig.hasManagedCodexMcpConfig("   "), "空白 = 不托管");
        assertFalse(CodexSandboxConfig.hasManagedCodexMcpConfig("null"), "字面 null = 不托管");
        assertTrue(CodexSandboxConfig.hasManagedCodexMcpConfig("{}"), "{} = 托管空集（strict）");
        assertTrue(CodexSandboxConfig.hasManagedCodexMcpConfig("{\"mcpServers\":{}}"),
                "空 mcpServers = 托管空集");
        assertTrue(CodexSandboxConfig.hasManagedCodexMcpConfig("{\"mcpServers\":{\"x\":{}}}"),
                "有 server = 托管");
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
