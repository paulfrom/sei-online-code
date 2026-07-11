package com.changhong.onlinecode.service.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 工作区代码文件扫描器。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §11。
 *
 * <p>按固定预算扫描工作区源文件，计算指纹并提取一行摘要。超出预算时记录 truncated 与 reason，
 * 不抛异常阻断主流程。</p>
 *
 * @author sei-online-code
 */
@Component
public class WorkspaceFileScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceFileScanner.class);

    static final int MAX_FILES = 200;
    static final long MAX_FILE_BYTES = 128L * 1024L;
    static final long MAX_TOTAL_BYTES = 5L * 1024L * 1024L;
    static final int MAX_DEPTH = 8;

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", "node_modules", "dist", "build", "target", "out",
            "coverage", "logs", "tmp", ".cache", ".idea", ".vscode",
            "agent-memory", "platform-memory", "docs");

    /**
     * 扫描工作区源码文件。
     *
     * @param workspaceRoot 工作区根目录
     * @return 扫描结果（不可变列表）
     */
    public ScanResult scan(Path workspaceRoot) {
        if (workspaceRoot == null || !Files.exists(workspaceRoot)) {
            ScanResult empty = new ScanResult();
            empty.setFiles(Collections.emptyList());
            empty.setTruncated(false);
            empty.setTotalFiles(0);
            empty.setTotalBytes(0L);
            return empty;
        }

        List<ScannedSourceFile> files = new ArrayList<>();
        long[] totalBytes = {0L};
        int[] totalFiles = {0};
        boolean[] truncated = {false};
        String[] reason = {null};

        try {
            Files.walkFileTree(workspaceRoot, Collections.emptySet(), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (EXCLUDED_DIRS.contains(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (truncated[0]) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    long size = attrs.size();
                    if (size > MAX_FILE_BYTES) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (totalFiles[0] >= MAX_FILES) {
                        truncated[0] = true;
                        reason[0] = "maxFiles exceeded";
                        return FileVisitResult.TERMINATE;
                    }
                    if (totalBytes[0] + size > MAX_TOTAL_BYTES) {
                        truncated[0] = true;
                        reason[0] = "maxTotalBytes exceeded";
                        return FileVisitResult.TERMINATE;
                    }

                    byte[] content = readUpTo(file, MAX_FILE_BYTES);
                    if (content == null) {
                        return FileVisitResult.CONTINUE;
                    }
                    totalBytes[0] += content.length;
                    totalFiles[0]++;

                    ScannedSourceFile scanned = new ScannedSourceFile();
                    scanned.setPath(workspaceRoot.relativize(file).toString().replace('\\', '/'));
                    scanned.setSize(content.length);
                    scanned.setFingerprint(sha256(content));
                    scanned.setSummary(extractSummary(content));
                    files.add(scanned);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOGGER.warn("memory-scan: 扫描工作区失败 workspace={}", workspaceRoot, e);
            truncated[0] = true;
            reason[0] = "io_error: " + e.getMessage();
        }

        ScanResult result = new ScanResult();
        result.setFiles(List.copyOf(files));
        result.setTruncated(truncated[0]);
        result.setReason(reason[0]);
        result.setTotalFiles(totalFiles[0]);
        result.setTotalBytes(totalBytes[0]);
        return result;
    }

    /**
     * 扫描指定路径集合的源码文件（增量扫描）。
     *
     * <p>只处理 {@code paths} 中存在的常规文件，跳过被排除目录；预算限制与全量扫描一致。
     * 用于 CodingTask 后回写，避免为少量变更文件重新扫描整个工作区。</p>
     *
     * @param workspaceRoot 工作区根目录
     * @param paths         相对工作区根目录的文件路径集合
     * @return 扫描结果
     */
    public ScanResult scanPaths(Path workspaceRoot, List<String> paths) {
        if (workspaceRoot == null || !Files.exists(workspaceRoot) || paths == null || paths.isEmpty()) {
            ScanResult empty = new ScanResult();
            empty.setFiles(Collections.emptyList());
            empty.setTruncated(false);
            empty.setTotalFiles(0);
            empty.setTotalBytes(0L);
            return empty;
        }

        List<ScannedSourceFile> files = new ArrayList<>();
        long[] totalBytes = {0L};
        int[] totalFiles = {0};
        boolean[] truncated = {false};
        String[] reason = {null};

        for (String relative : paths) {
            if (truncated[0]) {
                break;
            }
            if (relative == null || relative.isBlank()) {
                continue;
            }
            String normalized = relative.replace('\\', '/');
            if (isExcludedPath(normalized)) {
                continue;
            }

            Path file = workspaceRoot.resolve(normalized);
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                continue;
            }

            long size;
            try {
                size = Files.size(file);
            } catch (IOException e) {
                LOGGER.warn("memory-scan: 获取文件大小失败 file={}", file, e);
                continue;
            }
            if (size > MAX_FILE_BYTES) {
                continue;
            }
            if (totalFiles[0] >= MAX_FILES) {
                truncated[0] = true;
                reason[0] = "maxFiles exceeded";
                break;
            }
            if (totalBytes[0] + size > MAX_TOTAL_BYTES) {
                truncated[0] = true;
                reason[0] = "maxTotalBytes exceeded";
                break;
            }

            byte[] content = readUpTo(file, MAX_FILE_BYTES);
            if (content == null) {
                continue;
            }
            totalBytes[0] += content.length;
            totalFiles[0]++;

            ScannedSourceFile scanned = new ScannedSourceFile();
            scanned.setPath(normalized);
            scanned.setSize(content.length);
            scanned.setFingerprint(sha256(content));
            scanned.setSummary(extractSummary(content));
            files.add(scanned);
        }

        ScanResult result = new ScanResult();
        result.setFiles(List.copyOf(files));
        result.setTruncated(truncated[0]);
        result.setReason(reason[0]);
        result.setTotalFiles(totalFiles[0]);
        result.setTotalBytes(totalBytes[0]);
        return result;
    }

    private boolean isExcludedPath(String path) {
        for (String dir : EXCLUDED_DIRS) {
            if (path.startsWith(dir + "/") || path.contains("/" + dir + "/")) {
                return true;
            }
        }
        return false;
    }

    private byte[] readUpTo(Path file, long maxBytes) {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes((int) maxBytes);
        } catch (IOException e) {
            LOGGER.warn("memory-scan: 读取文件失败 file={}", file, e);
            return null;
        }
    }

    private String extractSummary(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        for (String line : text.split("\r?\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
            }
        }
        return "";
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }
}
