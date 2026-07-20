package com.changhong.onlinecode.service;

import com.changhong.onlinecode.entity.PlatformConfig;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CommitAction;
import org.gitlab4j.api.models.CommitPayload;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 统一封装远端 Git 仓库读写。远端克隆和上传只走 GitLab HTTP API，
 * 本地仓库初始化、提交和差异计算仍使用本机 Git。
 */
@Component
public class GitApi {

    private final ConfigService configService;

    public GitApi(ConfigService configService) {
        this.configService = configService;
    }

    public void cloneRepository(String gitUrl, Path workspaceDir) {
        RepositoryTarget target = resolveTarget(gitUrl);
        GitLabApi client = client(target.host());
        try {
            org.gitlab4j.api.models.Project project = client.getProjectApi().getProject(target.projectPath());
            String defaultBranch = blankToDefault(project.getDefaultBranch(), "main");
            Files.createDirectories(workspaceDir);
            try (InputStream input = client.getRepositoryApi().getRepositoryArchive(
                    target.projectPath(), defaultBranch, Constants.ArchiveFormat.ZIP)) {
                extractArchive(input, workspaceDir);
            }
            runGit(workspaceDir, "init");
            runGit(workspaceDir, "config", "user.name", "sei-online-code");
            runGit(workspaceDir, "config", "user.email", "sei-online-code@local");
            runGit(workspaceDir, "checkout", "-B", defaultBranch);
            runGit(workspaceDir, "add", "-A");
            if (!runGitAllowFailure(workspaceDir, "diff", "--cached", "--quiet")) {
                runGit(workspaceDir, "commit", "-m", "chore: initialize workspace from Git API");
            }
            runGit(workspaceDir, "remote", "add", "origin", gitUrl.trim());
            runGit(workspaceDir, "update-ref", "refs/remotes/origin/" + defaultBranch, "HEAD");
            runGit(workspaceDir, "update-ref", "refs/sei/api-clone-base", "HEAD");
        } catch (Exception e) {
            throw new IllegalStateException("通过 Git API 克隆仓库失败: " + target.projectPath(), e);
        }
    }

    public UploadResult upload(Path workspaceDir, String projectId, String branch,
                               String targetBranch, String commitMessage) {
        GitLabApi client = client(null);
        try {
            Optional<Branch> remoteBranch = client.getRepositoryApi().getOptionalBranch(projectId, branch);
            String comparisonRef = localComparisonRef(workspaceDir, targetBranch);
            List<ChangedPath> changes = readChanges(workspaceDir, comparisonRef);
            String existenceRef = remoteBranch.isPresent() ? branch : targetBranch;
            List<CommitAction> actions = new ArrayList<>();
            List<String> changedFiles = new ArrayList<>();
            for (ChangedPath change : changes) {
                appendActions(client, projectId, existenceRef, workspaceDir, change, actions, changedFiles);
            }

            if (actions.isEmpty()) {
                Branch resolved = remoteBranch.orElseGet(() -> createBranch(client, projectId, branch, targetBranch));
                return new UploadResult(resolved.getCommit().getId(), changedFiles);
            }

            CommitPayload payload = new CommitPayload()
                    .withBranch(branch)
                    .withCommitMessage(blankToDefault(commitMessage, "chore: upload workspace changes"))
                    .withActions(actions)
                    .withAuthorName("sei-online-code")
                    .withAuthorEmail("sei-online-code@local");
            if (remoteBranch.isEmpty()) {
                payload.withStartBranch(targetBranch);
            }
            Commit commit = client.getCommitsApi().createCommit(projectId, payload);
            return new UploadResult(commit.getId(), changedFiles);
        } catch (Exception e) {
            throw new IllegalStateException("通过 Git API 上传仓库变更失败: project=" + projectId
                    + ", branch=" + branch, e);
        }
    }

    public String getBranchHead(String projectId, String branch) {
        try {
            return client(null).getRepositoryApi().getBranch(projectId, branch).getCommit().getId();
        } catch (Exception e) {
            throw new IllegalStateException("通过 Git API 获取分支失败: project=" + projectId
                    + ", branch=" + branch, e);
        }
    }

