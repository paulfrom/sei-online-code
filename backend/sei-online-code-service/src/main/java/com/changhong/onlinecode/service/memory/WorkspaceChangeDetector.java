package com.changhong.onlinecode.service.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件系统级工作区变更探测器。
 *
 * <p>用于 CodingTask 完成判定，不能依赖 Git：平台生成的项目可能还未初始化仓库。
 * 这里只比较执行前后的相对路径、文件大小和 mtime，判断 agent 是否在项目工作区落地了实际文件变更。</p>
 */
@Component
public class WorkspaceChangeDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceChangeDetector.class);

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", ".gradle", ".idea", ".vscode", "node_modules", "target", "build", "dist", ".next", ".umi");

    public Snapshot snapshot(String workspacePath) {
        Path root = workspacePath == null || workspacePath.isBlank()
                ? null : Path.of(workspacePath).toAbsolutePath().normalize();
        if (root == null || !Files.isDirectory(root)) {
            return Snapshot.unavailable("工作区路径不存在或不是目录: " + workspacePath);
        }
        Map<String, Fingerprint> files = new HashMap<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!root.equals(dir) && IGNORED_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        String relative = root.relativize(file).toString().replace('\\', '/');
                        files.put(relative, new Fingerprint(attrs.size(), attrs.lastModifiedTime().toMillis()));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return new Snapshot(root.toString(), files, null);
        } catch (IOException e) {
            LOGGER.warn("workspace-change: 快照失败 workspace={}", root, e);
            return Snapshot.unavailable("工作区快照失败: " + e.getMessage());
        }
    }

    public List<String> changedFiles(Snapshot before, String workspacePath) {
        Snapshot after = snapshot(workspacePath);
        if (before == null || !before.available() || !after.available()) {
            return Collections.emptyList();
        }
        Set<String> changed = new LinkedHashSet<>();
        before.files().forEach((path, fingerprint) -> {
            Fingerprint current = after.files().get(path);
            if (current == null || !current.equals(fingerprint)) {
                changed.add(path);
            }
        });
        after.files().forEach((path, fingerprint) -> {
            if (!before.files().containsKey(path)) {
                changed.add(path);
            }
        });
        return new ArrayList<>(changed);
    }

    public record Snapshot(String workspacePath, Map<String, Fingerprint> files, String failureReason) {
        static Snapshot unavailable(String reason) {
            return new Snapshot(null, Map.of(), reason);
        }

        public boolean available() {
            return failureReason == null;
        }
    }

    private record Fingerprint(long size, long modifiedAtMillis) {
    }
}
