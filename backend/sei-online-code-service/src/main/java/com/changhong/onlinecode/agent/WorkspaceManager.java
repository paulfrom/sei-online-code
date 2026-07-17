package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dao.RequirementDao;
import com.changhong.onlinecode.dto.WorkspaceResolveResult;
import com.changhong.onlinecode.dto.enums.WorkspaceSource;
import com.changhong.onlinecode.entity.PlatformConfig;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Requirement;
import com.changhong.onlinecode.service.ConfigService;
import com.changhong.sei.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * WorkspaceManager（B35）。契约 Phase 5 §3，蓝图参照 multica {@code daemon/local_directory.go} + {@code repocache/cache.go}。
 *
 * <p>在可配置的 Workspace Root 下解析每个项目的工作区目录，语义为 <b>clone-once + reuse</b>：
 * 目录已存在即复用（后续 Build Loop 回合原地增量编辑，绝不重复 clone/生成）；不存在则按平台配置
 * 是否设置项目 gitUrl / templateGitlabUrl 决定 provision 来源——项目 gitUrl 优先拉取真实项目仓库，
 * 否则有模板地址走模板仓拉取，空则走 SCAFFOLD 生成 canonical SUID 脚手架。</p>
 *
 * <p>本实现不再停留在 compile-only：resolve 会真实创建项目物理工作区。平台自有运行物料统一落在
 * 工作区的 {@code .sei/} 目录，代码执行阶段写入 AGENTS.md / CLAUDE.md、技能目录和代码文件时，
 * 都有明确且稳定的落点。</p>
 *
 * @author sei-online-code
 */
@Component
@Slf4j
public class WorkspaceManager {

