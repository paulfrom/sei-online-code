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