    public GitLabApi client(String host) {
        PlatformConfig config = configService.get();
        String resolvedHost = blankToDefault(host, configService.resolveGitlabApiBaseUrl(config));
        String token = configService.resolveGitlabToken(config);
        if (resolvedHost == null || resolvedHost.isBlank() || token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "GitLab API 配置不完整：apiBaseUrl/token 必填，" +
                    "请通过环境变量 oc.gitlab.api-base-url / oc.gitlab.token 或平台配置页面设置");
        }
        try {
            GitLabApi client = new GitLabApi(resolvedHost.trim(), token.trim());
            client.setRequestTimeout(60_000, 120_000);
            return client;
        } catch (Exception e) {
            throw new IllegalStateException("初始化 GitLab API 失败", e);
        }
    }

    private void appendActions(GitLabApi client, String projectId, String ref, Path workspaceDir,
                               ChangedPath change, List<CommitAction> actions, List<String> changedFiles)
            throws Exception {
        if (change.previousPath() != null && remoteFileExists(client, projectId, change.previousPath(), ref)) {
            actions.add(new CommitAction()
                    .withAction(CommitAction.Action.DELETE)
                    .withFilePath(change.previousPath()));
            changedFiles.add(change.previousPath());
        }
        if (change.deleted()) {
            if (remoteFileExists(client, projectId, change.path(), ref)) {
                actions.add(new CommitAction()
                        .withAction(CommitAction.Action.DELETE)
                        .withFilePath(change.path()));
            }
            changedFiles.add(change.path());
            return;
        }

        boolean exists = remoteFileExists(client, projectId, change.path(), ref);
        String content = Base64.getEncoder().encodeToString(Files.readAllBytes(workspaceDir.resolve(change.path())));
        actions.add(new CommitAction()
                .withAction(exists ? CommitAction.Action.UPDATE : CommitAction.Action.CREATE)
                .withFilePath(change.path())
                .withContent(content)
                .withEncoding(Constants.Encoding.BASE64));
        changedFiles.add(change.path());
    }

    private boolean remoteFileExists(GitLabApi client, String projectId, String path, String ref) {
        return client.getRepositoryFileApi().getOptionalFileInfo(projectId, path, ref).isPresent();
    }

    private Branch createBranch(GitLabApi client, String projectId, String branch, String targetBranch) {
        try {
            return client.getRepositoryApi().createBranch(projectId, branch, targetBranch);
        } catch (Exception e) {
            throw new IllegalStateException("通过 Git API 创建分支失败: " + branch, e);
        }
    }

    private List<ChangedPath> readChanges(Path workspaceDir, String comparisonRef) throws IOException, InterruptedException {
        String output = runGitOutput(workspaceDir, "diff", "--name-status", "-z", comparisonRef + "...HEAD");
        String[] fields = output.split("\u0000", -1);
        List<ChangedPath> changes = new ArrayList<>();
        for (int i = 0; i < fields.length && !fields[i].isEmpty(); ) {
            String status = fields[i++];
            if (status.startsWith("R") || status.startsWith("C")) {
                String previous = fields[i++];
                String current = fields[i++];
                changes.add(new ChangedPath(current, previous, false));
            } else {
                String path = fields[i++];
                changes.add(new ChangedPath(path, null, status.startsWith("D")));
            }
        }
        return changes;
    }

    private String localComparisonRef(Path workspaceDir, String targetBranch) {
        String remoteRef = "refs/remotes/origin/" + targetBranch;
        if (runGitAllowFailure(workspaceDir, "rev-parse", "--verify", remoteRef)) {
            return remoteRef;
        }
        if (runGitAllowFailure(workspaceDir, "rev-parse", "--verify", "refs/sei/api-clone-base")) {
            return "refs/sei/api-clone-base";
        }
        try {
            return runGitOutput(workspaceDir, "rev-list", "--max-parents=0", "HEAD").trim();
        } catch (Exception e) {
            throw new IllegalStateException("无法确定 Git API 上传的本地比较基线", e);
        }
    }

    private RepositoryTarget resolveTarget(String gitUrl) {
        String raw = gitUrl == null ? "" : gitUrl.trim();
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            java.net.URI uri = java.net.URI.create(raw);
            String path = trimProjectPath(uri.getPath());
            return new RepositoryTarget(uri.getScheme() + "://" + uri.getAuthority(), path);
        }
        if (raw.startsWith("git@") && raw.contains(":")) {
            int separator = raw.indexOf(':');
            String host = "https://" + raw.substring(4, separator);
            return new RepositoryTarget(host, trimProjectPath(raw.substring(separator + 1)));
        }
        PlatformConfig config = configService.get();
        return new RepositoryTarget(configService.resolveGitlabApiBaseUrl(config), trimProjectPath(raw));
    }

    private String trimProjectPath(String path) {
        String normalized = path == null ? "" : path.replaceAll("^/+", "").replaceAll("\\.git$", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Git 仓库地址缺少项目路径");
        }
        return normalized;
    }

    private void extractArchive(InputStream input, Path workspaceDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String relative = stripArchiveRoot(entry.getName());
                if (relative == null || relative.isBlank()) {
                    continue;
                }
                Path target = workspaceDir.resolve(relative).normalize();
                if (!target.startsWith(workspaceDir.normalize())) {
                    throw new IOException("仓库归档包含越界路径: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.write(target, readAllBytes(zip));
                }
            }
        }
    }

    private String stripArchiveRoot(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("..")) {
            return null;
        }
        int separator = path.indexOf('/');
        return separator < 0 ? null : path.substring(separator + 1);
    }

    private byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toByteArray();
    }

    private void runGit(Path cwd, String... args) throws IOException, InterruptedException {
        ProcessResult result = executeGit(cwd, args);
        if (result.exitCode() != 0) {
            throw new IllegalStateException("本地 Git 命令失败: git " + String.join(" ", args)
                    + ": " + result.output());
        }
    }

    private String runGitOutput(Path cwd, String... args) throws IOException, InterruptedException {
        ProcessResult result = executeGit(cwd, args);
        if (result.exitCode() != 0) {
            throw new IllegalStateException("本地 Git 命令失败: git " + String.join(" ", args)
                    + ": " + result.output());
        }
        return result.output();
    }

    private boolean runGitAllowFailure(Path cwd, String... args) {
        try {
            return executeGit(cwd, args).exitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private ProcessResult executeGit(Path cwd, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record UploadResult(String commitHash, List<String> changedFiles) {
    }

    private record RepositoryTarget(String host, String projectPath) {
    }

    private record ChangedPath(String path, String previousPath, boolean deleted) {
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
