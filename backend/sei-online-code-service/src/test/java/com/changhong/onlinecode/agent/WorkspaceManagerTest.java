package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.enums.WorkspaceSource;
import com.changhong.onlinecode.entity.PlatformConfig;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link WorkspaceManager} 单元测试（B35）：路径安全 + provision 来源决策。
 *
 * <p>验证 WHY：</p>
 * <ul>
 *   <li>isSafeRoot——工作区在根目录下按 projectId 建/删子目录，若把系统根/盘符根当作工作区根，
 *       后续 GC/清理会危及系统目录（数据丢失级）。故必须拒绝黑名单根，只放行安全的绝对路径。</li>
 *   <li>decideSource——契约 §3 规定：配置了模板地址则 CLONE、否则 SCAFFOLD（day-one 路径）。
 *       决策错误会导致该 clone 时误生成脚手架、或该生成时误 clone 空地址。</li>
 * </ul>
 *
 * @author sei-online-code
 */
class WorkspaceManagerTest {

    /** decideSource / isSafeRoot 不触碰 config 持久层与脚手架落盘，传 null 依赖即可纯逻辑单测。 */
    private final WorkspaceManager manager = new WorkspaceManager(null, null, new ScaffoldGenerator());

    @Test
    void isSafeRoot_rejectsSystemAndDriveRoots() {
        // 系统根 / 盘符根 —— 一律拒绝
        assertFalse(manager.isSafeRoot("/"), "根 / 必须拒绝");
        assertFalse(manager.isSafeRoot("/etc"), "/etc 必须拒绝");
        assertFalse(manager.isSafeRoot("/usr"), "/usr 必须拒绝");
        assertFalse(manager.isSafeRoot("/root"), "/root 必须拒绝");
        assertFalse(manager.isSafeRoot("/home"), "/home 必须拒绝");
        assertFalse(manager.isSafeRoot("/var/"), "带尾分隔符的系统根也须拒绝");
        assertFalse(manager.isSafeRoot("C:\\"), "盘符根 C:\\ 必须拒绝");
        assertFalse(manager.isSafeRoot("c:"), "盘符根 c: 必须拒绝（大小写不敏感）");
    }

    @Test
    void isSafeRoot_rejectsBlankAndRelative() {
        assertFalse(manager.isSafeRoot(null), "null 拒绝");
        assertFalse(manager.isSafeRoot("  "), "空白拒绝");
        assertFalse(manager.isSafeRoot("relative/dir"), "相对路径拒绝（落点不确定）");
    }

