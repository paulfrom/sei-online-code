package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.enums.WorkspaceSource;
import com.changhong.onlinecode.entity.PlatformConfig;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.service.ConfigService;
import com.changhong.onlinecode.service.GitApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
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
        assertTrue(Files.exists(tempDir.resolve("project-1/.git")));
        assertEquals("sei-online-code", runGit(tempDir.resolve("project-1"), "config", "user.name").trim());
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
    void resolveRequirementWorkspace_createsManagedCopyAndSkipsRegenerableArtifacts(@TempDir Path tempDir)
            throws Exception {
        ConfigService configService = mock(ConfigService.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        WorkspaceManager workspaceManager = new WorkspaceManager(projectDao, configService, new ScaffoldGenerator());

        PlatformConfig config = new PlatformConfig();
        config.setWorkspaceRoot(tempDir.toString());
        when(configService.get()).thenReturn(config);
        when(configService.resolveWorkspaceRoot(config)).thenReturn(tempDir.toString());

        Project project = new Project();
        project.setId("project-iso");
        when(projectDao.findOne("project-iso")).thenReturn(project);

        Path projectWorkspace = tempDir.resolve("project-iso");
        Files.createDirectories(projectWorkspace);
        Files.writeString(projectWorkspace.resolve("package.json"), "{}");
        Files.createDirectories(projectWorkspace.resolve("node_modules/pkg"));
        Files.writeString(projectWorkspace.resolve("node_modules/pkg/index.js"), "cached");
        Files.createDirectories(projectWorkspace.resolve(".next/cache"));
        Files.writeString(projectWorkspace.resolve(".next/cache/file"), "cached");

        Path isolated = workspaceManager.resolveRequirementWorkspace("project-iso", "req/1");

        assertTrue(isolated.startsWith(projectWorkspace.resolve(".sei").resolve("workspaces").toAbsolutePath().normalize()));
        assertTrue(Files.exists(isolated.resolve("package.json")));
        assertFalse(Files.exists(isolated.resolve("node_modules")));
        assertFalse(Files.exists(isolated.resolve(".next")));
    }

    @Test
    void resolveRequirementWorkspace_usesRequirementNoAsWorkspaceAndBranch(@TempDir Path tempDir)
            throws Exception {
        ConfigService configService = mock(ConfigService.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        RequirementDao requirementDao = mock(RequirementDao.class);
        WorkspaceManager workspaceManager = new WorkspaceManager(projectDao, configService, new ScaffoldGenerator());
        workspaceManager.setRequirementDao(requirementDao);

        PlatformConfig config = new PlatformConfig();
        config.setWorkspaceRoot(tempDir.toString());
        when(configService.get()).thenReturn(config);
        when(configService.resolveWorkspaceRoot(config)).thenReturn(tempDir.toString());

        Project project = new Project();
        project.setId("project-branch");
        when(projectDao.findOne("project-branch")).thenReturn(project);

        Requirement requirement = new Requirement();
        requirement.setId("req-1");
        requirement.setRequirementNo("REQ-0042");
        when(requirementDao.findOne("req-1")).thenReturn(requirement);

        Path isolated = workspaceManager.resolveRequirementWorkspace("project-branch", "req-1");

        assertTrue(isolated.endsWith(Path.of(".sei", "workspaces", "requirement-REQ-0042")));
        assertEquals("feature/REQ-0042", runGit(isolated, "branch", "--show-current").trim());
    }

    @Test
    void resolveRequirementWorkspace_serializesConcurrentInitialization(@TempDir Path tempDir)
            throws Exception {
        ConfigService configService = mock(ConfigService.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        WorkspaceManager workspaceManager = new WorkspaceManager(projectDao, configService, new ScaffoldGenerator());

        PlatformConfig config = new PlatformConfig();
        config.setWorkspaceRoot(tempDir.toString());
        when(configService.get()).thenReturn(config);
        when(configService.resolveWorkspaceRoot(config)).thenReturn(tempDir.toString());

        Project project = new Project();
        project.setId("project-concurrent");
        when(projectDao.findOne("project-concurrent")).thenReturn(project);

        int workerCount = 8;
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<CompletableFuture<Path>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workerCount; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    try {
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        return workspaceManager.resolveRequirementWorkspace("project-concurrent", "REQ-0001");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }, executor));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            Path expected = tempDir.resolve("project-concurrent/.sei/workspaces/requirement-REQ-0001")
                    .toAbsolutePath().normalize();
            for (CompletableFuture<Path> future : futures) {
                assertEquals(expected, future.get(15, TimeUnit.SECONDS));
            }
            assertEquals("feature/REQ-0001", runGit(expected, "branch", "--show-current").trim());
            assertFalse(Files.exists(expected.resolve(".git/index.lock")));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void resolveRequirementWorkspace_doesNotCheckoutAgainWhenAlreadyOnTargetBranch(@TempDir Path tempDir)
            throws Exception {
        ConfigService configService = mock(ConfigService.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        WorkspaceManager workspaceManager = new WorkspaceManager(projectDao, configService, new ScaffoldGenerator());

        PlatformConfig config = new PlatformConfig();
        config.setWorkspaceRoot(tempDir.toString());
        when(configService.get()).thenReturn(config);
        when(configService.resolveWorkspaceRoot(config)).thenReturn(tempDir.toString());

        Project project = new Project();
        project.setId("project-existing-branch");
        when(projectDao.findOne("project-existing-branch")).thenReturn(project);

        Path isolated = workspaceManager.resolveRequirementWorkspace("project-existing-branch", "REQ-0001");
        Path indexLock = isolated.resolve(".git/index.lock");
        Files.createFile(indexLock);
        try {
            assertEquals(isolated,
                    workspaceManager.resolveRequirementWorkspace("project-existing-branch", "REQ-0001"));
            assertEquals("feature/REQ-0001", runGit(isolated, "branch", "--show-current").trim());
        } finally {
            Files.deleteIfExists(indexLock);
        }
    }

    @Test
    void deleteRequirementWorkspace_removesOnlyRequirementWorkspace(@TempDir Path tempDir) throws Exception {
        ConfigService configService = mock(ConfigService.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        WorkspaceManager workspaceManager = new WorkspaceManager(projectDao, configService, new ScaffoldGenerator());

        PlatformConfig config = new PlatformConfig();
        config.setWorkspaceRoot(tempDir.toString());
        when(configService.get()).thenReturn(config);
        when(configService.resolveWorkspaceRoot(config)).thenReturn(tempDir.toString());

        Project project = new Project();
        project.setId("project-clean");
        when(projectDao.findOne("project-clean")).thenReturn(project);

        Path projectWorkspace = tempDir.resolve("project-clean");
        Files.createDirectories(projectWorkspace);
        Files.writeString(projectWorkspace.resolve("README.md"), "root");
        Path isolated = workspaceManager.resolveRequirementWorkspace("project-clean", "req-clean");
        Files.writeString(isolated.resolve("generated.txt"), "done");

        workspaceManager.deleteRequirementWorkspace("project-clean", "req-clean");

        assertTrue(Files.exists(projectWorkspace.resolve("README.md")));
        assertFalse(Files.exists(isolated));
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
        project.setProjectCode("inventory-app");
        when(projectDao.findOne("project-3")).thenReturn(project);

        var result = workspaceManager.resolve("project-3");

        assertEquals(WorkspaceSource.CLONE, result.getSource());
        Path workspace = Path.of(result.getPath());
        assertTrue(Files.exists(workspace.resolve("frontend/package.json")));
        assertTrue(Files.exists(workspace.resolve("backend/src/main/java/com/changhong/inventory/app/App.java")));
        assertTrue(Files.exists(workspace.resolve(".git")), "模板生成的目标工作区应初始化本地 Git");
        assertFalse(runGitAllowFailure(workspace, "remote", "get-url", "origin").success(), "模板仓库 origin 不应带入目标工作区");
        assertEquals("Project inventory-app", Files.readString(workspace.resolve("README.md")));
        assertTrue(Files.readString(workspace.resolve("frontend/package.json")).contains("inventory-app"));
        assertTrue(Files.readString(workspace.resolve("backend/src/main/java/com/changhong/inventory/app/App.java"))
                .contains("package com.changhong.inventory.app;"));
    }

    @Test
    void resolve_projectGitUrlMaterializesProjectContentBeforeTemplate(@TempDir Path tempDir) throws Exception {
        Path projectRepo = tempDir.resolve("project-repo");
        Files.createDirectories(projectRepo.resolve("backend/src/main/java/demo"));
        Files.writeString(projectRepo.resolve("backend/src/main/java/demo/App.java"),
                "class App { String literal = \"aaa_projectName_aaa\"; }", StandardCharsets.UTF_8);
        Files.writeString(projectRepo.resolve("README.md"), "Real project repository", StandardCharsets.UTF_8);

        runGit(projectRepo, "init");
        runGit(projectRepo, "config", "user.email", "test@example.com");
        runGit(projectRepo, "config", "user.name", "tester");
        runGit(projectRepo, "add", ".");
        runGit(projectRepo, "commit", "-m", "init");

        Path templateDir = tempDir.resolve("template-repo");
        Files.createDirectories(templateDir);
        Files.writeString(templateDir.resolve("README.md"), "Template repository", StandardCharsets.UTF_8);

        ConfigService configService = mock(ConfigService.class);
        ProjectDao projectDao = mock(ProjectDao.class);
        GitApi gitApi = mock(GitApi.class);
        doAnswer(invocation -> {
            Path destination = invocation.getArgument(1);
            runGit(tempDir, "clone", projectRepo.toUri().toString(), destination.toString());
            return null;
        }).when(gitApi).cloneRepository(eq(projectRepo.toUri().toString()), any(Path.class));
        WorkspaceManager workspaceManager = new WorkspaceManager(
                projectDao, configService, new ScaffoldGenerator(), gitApi);

        PlatformConfig config = new PlatformConfig();
        config.setWorkspaceRoot(tempDir.resolve("workspaces-project-git").toString());
        config.setTemplateGitlabUrl(templateDir.toUri().toString());
        when(configService.get()).thenReturn(config);
        when(configService.resolveWorkspaceRoot(config)).thenReturn(config.getWorkspaceRoot());

        Project project = new Project();
        project.setId("project-git");
        project.setName("真实项目");
        project.setGitUrl(projectRepo.toUri().toString());
        when(projectDao.findOne("project-git")).thenReturn(project);

        var result = workspaceManager.resolve("project-git");

        assertEquals(WorkspaceSource.CLONE, result.getSource());
        Path workspace = Path.of(result.getPath());
        assertEquals("Real project repository", Files.readString(workspace.resolve("README.md")));
        assertTrue(Files.exists(workspace.resolve("backend/src/main/java/demo/App.java")));
        assertTrue(Files.readString(workspace.resolve("backend/src/main/java/demo/App.java"))
                .contains("aaa_projectName_aaa"), "项目仓库内容应原样下载，不做模板占位替换");
        assertTrue(Files.exists(workspace.resolve(".git")), "项目仓库应通过 git clone 建立，保留 origin");
        assertEquals(projectRepo.toUri().toString(), runGit(workspace, "remote", "get-url", "origin").trim());
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

    private String runGit(Path dir, String... args) throws Exception {
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
        return output;
    }

    private GitResult runGitAllowFailure(Path dir, String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        return new GitResult(code, output);
    }

    private record GitResult(int code, String output) {
        boolean success() {
            return code == 0;
        }
    }
}
