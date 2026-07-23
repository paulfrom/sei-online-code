package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.WorkspaceManager;
import com.changhong.onlinecode.config.OcConfig;
import com.changhong.onlinecode.entity.PlatformConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConfigService#resolveWorkspaceRoot} 的 trim 行为单测（缺陷修复回归）。
 *
 * <p>验证 WHY：{@code workspaceRoot} 若带前导/尾部空白（如 {@code "\n  /home/.../data"}，
 * 常见于多行 env 导出或 YAML 缩进），{@code WorkspaceManager.isSafeRoot} 的
 * {@code new File(root).isAbsolute()} 用原始 root → Linux 下首字符非 {@code /} → 误判不安全 →
 * 抛 {@code IllegalStateException: 不安全的工作区根}。在 {@code resolveWorkspaceRoot} 返回前 trim，
 * 使 DB 与 env 两个来源的 incidental 空白被统一清掉，{@code isSafeRoot} 收到的总是干净路径。</p>
 *
 * @author sei-online-code
 */
class ConfigServiceTest {

    private final OcConfig ocConfig = new OcConfig();

    /** resolveWorkspaceRoot 不触碰 dao/entityManager，传 null 即可纯逻辑单测。 */
    private final ConfigService service = new ConfigService(null, ocConfig);

    @Test
    void trimsWhitespaceFromConfigWorkspaceRoot() {
        PlatformConfig config = new PlatformConfig();
        String expected = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "sei-online-code", "project", "data")
                .toAbsolutePath().toString();
        // 复现线上输入：前导换行+空格、尾部空格
        config.setWorkspaceRoot("\n  " + expected + "  ");

        String resolved = service.resolveWorkspaceRoot(config);

        assertEquals(expected, resolved,
                "config.workspaceRoot 的前后空白须被 trim");
        // trim 后须能通过 isSafeRoot（证明修复闭环：原脏值会被 isSafeRoot 误拒）
        assertTrue(new WorkspaceManager(null, null, null).isSafeRoot(resolved),
                "trim 后的绝对路径须通过 isSafeRoot");
    }

    @Test
    void trimsWhitespaceFromEnvWorkspaceRoot() throws Exception {
        String expected = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "sei-online-code", "project", "data")
                .toAbsolutePath().toString();
        setEnvWorkspaceRoot("\n  " + expected + "  ");

        // config 无 workspaceRoot → 走 env 分支
        String resolved = service.resolveWorkspaceRoot(new PlatformConfig());

        assertEquals(expected, resolved,
                "env oc.workspace.root 的前后空白须被 trim");
        assertTrue(new WorkspaceManager(null, null, null).isSafeRoot(resolved),
                "trim 后的绝对路径须通过 isSafeRoot");
    }

    @Test
    void blankConfigFallsThroughToEnv() throws Exception {
        // config.workspaceRoot 纯空白 → 视为未配置 → 走 env-fallback（对齐 isNotBlank 语义）
        setEnvWorkspaceRoot("/tmp/sei-online-code");

        PlatformConfig config = new PlatformConfig();
        config.setWorkspaceRoot("   ");

        assertEquals("/tmp/sei-online-code", service.resolveWorkspaceRoot(config),
                "纯空白 workspaceRoot 等同未配置，走 env-fallback");
    }

    @Test
    void resolvesGitlabHostFromConfigBeforeEnvironment() throws Exception {
        setField("gitlabHost", " https://env.gitlab.example.com ");
        PlatformConfig config = new PlatformConfig();
        config.setGitlabHost(" https://db.gitlab.example.com ");

        assertEquals("https://db.gitlab.example.com", service.resolveGitlabHost(config));
    }

    @Test
    void resolvesGitlabHostFromEnvironmentWhenConfigBlank() throws Exception {
        setField("gitlabHost", " https://env.gitlab.example.com ");

        assertEquals("https://env.gitlab.example.com",
                service.resolveGitlabHost(new PlatformConfig()));
    }

    private void setEnvWorkspaceRoot(String value) throws Exception {
        setField("workspaceRoot", value);
    }

    private void setField(String fieldName, String value) throws Exception {
        Field f = OcConfig.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(ocConfig, value);
    }
}