    @Test
    void isSafeRoot_acceptsSafeAbsoluteDir() {
        String safeRoot = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "sei-online-code").toAbsolutePath().toString();
        assertTrue(manager.isSafeRoot(safeRoot), "临时区下的专用工作区根应放行");
    }

    @Test
    void decideSource_cloneWhenTemplateUrlSet() {
        PlatformConfig config = new PlatformConfig();
        config.setTemplateGitlabUrl("https://gitlab.example.com/tpl/suid-template.git");
        assertEquals(WorkspaceSource.CLONE, manager.decideSource(config),
                "配置了模板地址 → CLONE");
    }

    @Test
    void decideSource_scaffoldWhenTemplateUrlEmptyOrNull() {
        PlatformConfig empty = new PlatformConfig();
        empty.setTemplateGitlabUrl("");
        assertEquals(WorkspaceSource.SCAFFOLD, manager.decideSource(empty),
                "空模板地址 → SCAFFOLD（day-one 路径）");

        PlatformConfig nullUrl = new PlatformConfig();
        assertEquals(WorkspaceSource.SCAFFOLD, manager.decideSource(nullUrl),
                "模板地址为 null → SCAFFOLD");

        assertEquals(WorkspaceSource.SCAFFOLD, manager.decideSource(null),
                "配置缺失 → SCAFFOLD");
    }

    @Test
    void resolve_scaffoldMaterializesWorkspace(@TempDir Path tempDir) throws Exception {
        ConfigService configService = mock(ConfigService.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        ScaffoldGenerator scaffoldGenerator = new ScaffoldGenerator();
        WorkspaceManager workspaceManager = new WorkspaceManager(projectDao, configService, scaffoldGenerator);

        PlatformConfig config = new PlatformConfig();
        config.setWorkspaceRoot(tempDir.toString());
        config.setTemplateGitlabUrl("");
        when(configService.get()).thenReturn(config);
        when(configService.resolveWorkspaceRoot(config)).thenReturn(tempDir.toString());

        Project project = new Project();
        project.setId("project-1");
        when(projectDao.findOne("project-1")).thenReturn(project);

        var result = workspaceManager.resolve("project-1");

        assertFalse(result.isProvisioned(), "首次解析应实际 provision 工作区");
        assertEquals(WorkspaceSource.SCAFFOLD, result.getSource());
        assertTrue(Files.exists(tempDir.resolve("project-1/package.json")));
        assertTrue(Files.exists(tempDir.resolve("project-1/.sei/workspace.json")));
        assertTrue(Files.exists(tempDir.resolve("project-1/.sei/runs/.gitkeep")));
        assertTrue(Files.exists(tempDir.resolve("project-1/README.md")));
    }

    @Test
    void resolve_respectsProjectWorkspacePath(@TempDir Path tempDir) throws Exception {
        ConfigService configService = mock(ConfigService.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        WorkspaceManager workspaceManager = new WorkspaceManager(projectDao, configService, new ScaffoldGenerator());

        PlatformConfig config = new PlatformConfig();
        config.setWorkspaceRoot(tempDir.toString());
        when(configService.get()).thenReturn(config);
        when(configService.resolveWorkspaceRoot(config)).thenReturn(tempDir.toString());

        Path customPath = tempDir.resolve("custom-root").resolve("project-2");
        Project project = new Project();
        project.setId("project-2");
        project.setWorkspacePath(customPath.toString());
        when(projectDao.findOne("project-2")).thenReturn(project);

        var result = workspaceManager.resolve("project-2");

        assertEquals(customPath.toString(), result.getPath());
        assertTrue(Files.exists(customPath.resolve(".sei/workspace.json")));
        assertDoesNotThrow(() -> Files.readString(customPath.resolve("README.md")));
    }

    @Test
    void resolve_templateMonoModeGeneratesWorkspace(@TempDir Path tempDir) throws Exception {
        Path templateDir = tempDir.resolve("mono-template");
        Files.createDirectories(templateDir.resolve("backend/src/main/java/aaa_packageNameDir_aaa"));
        Files.createDirectories(templateDir.resolve("frontend"));
        Files.writeString(templateDir.resolve("backend/src/main/java/aaa_packageNameDir_aaa/App.java"), """
                package aaa_packageName_aaa;

                public class App {
                    String project = "aaa_projectName_aaa";
                    String desc = "aaa_description_aaa";
                    String version = "aaa_projectVersion_aaa";
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(templateDir.resolve("frontend/package.json"),
                "{\"name\":\"aaa_projectName_aaa\",\"description\":\"aaa_description_aaa\"}",
                StandardCharsets.UTF_8);
        Files.writeString(templateDir.resolve("README.md"), "Project aaa_projectName_aaa", StandardCharsets.UTF_8);

        runGit(templateDir, "init");
        runGit(templateDir, "config", "user.email", "test@example.com");
        runGit(templateDir, "config", "user.name", "tester");
        runGit(templateDir, "add", ".");
        runGit(templateDir, "commit", "-m", "init");

        ConfigService configService = mock(ConfigService.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        WorkspaceManager workspaceManager = new WorkspaceManager(projectDao, configService, new ScaffoldGenerator());

        PlatformConfig config = new PlatformConfig();
        config.setWorkspaceRoot(tempDir.resolve("workspaces").toString());
        config.setTemplateGitlabUrl(templateDir.toUri().toString());
        when(configService.get()).thenReturn(config);
        when(configService.resolveWorkspaceRoot(config)).thenReturn(config.getWorkspaceRoot());

        Project project = new Project();
        project.setId("project-3");
        project.setName("库存管理台");
        project.setDesign("库存管理 mono 模板");
        project.setGitUrl("https://gitlab.example.com/group/inventory-app.git");
        when(projectDao.findOne("project-3")).thenReturn(project);

        var result = workspaceManager.resolve("project-3");

        assertEquals(WorkspaceSource.CLONE, result.getSource());
        Path workspace = Path.of(result.getPath());
        assertTrue(Files.exists(workspace.resolve("frontend/package.json")));
        assertTrue(Files.exists(workspace.resolve("backend/src/main/java/com/changhong/inventory/app/App.java")));
        assertFalse(Files.exists(workspace.resolve(".git")), "模板仓库历史不应带入目标工作区");
        assertEquals("Project inventory-app", Files.readString(workspace.resolve("README.md")));
        assertTrue(Files.readString(workspace.resolve("frontend/package.json")).contains("inventory-app"));
        assertTrue(Files.readString(workspace.resolve("backend/src/main/java/com/changhong/inventory/app/App.java"))
                .contains("package com.changhong.inventory.app;"));
    }

    @Test
    void resolve_templateMonoModeUsesConfiguredMetadata(@TempDir Path tempDir) throws Exception {
        Path templateDir = tempDir.resolve("mono-template-explicit");
        Files.createDirectories(templateDir.resolve("backend/src/main/java/aaa_packageNameDir_aaa"));
        Files.createDirectories(templateDir.resolve("frontend"));
        Files.writeString(templateDir.resolve("backend/src/main/java/aaa_packageNameDir_aaa/App.java"), """
                package aaa_packageName_aaa;
                class App {}
                """, StandardCharsets.UTF_8);
        Files.writeString(templateDir.resolve("frontend/package.json"),
                "{\"name\":\"aaa_projectName_aaa\",\"version\":\"aaa_projectVersion_aaa\"}",
                StandardCharsets.UTF_8);

        runGit(templateDir, "init");
        runGit(templateDir, "config", "user.email", "test@example.com");
        runGit(templateDir, "config", "user.name", "tester");
        runGit(templateDir, "add", ".");
        runGit(templateDir, "commit", "-m", "init");

        ConfigService configService = mock(ConfigService.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        WorkspaceManager workspaceManager = new WorkspaceManager(projectDao, configService, new ScaffoldGenerator());

        PlatformConfig config = new PlatformConfig();
        config.setWorkspaceRoot(tempDir.resolve("workspaces2").toString());
        config.setTemplateGitlabUrl(templateDir.toUri().toString());
        when(configService.get()).thenReturn(config);
        when(configService.resolveWorkspaceRoot(config)).thenReturn(config.getWorkspaceRoot());

        Project project = new Project();
        project.setId("project-4");
        project.setName("测试项目");
        project.setProjectCode("custom-app");
        project.setProjectVersion("2.3.4");
        project.setPackageName("com.demo.customized");
        when(projectDao.findOne("project-4")).thenReturn(project);

        var result = workspaceManager.resolve("project-4");

        Path workspace = Path.of(result.getPath());
        assertTrue(Files.readString(workspace.resolve("frontend/package.json")).contains("\"custom-app\""));
        assertTrue(Files.readString(workspace.resolve("frontend/package.json")).contains("\"2.3.4\""));
        assertTrue(Files.exists(workspace.resolve("backend/src/main/java/com/demo/customized/App.java")));
        assertTrue(Files.readString(workspace.resolve("backend/src/main/java/com/demo/customized/App.java"))
                .contains("package com.demo.customized;"));
    }

    private void runGit(Path dir, String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        assertEquals(0, code, output);
    }
}