    private static final String MANIFEST_PATH = ".sei/workspace.json";
    private static final Pattern REPLACE_PATTERN = Pattern.compile("([a-zA-Z]+)_(\\w+)_([a-zA-Z]+)");
    private static final String DEFAULT_PROJECT_VERSION = "1.0.0-SNAPSHOT";
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "jpeg", "jpg", "png", "gif", "svg", "ico", "bmp", "webp", "jar", "zip", "gz", "eot",
            "ttf", "woff", "woff2", "pdf", "mp4", "mov", "avi", "class", "so", "dll", "dylib"
    );
    private static final Set<String> COPY_EXCLUDED_DIRS = Set.of(
            "node_modules", ".next", ".turbo", ".vite", "dist", "build", "target", ".gradle");

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
    /**
     * Git 会用 index.lock/config.lock 保护仓库写操作；同一 JVM 内必须先按物理路径串行化工作区初始化，
     * 否则多个 worker 会在业务执行槽登记前同时进入 git init/checkout。
     */
    private final ConcurrentMap<Path, ReentrantLock> workspaceLocks = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private RequirementDao requirementDao;

    @Value("${oc.gitlab.host:}")
    private String gitlabHost;

    @Value("${oc.gitlab.token:}")
    private String gitlabToken;

    public WorkspaceManager(ProjectDao projectDao, ConfigService configService, ScaffoldGenerator scaffoldGenerator) {
        this.projectDao = projectDao;
        this.configService = configService;
        this.scaffoldGenerator = scaffoldGenerator;
    }

    void setRequirementDao(RequirementDao requirementDao) {
        this.requirementDao = requirementDao;
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
        ReentrantLock lock = workspaceLock(workspaceDir);
        lock.lock();
        try {
            WorkspaceManifest manifest = readManifest(workspaceDir);

            if (Files.exists(workspaceDir)) {
                WorkspaceSource source = manifest == null ? decideSource(config, project) : manifest.source();
                ensureManagedLayout(workspaceDir);
                ensureGitRepository(workspaceDir);
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

            WorkspaceSource source = decideSource(config, project);
            if (hasProjectGitUrl(project)) {
                materializeProjectWorkspace(project, workspaceDir);
            } else if (source == WorkspaceSource.CLONE) {
                materializeTemplateWorkspace(config, project, workspaceDir);
            } else {
                scaffoldWorkspace(workspaceDir);
            }
            ensureManagedLayout(workspaceDir);
            ensureGitRepository(workspaceDir);
            writeManifest(workspaceDir, new WorkspaceManifest(
                    projectId,
                    source,
                    project == null ? null : project.getName(),
                    config == null ? null : config.getTemplateGitlabUrl(),
                    workspaceDir.toString(),
                    OffsetDateTime.now().toString()
            ));
            return new WorkspaceResolveResult(dir, false, source);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 解析并准备项目下的隔离工作区。需求级工作区使用 key
     * {@code requirement-<requirementId>}，由调用方决定 key 的业务粒度。
     */
    public Path resolveIsolatedWorkspace(String projectId, String workspaceKey) {
        WorkspaceResolveResult resolved = resolve(projectId);
        if (resolved == null || resolved.getPath() == null || resolved.getPath().isBlank()) {
            throw new IllegalStateException("项目工作区解析失败: " + projectId);
        }
        Path projectWorkspace = Path.of(resolved.getPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(projectWorkspace)) {
            throw new IllegalStateException("项目工作区不存在或不是目录: " + projectWorkspace);
        }
        if (workspaceKey == null || workspaceKey.isBlank()) {
            return projectWorkspace;
        }
        Path root = isolatedWorkspacesRoot(projectWorkspace);
        Path target = root.resolve(safeSegment(workspaceKey)).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("非法隔离工作区路径: " + target);
        }
        ReentrantLock lock = workspaceLock(target);
        lock.lock();
        try {
            if (Files.isDirectory(target)) {
                ensureRequirementBranch(target, workspaceKey);
                return target;
            }
            try {
                Files.createDirectories(root);
                copyWorkspace(projectWorkspace, target);
                ensureRequirementBranch(target, workspaceKey);
                return target;
            } catch (IOException e) {
                throw new IllegalStateException("创建隔离工作区失败: " + target, e);
            }
        } finally {
            lock.unlock();
        }
    }

    public Path resolveRequirementWorkspace(String projectId, String requirementId) {
        return resolveIsolatedWorkspace(projectId, requirementWorkspaceKey(requirementId));
    }

    public void deleteRequirementWorkspace(String projectId, String requirementId) {
        WorkspaceResolveResult resolved = resolve(projectId);
        if (resolved == null || resolved.getPath() == null || resolved.getPath().isBlank()) {
            return;
        }
        Path projectWorkspace = Path.of(resolved.getPath()).toAbsolutePath().normalize();
        Path root = isolatedWorkspacesRoot(projectWorkspace);
        Path target = root.resolve(safeSegment(requirementWorkspaceKey(requirementId))).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("非法需求工作区路径: " + target);
        }
        ReentrantLock lock = workspaceLock(target);
        lock.lock();
        try {
            deleteTree(target);
        } finally {
            lock.unlock();
        }
    }

    public boolean isManagedWorkspacePath(Path projectWorkspace, Path candidate) {
        if (projectWorkspace == null || candidate == null) {
            return false;
        }
        Path root = projectWorkspace.toAbsolutePath().normalize();
        Path actual = candidate.toAbsolutePath().normalize();
        return actual.equals(root) || actual.startsWith(isolatedWorkspacesRoot(root));
    }

    public String requirementWorkspaceKey(String requirementId) {
        Requirement requirement = requirementDao == null || requirementId == null || requirementId.isBlank()
                ? null : requirementDao.findOne(requirementId);
        String key = requirement != null && requirement.getRequirementNo() != null
                && !requirement.getRequirementNo().isBlank()
                ? requirement.getRequirementNo()
                : requirementId;
        return "requirement-" + safeSegment(key);
    }

    /**
     * 决定 provision 来源：配置了 templateGitlabUrl → CLONE；否则 SCAFFOLD（契约 §3）。
     *
     * @param config 平台配置（可空，视为无模板地址）
     * @return CLONE 或 SCAFFOLD
     */
    public WorkspaceSource decideSource(PlatformConfig config) {
        return decideSource(config, null);
    }

    private WorkspaceSource decideSource(PlatformConfig config, Project project) {
        if (hasProjectGitUrl(project)) {
            return WorkspaceSource.CLONE;
        }
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
            log.info("workspace: 已生成脚手架 dir={}, files={}", workspaceDir, scaffoldGenerator.generate().size());
        } catch (IOException e) {
            throw new IllegalStateException("初始化脚手架工作区失败: " + workspaceDir, e);
        }
    }

    private void materializeProjectWorkspace(Project project, Path workspaceDir) {
        String gitUrl = project == null ? null : project.getGitUrl();
        if (gitUrl == null || gitUrl.isBlank()) {
            throw new IllegalStateException("项目 Git 地址为空，无法获取项目内容");
        }
        try {
            Path parent = workspaceDir.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            cloneProjectRepository(gitUrl, workspaceDir);
            log.info("workspace: 已从项目仓库生成 dir={}, gitUrl={}", workspaceDir, gitUrl);
        } catch (RuntimeException | IOException e) {
            deleteTree(workspaceDir);
            throw new IllegalStateException("拉取项目仓库内容失败: " + gitUrl, e);
        }
    }

    private void cloneProjectRepository(String gitUrl, Path workspaceDir) throws IOException {
        Path parent = workspaceDir.getParent();
        if (parent == null) {
            throw new IOException("工作区缺少父目录: " + workspaceDir);
        }
        Files.createDirectories(parent);
        runCommand(parent, "git", "clone", gitUrl.trim(), workspaceDir.getFileName().toString());
        ensureLocalGitConfig(workspaceDir);
    }

    private void materializeTemplateWorkspace(PlatformConfig config, Project project, Path workspaceDir) {
        String templateUrl = config == null ? null : config.getTemplateGitlabUrl();
        if (templateUrl == null || templateUrl.isBlank()) {
            throw new IllegalStateException("模板仓库地址为空，无法获取模板归档");
        }
        try {
            Path parent = workspaceDir.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (templateUrl.startsWith("file:")) {
                generateFromTemplate(Path.of(java.net.URI.create(templateUrl)), workspaceDir, project);
            } else {
                generateFromTemplateArchive(resolveTemplateRepo(templateUrl), workspaceDir, project);
            }
            log.info("workspace: 已从模板生成 dir={}, template={}", workspaceDir, templateUrl);
        } catch (IOException e) {
            deleteTree(workspaceDir);
            throw new IllegalStateException("拉取模板仓归档失败: " + templateUrl, e);
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

    private void generateFromTemplateArchive(TemplateRepo templateRepo, Path workspaceDir, Project project) throws IOException {
        Files.createDirectories(workspaceDir);
        Map<String, String> replaceData = buildReplaceData(project);
        Map<String, String> backendReplaceData = new HashMap<>(replaceData);
        putPackageData(backendReplaceData, derivePackageName(project));
        String resolvedHost = nullToEmpty(templateRepo.host(), nullToEmpty(gitlabHost));

        try (InputStream is = getGitLabApi(resolvedHost).getRepositoryApi()
                .getRepositoryArchive(templateRepo.projectPath(), null, Constants.ArchiveFormat.ZIP);
             BufferedInputStream bis = new BufferedInputStream(is);
             ZipInputStream zis = new ZipInputStream(bis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String relative = normalizeArchiveEntry(entry.getName());
                if (relative == null || "template_config.json".equals(relative)) {
                    continue;
                }

                Map<String, String> effectiveData = relative.startsWith("backend/") ? backendReplaceData : replaceData;
                String resolvedRelative = replacePlaceholders(relative, effectiveData);
                Path targetFile = workspaceDir.resolve(resolvedRelative);
                Path targetParent = targetFile.getParent();
                if (targetParent != null) {
                    Files.createDirectories(targetParent);
                }

                byte[] contentBytes = readAllBytes(zis);
                if (isBinaryFile(targetFile.getFileName().toString())) {
                    Files.write(targetFile, contentBytes);
                    continue;
                }

                String content = replacePlaceholders(new String(contentBytes, StandardCharsets.UTF_8), effectiveData);
                Files.writeString(targetFile, content, StandardCharsets.UTF_8);
            }
        } catch (RuntimeException e) {
            deleteTree(workspaceDir);
            throw e;
        } catch (Exception e) {
            deleteTree(workspaceDir);
            throw new IllegalStateException("处理模板仓归档失败: " + templateRepo.projectPath(), e);
        }
    }

    /**
     * 创建 GitLab 连接并调大读取超时，避免拉取模板归档时在较大仓库上过早超时。
     */
    public GitLabApi getGitLabApi(String host) {
        String resolvedHost = nullToEmpty(host);
        if (resolvedHost.isBlank()) {
            throw new IllegalStateException("未配置 GitLab Host，无法初始化 GitLabApi");
        }
        if (nullToEmpty(gitlabToken).isBlank()) {
            throw new IllegalStateException("未配置 oc.gitlab.token，无法拉取模板仓归档");
        }
        try {
            GitLabApi gitLabApi = new GitLabApi(resolvedHost, gitlabToken.trim());
            gitLabApi.setRequestTimeout(60 * 1000, 120 * 1000);
            return gitLabApi;
        } catch (Exception e) {
            throw new RuntimeException("获取Gitlab接口异常", e);
        }
    }

    static TemplateRepo resolveTemplateRepo(String templateGitlabUrl) {
        String raw = templateGitlabUrl == null ? "" : templateGitlabUrl.trim();
        if (raw.isBlank()) {
            throw new IllegalStateException("模板仓库地址为空，无法解析 GitLab 项目");
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            java.net.URI uri = java.net.URI.create(raw);
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("^/+", "");
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            if (path.isBlank()) {
                throw new IllegalStateException("模板仓库地址缺少项目路径: " + raw);
            }
            return new TemplateRepo(uri.getScheme() + "://" + uri.getAuthority(), path);
        }
        return new TemplateRepo("", raw.replaceAll("^/+", "").replaceAll("\\.git$", ""));
    }

    private String normalizeArchiveEntry(String originalPath) {
        if (originalPath == null || originalPath.isBlank() || originalPath.contains("..") || originalPath.startsWith("/")) {
            return null;
        }
        String[] parts = originalPath.split("/");
        if (parts.length <= 1) {
            return null;
        }
        return String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length));
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, len);
        }
        return output.toByteArray();
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

    private boolean hasProjectGitUrl(Project project) {
        return project != null && project.getGitUrl() != null && !project.getGitUrl().isBlank();
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
            return JsonUtils.mapper().readValue(Files.readString(manifestPath, StandardCharsets.UTF_8), WorkspaceManifest.class);
        } catch (IOException e) {
            log.warn("workspace manifest 读取失败，path={}", manifestPath, e);
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
                    JsonUtils.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(manifest),
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

    private Path isolatedWorkspacesRoot(Path projectWorkspace) {
        return projectWorkspace.toAbsolutePath().normalize().resolve(".sei").resolve("workspaces");
    }

    private String safeSegment(String value) {
        String safe = value == null ? "" : value.trim().replaceAll("[^a-zA-Z0-9._-]", "-");
        if (safe.isBlank()) {
            throw new IllegalArgumentException("工作区 key 为空，无法创建隔离工作区");
        }
        return safe;
    }

    private void copyWorkspace(Path sourceRoot, Path targetRoot) throws IOException {
        Path normalizedSource = sourceRoot.toAbsolutePath().normalize();
        Path workspacesRoot = isolatedWorkspacesRoot(normalizedSource);
        try (var stream = Files.walk(normalizedSource)) {
            for (Path source : stream.filter(path -> shouldCopy(path, normalizedSource, workspacesRoot)).toList()) {
                Path relative = normalizedSource.relativize(source);
                Path target = targetRoot.resolve(relative).normalize();
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private boolean shouldCopy(Path path, Path sourceRoot, Path workspacesRoot) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.equals(sourceRoot)) {
            return true;
        }
        if (normalized.startsWith(workspacesRoot)) {
            return false;
        }
        Path relative = sourceRoot.relativize(normalized);
        for (Path part : relative) {
            if (COPY_EXCLUDED_DIRS.contains(part.toString())) {
                return false;
            }
        }
        return true;
    }

    private void ensureRequirementBranch(Path workspaceDir, String workspaceKey) {
        ensureGitRepository(workspaceDir);
        String targetBranch = branchName(workspaceKey);
        String currentBranch = runCommandOutput(workspaceDir, "git", "branch", "--show-current").trim();
        if (!targetBranch.equals(currentBranch)) {
            runCommand(workspaceDir, "git", "checkout", "-B", targetBranch);
        }
    }

    private String branchName(String workspaceKey) {
        String key = workspaceKey == null ? "" : workspaceKey.trim();
        if (key.startsWith("requirement-")) {
            key = key.substring("requirement-".length());
        }
        return "feature/" + safeSegment(key);
    }

    private void ensureGitRepository(Path workspaceDir) {
        if (!Files.isDirectory(workspaceDir.resolve(".git"))) {
            runCommand(workspaceDir, "git", "init");
        }
        ensureLocalGitConfig(workspaceDir);
    }

    private void ensureLocalGitConfig(Path workspaceDir) {
        runCommand(workspaceDir, "git", "config", "user.name", "sei-online-code");
        runCommand(workspaceDir, "git", "config", "user.email", "sei-online-code@local");
    }

    private void runCommand(Path cwd, String... command) {
        runCommandOutput(cwd, command);
    }

    private String runCommandOutput(Path cwd, String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(cwd.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException(String.join(" ", command) + " failed: " + output);
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(String.join(" ", command) + " interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException(String.join(" ", command) + " failed", e);
        }
    }

    private ReentrantLock workspaceLock(Path workspaceDir) {
        return workspaceLocks.computeIfAbsent(lockKey(workspaceDir), ignored -> new ReentrantLock());
    }

    private Path lockKey(Path workspaceDir) {
        return workspaceDir.toAbsolutePath().normalize();
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
                    log.warn("workspace 清理失败 path={}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("workspace 清理失败 root={}", dir, e);
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

    record TemplateRepo(String host, String projectPath) {
    }
}
