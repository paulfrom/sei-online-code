package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.WorkspaceSource;
import com.changhong.onlinecode.entity.PlatformConfig;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.service.ConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WorkspaceManager（B35）。契约 Phase 5 §3，蓝图参照 multica {@code daemon/local_directory.go} + {@code repocache/cache.go}。
 *
 * <p>在可配置的 Workspace Root 下解析每个项目的工作区目录，语义为 <b>clone-once + reuse</b>：
 * 目录已存在即复用（后续 Build Loop 回合原地增量编辑，绝不重复 clone/生成）；不存在则按平台配置
 * 是否设置 templateGitlabUrl 决定 provision 来源——有地址走 CLONE、空则走 SCAFFOLD 生成 canonical SUID 脚手架。</p>
 *
 * <p>本实现不再停留在 compile-only：resolve 会真实创建项目物理工作区。平台自有运行物料统一落在
 * 工作区的 {@code .sei/} 目录，代码执行阶段写入 AGENTS.md / CLAUDE.md、技能目录和代码文件时，
 * 都有明确且稳定的落点。</p>
 *
 * @author sei-online-code
 */
@Component
public class WorkspaceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceManager.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MANIFEST_PATH = ".sei/workspace.json";
    private static final Pattern REPLACE_PATTERN = Pattern.compile("([a-zA-Z]+)_(\\w+)_([a-zA-Z]+)");
    private static final String DEFAULT_PROJECT_VERSION = "1.0.0-SNAPSHOT";
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "jpeg", "jpg", "png", "gif", "svg", "ico", "bmp", "webp", "jar", "zip", "gz", "eot",
            "ttf", "woff", "woff2", "pdf", "mp4", "mov", "avi", "class", "so", "dll", "dylib"
    );

    /**
     * 禁止作为工作区根的黑名单（参照 local_directory.go）：系统根 / 盘符根 / 常见系统目录。
     * 命中任一即拒绝——工作区在其下按 projectId 建/删子目录，落到这些根会危及系统。
     */
    private static final Set<String> BLACKLIST_ROOTS = Set.of(
            "/", "/root", "/home", "/etc", "/usr", "/bin", "/boot", "/dev", "/lib",
            "/proc", "/sys", "/var", "/opt", "/sbin",
            "c:\\", "c:", "d:\\", "d:");

    private final ProjectDao projectDao;
    private final ConfigService configService;
    private final ScaffoldGenerator scaffoldGenerator;

    public WorkspaceManager(ProjectDao projectDao, ConfigService configService, ScaffoldGenerator scaffoldGenerator) {
        this.projectDao = projectDao;
        this.configService = configService;
        this.scaffoldGenerator = scaffoldGenerator;
    }

    /**
     * 解析项目工作区目录（契约 §3）。
     *
     * <p>优先尊重项目自身已保存的 {@code workspacePath}；缺失时回退到平台级
     * {@code <workspaceRoot>/<projectId>} 约定路径。目录不存在时立即 provision。</p>
     *
     * @param projectId 项目 id
     * @return 解析结果（path/provisioned/source）
     */
    public WorkspaceResolveResult resolve(String projectId) {
        PlatformConfig config = configService.get();
        String root = configService.resolveWorkspaceRoot(config);

        if (!isSafeRoot(root)) {
            throw new IllegalStateException("不安全的工作区根，拒绝解析: " + root);
        }

        Project project = projectDao == null ? null : projectDao.findOne(projectId);
        String dir = resolveWorkspacePath(projectId, project, root);
        ensureSafeWorkspacePath(dir);

        Path workspaceDir = Path.of(dir);
        WorkspaceManifest manifest = readManifest(workspaceDir);

        if (Files.exists(workspaceDir)) {
            WorkspaceSource source = manifest == null ? decideSource(config) : manifest.source();
            ensureManagedLayout(workspaceDir);
            writeManifest(workspaceDir, new WorkspaceManifest(
                    projectId,
                    source,
                    project == null ? null : project.getName(),
                    config == null ? null : config.getTemplateGitlabUrl(),
                    workspaceDir.toString(),
                    manifest == null ? OffsetDateTime.now().toString() : manifest.createdAt()
            ));
            return new WorkspaceResolveResult(dir, true, source);
        }

        WorkspaceSource source = decideSource(config);
        if (source == WorkspaceSource.CLONE) {
            materializeTemplateWorkspace(config, project, workspaceDir);
        } else {
            scaffoldWorkspace(workspaceDir);
        }
        ensureManagedLayout(workspaceDir);
        writeManifest(workspaceDir, new WorkspaceManifest(
                projectId,
                source,
                project == null ? null : project.getName(),
                config == null ? null : config.getTemplateGitlabUrl(),
                workspaceDir.toString(),
                OffsetDateTime.now().toString()
        ));
        return new WorkspaceResolveResult(dir, false, source);
    }

    /**
     * 决定 provision 来源：配置了 templateGitlabUrl → CLONE；否则 SCAFFOLD（契约 §3）。
     *
     * @param config 平台配置（可空，视为无模板地址）
     * @return CLONE 或 SCAFFOLD
     */
    public WorkspaceSource decideSource(PlatformConfig config) {
        if (config != null && config.getTemplateGitlabUrl() != null
                && !config.getTemplateGitlabUrl().isBlank()) {
            return WorkspaceSource.CLONE;
        }
        return WorkspaceSource.SCAFFOLD;
    }

    /**
     * 工作区根安全判定（黑名单，参照 local_directory.go）：拒绝空/系统根/盘符根/系统目录。
     *
     * @param root 工作区根路径
     * @return 安全（非黑名单、非空、绝对路径）则 true
     */
    public boolean isSafeRoot(String root) {
        if (root == null || root.isBlank()) {
            return false;
        }
        String normalized = normalize(root);
        if (BLACKLIST_ROOTS.contains(normalized)) {
            return false;
        }
        return new File(root).isAbsolute();
    }

    private String resolveWorkspacePath(String projectId, Project project, String root) {
        if (project != null && project.getWorkspacePath() != null && !project.getWorkspacePath().isBlank()) {
            return project.getWorkspacePath().trim();
        }
        return Path.of(root, projectId).toString();
    }

    private void ensureSafeWorkspacePath(String workspacePath) {
        File workspaceDir = new File(workspacePath);
        if (!workspaceDir.isAbsolute()) {
            throw new IllegalStateException("工作区路径必须是绝对路径: " + workspacePath);
        }
        File parent = workspaceDir.getParentFile();
        if (parent == null || !isSafeRoot(parent.getAbsolutePath())) {
            throw new IllegalStateException("工作区父目录不安全，拒绝解析: " + workspacePath);
        }
    }

    private void scaffoldWorkspace(Path workspaceDir) {
        try {
            Files.createDirectories(workspaceDir);
            for (ScaffoldGenerator.ScaffoldFile file : scaffoldGenerator.generate()) {
                writeFile(workspaceDir.resolve(file.path()), scaffoldGenerator.contentOf(file.path()));
            }
            LOGGER.info("workspace: 已生成脚手架 dir={}, files={}", workspaceDir, scaffoldGenerator.generate().size());
        } catch (IOException e) {
            throw new IllegalStateException("初始化脚手架工作区失败: " + workspaceDir, e);
        }
    }

    private void materializeTemplateWorkspace(PlatformConfig config, Project project, Path workspaceDir) {
        String templateUrl = config == null ? null : config.getTemplateGitlabUrl();
        if (templateUrl == null || templateUrl.isBlank()) {
            throw new IllegalStateException("模板仓库地址为空，无法执行 clone");
        }
        Path cloneDir = workspaceDir.getParent().resolve(workspaceDir.getFileName() + ".template-" + UUID.randomUUID());
        try {
            Path parent = workspaceDir.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Process process = new ProcessBuilder("git", "clone", templateUrl.trim(), cloneDir.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException("git clone 失败: " + output.trim());
            }
            generateFromTemplate(cloneDir, workspaceDir, project);
            LOGGER.info("workspace: 已从模板生成 dir={}, template={}", workspaceDir, templateUrl);
        } catch (IOException e) {
            deleteTree(workspaceDir);
            throw new IllegalStateException("执行 git clone 失败: " + templateUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteTree(workspaceDir);
            throw new IllegalStateException("执行 git clone 被中断: " + templateUrl, e);
        } finally {
            deleteTree(cloneDir);
        }
    }

    private void generateFromTemplate(Path templateDir, Path workspaceDir, Project project) throws IOException {
        Files.createDirectories(workspaceDir);
        Map<String, String> replaceData = buildReplaceData(project);
        Map<String, String> backendReplaceData = new HashMap<>(replaceData);
        putPackageData(backendReplaceData, derivePackageName(project));

        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(templateDir)) {
            stream.filter(Files::isRegularFile).forEach(files::add);
        }

        for (Path sourceFile : files) {
            Path relativePath = templateDir.relativize(sourceFile);
            if (relativePath.startsWith(".git")) {
                continue;
            }
            String relative = relativePath.toString().replace(File.separatorChar, '/');
            if ("template_config.json".equals(relative)) {
                continue;
            }

            Map<String, String> effectiveData = relative.startsWith("backend/") ? backendReplaceData : replaceData;
            String resolvedRelative = replacePlaceholders(relative, effectiveData);
            Path targetFile = workspaceDir.resolve(resolvedRelative);
            Path targetParent = targetFile.getParent();
            if (targetParent != null) {
                Files.createDirectories(targetParent);
            }

            if (isBinaryFile(sourceFile.getFileName().toString())) {
                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                continue;
            }

            String content = Files.readString(sourceFile, StandardCharsets.UTF_8);
            content = replacePlaceholders(content, effectiveData);
            Files.writeString(targetFile, content, StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> buildReplaceData(Project project) {
        Map<String, String> replaceData = new HashMap<>();
        replaceData.put("projectName", deriveProjectCode(project));
        replaceData.put("description", project == null || project.getDesign() == null || project.getDesign().isBlank()
                ? (project == null ? "" : nullToEmpty(project.getName()))
                : project.getDesign().trim());
        replaceData.put("projectVersion", deriveProjectVersion(project));
        return replaceData;
    }

    private void putPackageData(Map<String, String> replaceData, String packageName) {
        replaceData.put("packageName", packageName);
        replaceData.put("packageNameDir", packageName.replace('.', '/'));
    }

    private String deriveProjectCode(Project project) {
        if (project != null && project.getProjectCode() != null && !project.getProjectCode().isBlank()) {
            String code = slugify(project.getProjectCode());
            if (!code.isBlank()) {
                return code;
            }
        }
        if (project != null && project.getGitUrl() != null && !project.getGitUrl().isBlank()) {
            String gitUrl = project.getGitUrl().trim();
            int slash = Math.max(gitUrl.lastIndexOf('/'), gitUrl.lastIndexOf(':'));
            String repo = slash >= 0 ? gitUrl.substring(slash + 1) : gitUrl;
            if (repo.endsWith(".git")) {
                repo = repo.substring(0, repo.length() - 4);
            }
            String slug = slugify(repo);
            if (!slug.isBlank()) {
                return slug;
            }
        }
        if (project != null && project.getName() != null && !project.getName().isBlank()) {
            String slug = slugify(project.getName());
            if (!slug.isBlank()) {
                return slug;
            }
        }
        return project == null ? "generated-project" : slugify(nullToEmpty(project.getId(), "generated-project"));
    }

    private String deriveProjectVersion(Project project) {
        if (project != null && project.getProjectVersion() != null && !project.getProjectVersion().isBlank()) {
            return project.getProjectVersion().trim();
        }
        return DEFAULT_PROJECT_VERSION;
    }

    private String derivePackageName(Project project) {
        if (project != null && project.getPackageName() != null && !project.getPackageName().isBlank()) {
            return normalizePackageName(project.getPackageName());
        }
        String code = deriveProjectCode(project);
        String normalized = code.replace('-', '.');
        normalized = normalized.replaceAll("[^a-zA-Z0-9_.]", ".");
        normalized = normalized.replaceAll("\\.+", ".");
        normalized = normalized.replaceAll("^\\.|\\.$", "");
        if (normalized.isBlank()) {
            normalized = "generated";
        }
        String[] rawSegments = normalized.split("\\.");
        List<String> segments = new ArrayList<>();
        segments.add("com");
        segments.add("changhong");
        for (String raw : rawSegments) {
            String segment = raw.toLowerCase().replaceAll("[^a-z0-9_]", "");
            if (segment.isBlank()) {
                continue;
            }
            if (!Character.isJavaIdentifierStart(segment.charAt(0))) {
                segment = "x" + segment;
            }
            segments.add(segment);
        }
        return String.join(".", segments);
    }

    private String normalizePackageName(String rawPackageName) {
        String normalized = rawPackageName.trim().replace('-', '.');
        normalized = normalized.replaceAll("[^a-zA-Z0-9_.]", ".");
        normalized = normalized.replaceAll("\\.+", ".");
        normalized = normalized.replaceAll("^\\.|\\.$", "");
        if (normalized.isBlank()) {
            return "com.changhong.generated";
        }
        String[] rawSegments = normalized.split("\\.");
        List<String> segments = new ArrayList<>();
        for (String raw : rawSegments) {
            String segment = raw.toLowerCase().replaceAll("[^a-z0-9_]", "");
            if (segment.isBlank()) {
                continue;
            }
            if (!Character.isJavaIdentifierStart(segment.charAt(0))) {
                segment = "x" + segment;
            }
            segments.add(segment);
        }
        if (segments.isEmpty()) {
            return "com.changhong.generated";
        }
        return String.join(".", segments);
    }

    private String slugify(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    private String replacePlaceholders(String text, Map<String, String> replaceData) {
        Matcher matcher = REPLACE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(2);
            String value = replaceData.get(key);
            if (value != null && matcher.group(1).equals(matcher.group(3))) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private boolean isBinaryFile(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        return BINARY_EXTENSIONS.contains(ext);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullToEmpty(String value, String fallback) {
        String normalized = nullToEmpty(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private void ensureManagedLayout(Path workspaceDir) {
        for (String relativePath : scaffoldGenerator.managedPaths()) {
            try {
                writeFile(workspaceDir.resolve(relativePath), scaffoldGenerator.contentOf(relativePath));
            } catch (IOException e) {
                throw new IllegalStateException("写入工作区平台物料失败: " + workspaceDir.resolve(relativePath), e);
            }
        }
    }

    private WorkspaceManifest readManifest(Path workspaceDir) {
        Path manifestPath = workspaceDir.resolve(MANIFEST_PATH);
        if (!Files.exists(manifestPath)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(Files.readString(manifestPath, StandardCharsets.UTF_8), WorkspaceManifest.class);
        } catch (IOException e) {
            LOGGER.warn("workspace manifest 读取失败，path={}", manifestPath, e);
            return null;
        }
    }

    private void writeManifest(Path workspaceDir, WorkspaceManifest manifest) {
        try {
            Path target = workspaceDir.resolve(MANIFEST_PATH);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target,
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(manifest),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("写入工作区清单失败: " + workspaceDir, e);
        }
    }

    private void writeFile(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    private void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOGGER.warn("workspace 清理失败 path={}", path, e);
                }
            });
        } catch (IOException e) {
            LOGGER.warn("workspace 清理失败 root={}", dir, e);
        }
    }

    /**
     * 归一化用于黑名单比对：去尾部分隔符、转小写（Windows 盘符大小写不敏感）。
     */
    private String normalize(String root) {
        String s = root.trim().toLowerCase();
        while (s.length() > 1 && (s.endsWith("/") || s.endsWith("\\"))) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private record WorkspaceManifest(
            String projectId,
            WorkspaceSource source,
            String projectName,
            String templateGitlabUrl,
            String workspacePath,
            String createdAt
    ) {
    }
}
